#!/usr/bin/env python3
"""Audit all seed inventories, shared definitions and environment boundaries."""
import argparse
import copy
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SEEDS = ROOT / 'server/docker/conf/mongodb'
from seed_audit_project import SHARED, CONFIGURED, RUNTIME, DEMO_ONLY_RUNTIME, audit_project

def identity(value):
    return value.get('$oid') if isinstance(value, dict) else value

def import_collections(rule, database):
    return {name for item in rule.split(';') if item.partition(':')[0] == database
            for name in item.partition(':')[2].split(',') if name}


def audit_delivery(data, errors, packaged_root=None):
    """Compare delivery with the classified DEV/production baseline, not itself."""
    production = data['myscoutee_db']
    required = set(SHARED + CONFIGURED) | {name for name, rows in production.items() if rows}
    package = json.loads((ROOT / 'packaging/scripts/release-project.json').read_text())['packageName']
    policy = json.loads((ROOT / 'packaging/templates/install-policy.json').read_text())
    env_path = Path('packaging/templates') / (package + '.env')
    source_env = (ROOT / 'server/.env').read_text()
    packaged_env = (ROOT / env_path).read_text()
    importer = (SEEDS / 'mongo-seed-policy.inc').read_text()
    env_rule = lambda text: re.search(r'^MONGO_IMPORT_ALLOWED_COLLECTIONS_BY_DB=(.*)$', text, re.M).group(1)
    fallback = re.search(r'allowed_collections_by_db="\$\{MONGO_IMPORT_ALLOWED_COLLECTIONS_BY_DB:-(.*)\}"', importer).group(1)
    rules = [('source environment', env_rule(source_env)), ('importer default', fallback),
             ('installed environment', env_rule(packaged_env)), ('install fallback', policy['mongoSeedFallback'])]
    for label, rule in rules:
        for database in ('production_db', 'myscoutee_db') if label in ('source environment', 'importer default') else ('production_db',):
            actual = import_collections(rule, database)
            if '*' in actual or not required.issubset(actual):
                errors.append(f'{label}/{database}: missing production inputs {sorted(required - actual)}; wildcard={"*" in actual}')
    missing_upgrade = required - set(policy['requiredMongoCollections'])
    if missing_upgrade:
        errors.append(f'install upgrade policy: missing production inputs {sorted(missing_upgrade)}')
    if packaged_root is None:
        return
    installed = Path(packaged_root) / 'server/docker/conf/mongodb'
    if (installed / 'e2e_db').exists():
        errors.append('installed Mongo inputs contain E2E fixtures')
    for path in SEEDS.iterdir():
        if path.is_file() and (not (installed / path.name).is_file()
                               or path.read_bytes() != (installed / path.name).read_bytes()):
            errors.append(f'installed Mongo initializer differs or is missing: {path.name}')
    for mode in ('demo_db', 'myscoutee_db'):
        expected = set(data[mode]) if mode == 'demo_db' else required
        actual = {p.stem for p in (installed / mode).glob('*.json')}
        if actual != expected:
            errors.append(f'installed {mode}: missing={sorted(expected - actual)}, unexpected={sorted(actual - expected)}')
        for name in expected & actual:
            reference = copy.deepcopy(data[mode][name])
            if name == 'appSettings':
                admins = sorted({row['email'] for row in data['demo_db']['users']
                                 if row.get('hostTier') == 'Admin' and re.search(r'@[^@]+\.(local|invalid)$', row.get('email', ''), re.I)})
                for row in reference:
                    if row.get('settingsKey') == 'global':
                        row['adminEmails'] = admins if mode == 'demo_db' else []
            if json.loads((installed / mode / (name + '.json')).read_text()) != reference:
                errors.append(f'installed {mode}/{name}: differs from canonical environment input')


def audit(seeds=SEEDS, packaged_root=None):
    errors = []
    data = {}
    known = set(SHARED + CONFIGURED + RUNTIME + DEMO_ONLY_RUNTIME) | {'adminUsers'}
    for mode in ('demo_db', 'myscoutee_db', 'e2e_db'):
        data[mode] = {}
        for path in (seeds / mode).glob('*.json'):
            rows = json.loads(path.read_text())
            if path.stem not in known:
                errors.append(f'{mode}/{path.stem}: unclassified seed; review its ownership')
            if not isinstance(rows, list) or any(not isinstance(r, dict) or '_id' not in r for r in rows):
                errors.append(f'{mode}/{path.stem}: expected document array with identities')
                continue
            ids = [identity(r['_id']) for r in rows]
            if len(set(ids)) != len(ids):
                errors.append(f'{mode}/{path.stem}: duplicate identity')
            data[mode][path.stem] = rows
        required = SHARED + CONFIGURED + RUNTIME + (() if mode == 'myscoutee_db' else DEMO_ONLY_RUNTIME)
        for name in required:
            if name not in data[mode]:
                errors.append(f'{mode}/{name}: missing JSON baseline')
    if errors:
        return errors
    audit_project(data, errors)
    audit_delivery(data, errors, packaged_root)
    return errors

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--packaged-root', type=Path, help='Extracted /opt/<package> directory to compare with DEV/source inputs')
    args = parser.parse_args()
    errors = audit(packaged_root=args.packaged_root)
    if errors:
        raise SystemExit('\n'.join(errors))
    print(f'PASS: all {len(SHARED + CONFIGURED + RUNTIME + DEMO_ONLY_RUNTIME)} dev/prod/e2e seed baselines classified and project parity verified.')
