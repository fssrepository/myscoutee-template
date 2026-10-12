#!/usr/bin/env python3
"""Compare installed inputs with the source/DEV owners, without starting services.

Use the builder's deb-root directory, optionally verify every actual DEB byte
against it and inspect the exact bundled images. Generated deployment secrets,
operator state and runtime volumes are not source files.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import tarfile
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def run(*args):
    return subprocess.check_output(args)


def digest(value):
    return hashlib.sha256(value).hexdigest()


def files(path):
    return {str(p.relative_to(path)): p.read_bytes() for p in path.rglob('*') if p.is_file()}


def equal_files(expected, actual, label):
    missing, extra = expected.keys() - actual.keys(), actual.keys() - expected.keys()
    changed = [name for name in expected.keys() & actual.keys() if expected[name] != actual[name]]
    assert not (missing or extra or changed), f'{label}: missing={sorted(missing)}, extra={sorted(extra)}, changed={sorted(changed)}'
    print(f'PASS: {label}: {len(expected)} files')


def image_files(container, path):
    archive = run('docker', 'cp', container + ':' + path, '-')
    with tarfile.open(fileobj=io.BytesIO(archive)) as stream:
        return {entry.name.split('/', 1)[-1]: stream.extractfile(entry).read()
                for entry in stream if entry.isfile()}


def audit_images(installed):
    manifest = json.loads((installed / 'packaging/IMAGE-MANIFEST.json').read_text())
    nginx_error_page = None
    for row in manifest['images']:
        actual_id = run('docker', 'image', 'inspect', '--format', '{{.Id}}', row['reference']).decode().strip()
        assert actual_id == row['imageId'], f'image changed: {row["reference"]}'
        container = run('docker', 'create', row['imageId']).decode().strip()
        try:
            if row['service'] == 'nginx-base':
                nginx_error_page = next(iter(image_files(container, '/usr/share/nginx/html/50x.html').values()))
            elif row['service'] == 'nginx':
                assert nginx_error_page is not None, 'base image must precede the frontend image'
                expected_frontend = {'50x.html': nginx_error_page, **files(ROOT / 'frontend/prod')}
                equal_files(expected_frontend, image_files(container, '/usr/share/nginx/html/.'), 'production frontend assets and unchanged base error page')
                nginx = ROOT / 'server/docker'
                pairs = {'conf/nginx/nginx.conf': '/etc/nginx/nginx.conf',
                         'conf/nginx/conf/http/spring.conf': '/etc/nginx/myscoutee-templates/spring.conf',
                         'conf/nginx/conf/stream/mongodb.conf': '/etc/nginx/stream_conf.d/mongodb.conf',
                         'images/nginx/prod/nginx-entrypoint.sh': '/scripts/nginx-production-entrypoint.sh',
                         'images/nginx/runtime-status.sh': '/scripts/runtime-status.sh'}
                for path in ['runtime-status.conf', 'update-status.prod.conf', 'http-transport-policy.prod.conf', 'https-response-policy.prod.conf']:
                    pairs['conf/nginx/conf/http/' + path] = '/etc/nginx/conf.d/' + path.replace('-policy.prod', '-policy')
                for source, target in pairs.items():
                    assert list(image_files(container, target).values()) == [(nginx / source).read_bytes()], source
                routing = (nginx / 'conf/nginx/conf/http/routing.conf').read_bytes() + b'\ninclude /etc/nginx/conf.d/update-status.prod.conf;\n'
                assert list(image_files(container, '/etc/nginx/conf.d/routing.conf').values()) == [routing]
                print('PASS: Nginx shared routes, production transport policy and entrypoint')
            elif row['service'] == 'minio':
                equal_files(files(ROOT / 'server/docker/conf/minio'), image_files(container, '/seed/.'), 'DEV media seed → production image')
                for name in ['bootstrap_minio_seed.sh', 'start_minio_with_seed.sh']:
                    assert list(image_files(container, '/scripts/' + name).values()) == [(ROOT / 'server/docker/scripts' / name).read_bytes()]
                print('PASS: MinIO bootstrap scripts')
            elif row['service'] == 'profilemanagement':
                jar = next(iter(image_files(container, '/app/spring-boot-application.jar').values()))
                expected = ROOT / ('server/projects/profile/build/libs/profile-' + manifest['releaseVersion'] + '.jar')
                assert digest(jar) == digest(expected.read_bytes()), 'API image has a stale JAR'
                with zipfile.ZipFile(io.BytesIO(jar)) as archive:
                    for name, content in files(ROOT / 'server/projects/profile/src/main/resources').items():
                        assert archive.read('BOOT-INF/classes/' + name) == content, 'JAR resource: ' + name
                print('PASS: exact built JAR and all source classpath resources (including DEV/PROD configuration)')
            else:
                entrypoint = json.loads(run('docker', 'image', 'inspect', '--format', '{{json .Config.Entrypoint}}', row['imageId']))
                if entrypoint and entrypoint[0].startswith('/'):
                    assert image_files(container, entrypoint[0]), 'missing image entrypoint'
            print('PASS: bundled image identity and input check: ' + row['service'])
        finally:
            subprocess.run(['docker', 'rm', '-v', container], check=True, stdout=subprocess.DEVNULL)


def audit_deb(deb, stage):
    # Read the full artifact, including the image archives. No deployment starts.
    expected = {str(p.relative_to(stage)): p for p in stage.rglob('*') if p.is_file() and 'DEBIAN' not in p.relative_to(stage).parts}
    checked = set()
    process = subprocess.Popen(['dpkg-deb', '--fsys-tarfile', str(deb)], stdout=subprocess.PIPE)
    try:
        with tarfile.open(fileobj=process.stdout, mode='r|') as stream:
            for entry in stream:
                assert entry.uid == entry.gid == 0 and not entry.issym() and not entry.islnk(), entry.name
                if not entry.isfile():
                    continue
                name = entry.name.removeprefix('./')
                assert name in expected, 'unexpected DEB file: ' + name
                with stream.extractfile(entry) as source, expected[name].open('rb') as reference:
                    while chunk := source.read(1024 * 1024):
                        assert chunk == reference.read(len(chunk)), 'DEB bytes differ: ' + name
                    assert not reference.read(1), 'truncated DEB file: ' + name
                assert entry.mode == expected[name].stat().st_mode & 0o777, name
                checked.add(name)
        assert process.wait() == 0 and checked == expected.keys(), 'DEB inventory differs from verified stage'
    finally:
        process.stdout.close()
        if process.poll() is None:
            process.terminate()
            process.wait()
    print(f'PASS: actual DEB bytes, modes, ownership and complete inventory: {len(checked)} files')
    with tempfile.TemporaryDirectory() as directory:
        subprocess.run(['dpkg-deb', '-e', str(deb), directory], check=True)
        equal_files(files(stage / 'DEBIAN'), files(Path(directory)), 'actual DEB control files')


def audit_environments():
    # Render each actual composition independently. E2E must never inherit DEV
    # services, credentials, source mounts or databases by layering the files.
    probe = dict(os.environ, MYSCOUTEE_VERSION='8.7.6', CHAT_CALL_STUN_PORT='34789',
                 CHAT_CALL_STUN_URLS='stun:fixture.invalid:34789',
                 MYSCOUTEE_UPDATE_TLS_CA_CERTIFICATE='/fixture/ca.pem')
    project = json.loads((ROOT / 'packaging/scripts/release-project.json').read_text())
    definitions = {
        'dev': ROOT / 'server/docker-compose-dev.yml',
        'prod': ROOT / 'server/docker-compose-prod.yml',
        'e2e': ROOT / 'server/docker-compose.e2e.yml',
        'installed': ROOT / 'packaging/compose/docker-compose.production.yml'
    }
    for mode, source in definitions.items():
        command = ['docker', 'compose', '-f', str(source)]
        if mode == 'installed':
            command += ['--env-file', str(ROOT / 'packaging/templates' / (project['packageName'] + '.env'))]
        result = subprocess.run(command + ['config', '--no-env-resolution', '--format', 'json'],
                                env=probe, text=True, capture_output=True, check=True)
        services = json.loads(result.stdout)['services']
        api = services['profilemanagement']
        environment = api['environment']
        assert services['mongodb']['ulimits']['nofile'] == {'soft': 64000, 'hard': 64000}, \
            mode + ': Mongo open-file limits must not depend on Docker host defaults'
        assert 'stun' in services, mode + ': missing STUN service'
        assert not any(key.startswith('CHAT_CALL_') for key in services['mongodb']['environment']), mode
        assert environment['SPRING_PROFILES_ACTIVE'] == ('dev' if mode == 'dev' else 'prod'), mode
        if mode != 'e2e':
            assert environment['CHAT_CALL_STUN_PORT'] == probe['CHAT_CALL_STUN_PORT'], mode
            assert environment['CHAT_CALL_STUN_URLS'] == probe['CHAT_CALL_STUN_URLS'], mode
        if mode in ('prod', 'e2e'):
            assert api['build']['args']['MYSCOUTEE_VERSION'] == '8.7.6', mode + ': release version not passed to Docker build'
        if mode in ('prod', 'installed'):
            assert environment['OPERATOR_UPDATE_TLS_CA_CERTIFICATE'] == '/fixture/ca.pem', mode + ': update CA ignored'
        if mode == 'e2e':
            mongo = services['mongodb']['environment']
            assert mongo['MONGO_SEED_DIRECTORY'] == 'e2e_db'
            assert environment['MONGODB_DATABASE'] == environment['MONGODB_DEMO_DATABASE'] == mongo['MONGO_E2E_DATABASE']
            assert all(volume['type'] != 'bind' for service in services.values() for volume in service.get('volumes', [])), 'E2E contains host/source bind mounts'
        for name, service in services.items():
            assert all(volume.get('target') != '/etc/timezone' for volume in service.get('volumes', [])), \
                mode + ': host /etc/timezone is not a portable file mount; use TZ and /etc/localtime'
            build = service.get('build')
            if build:
                context = Path(build['context'])
                assert context.is_dir(), mode + ': missing build context for ' + name
                assert (context / build.get('dockerfile', 'Dockerfile')).is_file(), mode + ': missing Dockerfile for ' + name
        print(f'PASS: {mode}: composed services, nondefault configuration wiring, version and deployment isolation')
    for profile in ('dev', 'prod'):
        resource = (ROOT / f'server/projects/profile/src/main/resources/application-{profile}.yaml').read_text()
        assert '${EMAIL_SENDGRID_API_KEY:' in resource, profile + ': packaged email secret is ignored'
        assert '${EMAIL_SENDGRID_ENDPOINT:' in resource, profile + ': email endpoint override is ignored'
        if profile == 'prod':
            assert '${EMAIL_BASE_URL:https://localhost}' in resource, 'production email URL lost its HTTPS default'
    for mode in ('prod', 'e2e'):
        dockerfile = (ROOT / f'server/docker/images/profilemanagement/{mode}/Dockerfile').read_text()
        assert 'ARG MYSCOUTEE_VERSION=' in dockerfile, mode
        if mode == 'prod':
            assert re.search(r'^COPY (?:--chown=\S+ )?profile-\$\{MYSCOUTEE_VERSION\}\.jar ', dockerfile, re.M), 'production selects an ambiguous JAR'
        else:
            assert 'ENV MYSCOUTEE_VERSION=${MYSCOUTEE_VERSION}' in dockerfile, 'E2E Gradle build ignores version'
    print('PASS: DEV/PROD email credentials and exact production/E2E build version')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('stage', type=Path, nargs='?')
    parser.add_argument('--environments-only', action='store_true')
    parser.add_argument('--deb', type=Path)
    parser.add_argument('--images', action='store_true')
    args = parser.parse_args()
    if args.environments_only:
        audit_environments()
        return
    if args.stage is None:
        parser.error('stage is required unless --environments-only is selected')
    project = json.loads((ROOT / 'packaging/scripts/release-project.json').read_text())
    package = project['packageName']
    installed = args.stage / 'opt' / package
    subprocess.run(['node', str(ROOT / 'packaging/tests/runtime-index-parity-test.mjs')], check=True)
    subprocess.run(['node', str(ROOT / 'packaging/tests/release-catalog-parity-test.mjs')], check=True)
    subprocess.run(['python3', str(ROOT / 'tools/seeds/check-system-seed-parity.py'), '--packaged-root', str(installed)], check=True)
    for directory in ['packaging/scripts', 'packaging/migrations', 'packaging/templates']:
        expected = files(ROOT / directory)
        if directory.endswith('/scripts'):
            expected = {name: body for name, body in expected.items() if '/' not in name and Path(name).suffix in ('.sh', '.py', '.js', '.jq', '.json')}
        if directory.endswith('/templates'):
            name = package + '.env'
            version = (installed / 'packaging/VERSION').read_text().strip()
            text = re.sub(r'^MYSCOUTEE_VERSION=.*$', lambda _: 'MYSCOUTEE_VERSION=' + version, expected[name].decode(), flags=re.M)
            for image in project['images']:
                if subprocess.run(['dpkg', '--compare-versions', version.replace('-', '~', 1), 'ge',
                                   image['sinceVersion'].replace('-', '~', 1)]).returncode != 0:
                    continue
                value = image['repository'] + ':' + version + '-' + image['tagSuffix']
                text = re.sub(r'^' + image['environment'] + '=.*$', lambda _: image['environment'] + '=' + value, text, flags=re.M)
            expected[name] = text.encode()
        equal_files(expected, files(installed / directory), directory)
    expected = {}
    for name in json.loads((ROOT / 'packaging/release/runtime-scripts.json').read_text()):
        path = ROOT / 'server/docker/scripts' / name
        expected.update({name + '/' + key: value for key, value in files(path).items()} if path.is_dir() else {name: path.read_bytes()})
    equal_files(expected, files(installed / 'server/docker/scripts'), 'selected host runtime scripts')
    equal_files(files(ROOT / 'packaging/systemd'), files(args.stage / 'lib/systemd/system'), 'systemd units')
    equal_files(files(ROOT / 'packaging/sysusers'), files(args.stage / 'usr/lib/sysusers.d'), 'system users')
    pairs = [('server/docker-compose-stun.yml', 'server/docker-compose-stun.yml'),
             ('packaging/compose/docker-compose.production-replicaset.yml', 'server/docker-compose.replicaset.yml'),
             ('server/projects/profile/secrets/README.md', 'server/projects/profile/secrets/README.md'),
             ('server/projects/profile/secrets/firebase-service-account.json.example', 'server/projects/profile/secrets/firebase-service-account.json.example')]
    for source, target in pairs:
        assert (ROOT / source).read_bytes() == (installed / target).read_bytes(), target
    compose = (ROOT / 'packaging/compose/docker-compose.production.yml').read_text().replace('docker-compose.production-replicaset.yml', 'docker-compose.replicaset.yml')
    assert (installed / 'server/docker-compose-prod.yml').read_text() == compose
    for name in ['postinst', 'prerm']:
        assert (args.stage / 'DEBIAN' / name).read_bytes() == (ROOT / 'packaging/debian' / name).read_bytes(), name
    with tempfile.TemporaryDirectory() as directory:
        expected = Path(directory) / 'postrm'
        subprocess.run(['python3', str(ROOT / 'packaging/release/render-postrm.py'), str(ROOT / 'packaging'), str(expected)], check=True)
        assert (args.stage / 'DEBIAN/postrm').read_bytes() == expected.read_bytes()
    print('PASS: installed Compose, replica set, STUN, secret provisioning instructions and package lifecycle scripts')
    if args.images:
        audit_images(installed)
    if args.deb:
        audit_deb(args.deb, args.stage)


if __name__ == '__main__':
    main()
