#!/bin/sh
# Development-only wrapper around the existing signed release/download fixture.
set -eu
umask 077
mkdir -p /data/tls /data/catalog
if [ ! -f /data/tls/certificate.pem ]; then
  openssl req -x509 -newkey rsa:2048 -nodes -days 365 \
    -keyout /data/tls/private-key.pem -out /data/tls/certificate.pem \
    -subj '/CN=release-simulator' \
    -addext 'subjectAltName=DNS:release-simulator,DNS:localhost,IP:127.0.0.1' \
    -addext 'basicConstraints=critical,CA:TRUE' >/dev/null 2>&1
  chmod 644 /data/tls/certificate.pem
fi
if [ ! -f /data/catalog/index.json ]; then
  # A clean checkout has no locally built releases. Never fall back to GitHub.
  python3 - <<'PY'
import json, os, re
from pathlib import Path
repository = os.environ.get('RELEASE_SIMULATOR_REPOSITORY', 'fssrepository/myscoutee')
if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repository):
    raise SystemExit('Invalid release simulator repository')
Path('/data/catalog/index.json').write_text(json.dumps({
    'origin': 'https://release-simulator:18445',
    'repository': repository, 'paths': {}, 'releases': []}) + '\n')
PY
fi
set -- python3 /workspace/tests/qa/categories/Operator/tools/operator-update-release-server.py serve \
  --root /data/catalog --bind 0.0.0.0 \
  --certificate /data/tls/certificate.pem --tls-key /data/tls/private-key.pem
if [ -n "${RELEASE_SIMULATOR_FAIL_PACKAGE_AFTER_BYTES:-}" ]; then
  set -- "$@" --fail-package-after-bytes "$RELEASE_SIMULATOR_FAIL_PACKAGE_AFTER_BYTES"
fi
exec "$@"
