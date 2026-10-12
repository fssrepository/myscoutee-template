#!/usr/bin/env python3
"""Exercise selected host scripts with disposable files and command doubles.

No service, real database, system account or installed package is modified.
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile

ROOT = Path(__file__).resolve().parents[2]


def run(args, env, *, success=True):
    result = subprocess.run(args, env=env, text=True, capture_output=True)
    if success and result.returncode:
        raise AssertionError((args, result.returncode, result.stderr))
    if not success:
        assert result.returncode, args
    return result


def executable(path, source):
    path.write_text(source)
    path.chmod(0o755)


with tempfile.TemporaryDirectory(prefix='template-host-lifecycle-') as directory:
    temp = Path(directory)
    bin_dir = temp / 'bin'
    bin_dir.mkdir()
    trace = temp / 'trace'
    env = dict(os.environ, PATH=str(bin_dir)+':'+os.environ['PATH'], HOST_TRACE=str(trace),
               DPKG_MAINTSCRIPT_PACKAGE='myscoutee-template')
    executable(bin_dir/'systemctl', '#!/bin/sh\nprintf "systemctl %s\\n" "$*" >> "$HOST_TRACE"\n')
    executable(bin_dir/'rm', '#!/bin/sh\nprintf "rm %s\\n" "$*" >> "$HOST_TRACE"\n')
    executable(bin_dir/'docker', '''#!/bin/sh
printf 'docker %s\n' "$*" >> "$HOST_TRACE"
if [ "$1" = images ]; then
  printf '%s\n' myscoutee-template-api:1.0.0-prod myscoutee-api:1.0.0-prod mongo:8.0.4 unrelated:1
fi
''')
    postrm = temp / 'postrm'
    run(['python3',str(ROOT/'packaging/release/render-postrm.py'),str(ROOT/'packaging'),str(postrm)],env)
    run(['bash',str(postrm),'remove'],env)
    assert 'rm ' not in trace.read_text() and 'docker ' not in trace.read_text()
    trace.write_text('')
    run(['bash',str(postrm),'purge'],env)
    calls = trace.read_text().splitlines()
    assert [line for line in calls if line.startswith('docker image rm')] == ['docker image rm -f myscoutee-template-api:1.0.0-prod']
    assert not any(line.startswith('docker volume') for line in calls)
    assert [line for line in calls if line.startswith('rm ')] == [
        'rm -rf /etc/myscoutee-template','rm -rf /opt/myscoutee-template',
        'rm -rf /var/lib/myscoutee-template','rm -rf /var/log/myscoutee-template',
        'rm -rf /var/cache/myscoutee-template-update']
    (bin_dir/'rm').unlink()  # Remaining cleanup is confined to the temporary tree.

    # Actual selected Compose model, without resolving deployment secret files.
    model = run(['docker','compose','-f',str(ROOT/'packaging/compose/docker-compose.production.yml'),
                 '--env-file',str(ROOT/'packaging/templates/myscoutee-template.env'),
                 'config','--no-env-resolution','--format','json'],dict(os.environ)).stdout
    model_path = temp/'compose.json'
    model_path.write_text(model)
    assert set(json.loads(model)['services']) == {'stun','mcp','nginx','mongodb','redis','minio','profilemanagement'}
    scripts = temp/'scripts'
    scripts.mkdir()
    for name in ['release-project.sh','release-project.json','restart-update-stack.sh']:
        shutil.copy2(ROOT/'packaging/scripts'/name, scripts/name)
    for name in ['reset-demo-state.sh','sync-i18n-seeds.sh','migrate-production-data.sh']:
        executable(scripts/name,'#!/bin/sh\nprintf "%s %s\\n" "$(basename "$0")" "$*" >> "$HOST_TRACE"\n')
    executable(temp/'compose','''#!/bin/sh
if [ "$1" = config ]; then cat "$HOST_MODEL"; exit; fi
printf 'compose %s\n' "$*" >> "$HOST_TRACE"
''')
    executable(bin_dir/'docker','''#!/bin/sh
printf '%s\n' "${HOST_HEALTH:-healthy}"
''')
    env.update(MYSCOUTEE_COMPOSE_SCRIPT=str(temp/'compose'),HOST_MODEL=str(model_path))
    trace.write_text('')
    run(['bash',str(scripts/'restart-update-stack.sh')],env)
    calls=trace.read_text().splitlines()
    assert calls[0:2] == ['systemctl is-active --quiet myscoutee-template.service',
                         'systemctl is-active --quiet myscoutee-template-update-status.socket']
    assert 'compose up -d --no-deps --wait --wait-timeout 300 mcp minio mongodb redis stun' in calls
    assert calls[-1] == 'compose up -d --no-deps nginx'
    trace.write_text('')
    run(['bash',str(scripts/'restart-update-stack.sh')],dict(env,HOST_HEALTH='unhealthy'),success=False)
    assert not any('nginx' in line for line in trace.read_text().splitlines())

    # Complete common backup and guarded Mongo/master-key restore. Docker is a
    # command double here; database semantics are covered by runtime qualification.
    server=temp/'server'
    (server/'data/minio').mkdir(parents=True)
    (server/'data/minio/kept').write_text('media')
    (server/'.env').write_text('FIREBASE_ENABLED=false\nMONGO_REPLICA_SET_ENABLED=false\n')
    (server/'.env.secrets').write_text('MONGO_APP_PASSWORD=test-only\n')
    (server/'docker-compose-prod.yml').write_text('services: {}\n')
    key=temp/'configuration';key.mkdir()
    (key/'deployment-master-key').write_text('5'*64+'\n')
    executable(bin_dir/'docker','''#!/bin/sh
case "$1" in
  ps) printf '%s\n' mongodb myscoutee-template;;
  exec)
    if [ "$2" = -i ]; then
      cat >/dev/null
      printf 'restore\n' >> "$HOST_TRACE"
      exit "${HOST_RESTORE_EXIT:-0}"
    fi
    printf 'disposable Mongo dump\n';;
  compose) printf 'compose %s\n' "$*" >> "$HOST_TRACE";;
esac
''')
    env.update(MYSCOUTEE_SERVER_DIR=str(server),MYSCOUTEE_CONFIGURATION_KEY_DIR=str(key),
               MYSCOUTEE_BACKUP_DIR=str(temp/'backups'),MYSCOUTEE_BACKUP_REQUIRE_COMPLETE='true')
    result=run(['bash',str(ROOT/'packaging/scripts/backup-production.sh')],env)
    archive=Path(result.stdout.strip())
    assert archive.name.startswith('myscoutee-template-backup-')
    assert archive.stat().st_mode & 0o777 == 0o600
    with tarfile.open(archive) as stream:
        names=stream.getnames()
        assert not any('registry' in name for name in names)
        manifest=json.load(stream.extractfile('./backup-manifest.json'))
        assert manifest['complete'] is True
        assert set(manifest['components']) == {'mongodb','configuration','operatorConfigurationKey','minio'}
    (key/'deployment-master-key').write_text('7'*64+'\n')
    trace.write_text('')
    run(['bash',str(ROOT/'packaging/scripts/restore-production.sh'),str(archive)],dict(env,CONFIRM_RESTORE='true',HOST_RESTORE_EXIT='1'),success=False)
    assert (key/'deployment-master-key').read_text() == '7'*64+'\n'
    assert 'stop profilemanagement' in trace.read_text()
    assert 'up -d' not in trace.read_text()
    trace.write_text('')
    run(['bash',str(ROOT/'packaging/scripts/restore-production.sh'),str(archive)],dict(env,CONFIRM_RESTORE='true'))
    assert (key/'deployment-master-key').read_text() == '5'*64+'\n'
    assert 'up -d --force-recreate profilemanagement' in trace.read_text()
    (key/'deployment-master-key').unlink()
    run(['bash',str(ROOT/'packaging/scripts/backup-production.sh')],env,success=False)
print('PASS: template purge isolation, all seven selected services, ingress preservation, complete backup and failed/successful Mongo/key restore orchestration.')
