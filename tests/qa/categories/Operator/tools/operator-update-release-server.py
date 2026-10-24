#!/usr/bin/env python3
"""Local HTTPS release fixture. Prepare signed metadata, then serve explicit assets.

No GitHub writes, installed-version changes, update actions, or package mutation.
TLS and release-signing keys must stay outside the served assets.
"""
import argparse
import base64
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import re
import shutil
import ssl
import subprocess
import tempfile
import importlib.util
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[5]


def origin(value):
    url = urlsplit(value)
    if (url.scheme != 'https' or not url.hostname or url.username or url.password
            or url.path not in ('', '/') or url.query or url.fragment):
        raise ValueError('Use an HTTPS origin without credentials or a path.')
    return value.rstrip('/')


def digest(path):
    value = hashlib.sha256()
    with path.open('rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            value.update(chunk)
    return 'sha256:' + value.hexdigest()


def prepare_notification(args):
    """Build an inert signed QA package for release-discovery/notification tests only."""
    if not re.fullmatch(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)-qa\.[1-9]\d*', args.version):
        raise ValueError('Notification fixtures require a canonical QA prerelease version.')
    if args.release.exists() or args.root.exists():
        raise ValueError('Use new release and catalog directories; never replace retained releases.')
    key = args.key.resolve(strict=True)
    if args.key.is_symlink() or key.stat().st_mode & 0o077 or key.is_relative_to(ROOT):
        raise ValueError('Use a protected external QA signing key.')
    project = json.loads((ROOT / 'config/packaging/release-project.json').read_text())['packageName']
    if not re.fullmatch(r'[a-z0-9][a-z0-9+.-]+', project):
        raise ValueError('Invalid release project package name.')
    args.release.mkdir(parents=True, mode=0o700)
    with tempfile.TemporaryDirectory(prefix='notification-release-') as work:
        package = Path(work) / 'package'
        control = package / 'DEBIAN'
        control.mkdir(parents=True)
        control.chmod(0o755)
        (control / 'control').write_text('Package: ' + project + '\nVersion: ' + args.version.replace('-', '~')
            + '\nArchitecture: all\nMaintainer: MyScoutee QA <qa@myscoutee.invalid>\n'
            + 'Description: Inert notification fixture; cannot be installed\n')
        (control / 'preinst').write_text('#!/bin/sh\necho "Notification fixture cannot be installed" >&2\nexit 1\n')
        (control / 'preinst').chmod(0o755)
        deb = args.release / (project + '_' + args.version + '_all.deb')
        subprocess.run(['dpkg-deb', '--root-owner-group', '--build', str(package), str(deb)], check=True)
        client_tools = args.release / (project + '-client-tools_' + args.version + '.tar.gz')
        subprocess.run(['node', str(ROOT / 'config/packaging/node_modules/@fssrepository/myscoutee-packaging/bin/packaging.mjs'), '--project-root', str(ROOT), 'client-tools'], check=True,
            env={**os.environ, 'MYSCOUTEE_VERSION': args.version, 'MYSCOUTEE_DIST_DIR': str(args.release.resolve())})
        spec = importlib.util.spec_from_file_location('release_bundle', Path(os.environ.get('MYSCOUTEE_PACKAGING_SOURCE', ROOT.parent / 'myscoutee-packaging')) / 'release/signing/prepare-release.py')
        bundle = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(bundle)
        bundle.prepare_release(deb, client_tools, key, 'v' + args.version,
            args.installed_version, args.version, args.repository, 'qa', ROOT)
    (args.release / 'NOTIFICATION-FIXTURE.json').write_text(json.dumps({
        'purpose': 'Release discovery and role notification only', 'installable': False,
        'version': args.version, 'applicationImagesBuilt': False}) + '\n')
    args.release = [args.release]
    args.copy_assets = True
    prepare(args)


def origin(value):
    url = urlsplit(value)
    if (url.scheme != 'https' or not url.hostname or url.username or url.password
            or url.path not in ('', '/') or url.query or url.fragment):
        raise ValueError('Use an HTTPS origin without credentials or a path.')
    return value.rstrip('/')


def digest(path):
    value = hashlib.sha256()
    with path.open('rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            value.update(chunk)
    return 'sha256:' + value.hexdigest()


def prepare(args):
    base = origin(args.origin)
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', args.repository):
        raise ValueError('Invalid repository name.')
    key = args.key.resolve(strict=True)
    if args.key.is_symlink() or not key.is_file() or key.stat().st_mode & 0o077:
        raise ValueError('Use a regular external signing key with mode0600.')
    output = args.root.resolve()
    if key.is_relative_to(output) or (output / 'index.json').exists():
        raise ValueError('Use a new fixture directory; keep the signing key outside it.')
    output.mkdir(parents=True, exist_ok=True, mode=0o700)
    paths, releases = {}, []
    for directory in args.release:
        directory = directory.resolve(strict=True)
        envelope = json.loads((directory / 'myscoutee-update.json').read_text())
        manifest = json.loads(base64.b64decode(envelope['manifestBase64'], validate=True))
        version = manifest['release_version']
        if not re.fullmatch(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(-qa\.[1-9]\d*)?', version):
            raise ValueError('Expected a canonical stable or QA release version.')
        tag = 'v' + version
        prefix = f'/{args.repository}/releases/download/{tag}/'
        if any(release['tag_name'] == tag for release in releases):
            raise ValueError('Duplicate release version.')
        package = directory / Path(urlsplit(manifest['artifact_url']).path).name
        if (package.is_symlink() or not package.is_file()
                or package.stat().st_size != manifest['artifact_size_bytes']
                or digest(package) != manifest['artifact_sha256']):
            raise ValueError('Package bytes differ from the signed source metadata.')
        actual_version = subprocess.check_output(['dpkg-deb', '-f', str(package), 'Version'], text=True).strip().replace('~', '-')
        if actual_version != version:
            raise ValueError('Package internal version differs from its release metadata.')
        manifest['artifact_url'] = base + prefix + package.name
        manifest['release_notes_url'] = base + f'/{args.repository}/releases/tag/{tag}'
        target = output / tag
        target.mkdir(mode=0o700)
        source = target / 'manifest.source.json'
        source.write_text(json.dumps(manifest) + '\n')
        signed = target / 'myscoutee-update.json'
        subprocess.run(['python3', str(ROOT / 'config/packaging/node_modules/@fssrepository/myscoutee-packaging/release/signing/sign-update-manifest.py'),
                        '--private-key', str(key), '--manifest', str(source), '--output', str(signed)], check=True)
        if args.copy_assets:
            copied = target / package.name
            shutil.copyfile(package, copied)
            package = copied
        # Package signature remains bound to the unchanged package bytes/version.
        assets = []
        for path in (package, signed):
            route = prefix + path.name
            stored_path = path.relative_to(output) if args.copy_assets else path
            paths[route] = {'path': str(stored_path), 'size': path.stat().st_size, 'sha256': digest(path)}
            assets.append({'name': path.name, 'size': path.stat().st_size,
                           'browser_download_url': base + route})
        releases.append({'tag_name': tag, 'draft': False, 'prerelease': '-qa.' in version, 'assets': assets})
    (output / 'index.json').write_text(json.dumps({'origin': base, 'repository': args.repository,
                                                'paths': paths, 'releases': releases}, indent=2) + '\n')
    print(json.dumps({'origin': base, 'releases': [r['tag_name'] for r in releases],
                      'packagesModified': False, 'githubModified': False}))


def serve(args):
    index = json.loads((args.root / 'index.json').read_text())
    for record in index['paths'].values():
        path = Path(record['path'])
        if not path.is_absolute():
            path = args.root / path
        if path.is_symlink() or digest(path) != record['sha256']:
            raise ValueError('Fixture asset changed since preparation.')
        record['path'] = str(path)
    catalog = json.dumps(index['releases']).encode()
    list_path = f"/repos/{index['repository']}/releases"

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            route = urlsplit(self.path).path
            if route == list_path:
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Content-Length', str(len(catalog)))
                self.end_headers()
                self.wfile.write(catalog)
                return
            record = index['paths'].get(route)
            if record is None:
                self.send_error(404)
                return
            with Path(record['path']).open('rb') as source:
                if os.fstat(source.fileno()).st_size != record['size']:
                    self.send_error(409)
                    return
                self.send_response(200)
                self.send_header('Content-Type', 'application/octet-stream')
                self.send_header('Content-Length', str(record['size']))
                self.end_headers()
                try:
                    sent = 0
                    for chunk in iter(lambda: source.read(1024 * 1024), b''):
                        limit = args.fail_package_after_bytes if route.endswith('.deb') else None
                        if limit is not None:
                            chunk = chunk[:max(0, limit - sent)]
                        self.wfile.write(chunk)
                        sent += len(chunk)
                        if limit is not None and sent >= limit:
                            self.wfile.flush()
                            self.close_connection = True
                            return
                except (BrokenPipeError, ConnectionResetError):
                    pass

        def log_message(self, format, *values):
            # Paths carry only public release names; no headers/authentication logged.
            print(self.log_date_time_string(), format % values, flush=True)

    port = urlsplit(index['origin']).port or 443
    server = ThreadingHTTPServer((args.bind, port), Handler)
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    context.load_cert_chain(args.certificate, args.tls_key)
    server.socket = context.wrap_socket(server.socket, server_side=True)
    print('Serving local release fixture at ' + index['origin'], flush=True)
    server.serve_forever()


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='action', required=True)
    prepare_parser = commands.add_parser('prepare')
    prepare_parser.add_argument('--root', type=Path, required=True)
    prepare_parser.add_argument('--origin', required=True)
    prepare_parser.add_argument('--repository', default='fssrepository/myscoutee')
    prepare_parser.add_argument('--release', type=Path, action='append', required=True)
    prepare_parser.add_argument('--key', type=Path, required=True)
    prepare_parser.add_argument('--copy-assets', action='store_true', help='Copy packages into the fixture and use portable relative asset paths.')
    notification_parser = commands.add_parser('prepare-notification', help='Signed inert QA package; never installs or builds application images.')
    notification_parser.add_argument('--root', type=Path, required=True)
    notification_parser.add_argument('--release', type=Path, required=True)
    notification_parser.add_argument('--origin', required=True)
    notification_parser.add_argument('--repository', default='fssrepository/myscoutee')
    notification_parser.add_argument('--key', type=Path, required=True)
    notification_parser.add_argument('--version', required=True)
    notification_parser.add_argument('--installed-version', required=True)
    serve_parser = commands.add_parser('serve')
    serve_parser.add_argument('--root', type=Path, required=True)
    serve_parser.add_argument('--bind', default='127.0.0.1')
    serve_parser.add_argument('--certificate', type=Path, required=True)
    serve_parser.add_argument('--tls-key', type=Path, required=True)
    serve_parser.add_argument('--fail-package-after-bytes', type=int)
    args = parser.parse_args()
    if args.action == 'serve' and args.fail_package_after_bytes is not None and args.fail_package_after_bytes < 1:
        parser.error('Failure cutoff must be a positive byte count.')
    try:
        {'prepare': prepare, 'prepare-notification': prepare_notification, 'serve': serve}[args.action](args)
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        parser.error(str(error))


if __name__ == '__main__':
    main()
