#!/usr/bin/env python3
"""Check selected install data and generated secrets without touching a host."""
import copy
import json
import re
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
source = (ROOT / 'packaging/debian/postinst').read_text()
validator = source.split("jq -e --arg package \"$PACKAGE_NAME\" '\n", 1)[1].split("\n' \"$INSTALL_POLICY\"", 1)[0]
policy = json.loads((ROOT / 'packaging/templates/install-policy.json').read_text())

def validate(value):
    return subprocess.run(['jq', '-e', '--arg', 'package', 'myscoutee', validator], input=json.dumps(value),
                          text=True, capture_output=True).returncode == 0

assert validate(policy)
minimal = copy.deepcopy(policy)
minimal.update(ownedDirectories=[], migrateOwnership=[], ensureSecrets=[],
               freshSecrets=policy['freshSecrets'][:3], environmentDefaults={},
               requiredMongoCollections=['helpCenterRevisions'],
               mongoSeedFallback='demo_db:*;production_db:helpCenterRevisions')
assert validate(minimal)
for path, value in [('unknown', True), ('ownedDirectories', [{'path': '/tmp/foreign', 'mode': '0700', 'uid': 10001, 'gid': 10001}]),
                    ('environmentDefaults', {'BAD;COMMAND': 'x'}),
                    ('environmentDefaults', {'BAD_VALUE': 'x\nOTHER=changed'}),
                    ('freshSecrets', [{'key': 'KEY', 'encoding': 'shell', 'prefix': ''}])]:
    invalid = copy.deepcopy(policy)
    invalid[path] = value
    assert not validate(invalid), path

functions = '\n'.join(re.search(r'^' + name + r'\(\) \{\n.*?^\}', source,
                               re.M | re.S).group() for name in [
    'generate_secret', 'generate_guid', 'generate_policy_secret',
    'install_generated_secrets_file', 'read_env_file_value', 'upsert_env_value', 'ensure_generated_secret'])
with tempfile.TemporaryDirectory() as directory:
    root = Path(directory)
    (root / 'etc').mkdir()
    (root / 'opt/packaging/templates').mkdir(parents=True)
    (root / 'functions.sh').write_text(functions.replace('${PACKAGE_ETC}', str(root / 'etc'))
                                     .replace('${PACKAGE_ROOT}', str(root / 'opt')).replace('${PACKAGE_NAME}', 'myscoutee'))
    for selection in [policy, minimal]:
        (root / 'policy.json').write_text(json.dumps(selection))
        keys = [item['key'] for item in selection['freshSecrets']]
        template = root / 'opt/packaging/templates/myscoutee.secrets.env'
        template.write_text('CUSTOM=retained\n' + ''.join(key + '=\n' for key in keys))
        subprocess.run(['bash', '-euc', 'source "$1/functions.sh"; INSTALL_POLICY="$1/policy.json"; install_generated_secrets_file',
                        'test', directory], check=True)
        target = root / 'etc/myscoutee.secrets.env'
        values = dict(line.split('=', 1) for line in target.read_text().splitlines() if line)
        assert values.pop('CUSTOM') == 'retained'
        assert set(values) == set(keys)
        assert target.stat().st_mode & 0o777 == 0o600
        for secret in selection['freshSecrets']:
            pattern = '[a-f0-9]{64}' if secret['encoding'] == 'hex' else '[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-8[a-f0-9]{3}-[a-f0-9]{12}'
            assert re.fullmatch(re.escape(secret['prefix']) + pattern, values[secret['key']])
        before = target.read_bytes()
        subprocess.run(['bash', '-euc', 'source "$1/functions.sh"; ensure_generated_secret "$1/etc/myscoutee.secrets.env" MONGO_APP_PASSWORD replacement',
                        'test', directory], check=True)
        assert target.read_bytes() == before, 'upgrade replaced an existing credential'
print('PASS: native/minimal install selections, safe policy fields, generated secrets and credential preservation.')
