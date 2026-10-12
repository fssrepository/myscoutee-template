#!/usr/bin/env python3
"""Audit all seed inventories, shared definitions and environment boundaries."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SEEDS = ROOT / 'server/docker/conf/mongodb'
from seed_audit_project import SHARED, CONFIGURED, RUNTIME, DEMO_ONLY_RUNTIME, audit_project

def identity(value):
    return value.get('$oid') if isinstance(value, dict) else value

def audit(seeds=SEEDS):
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
    return errors

if __name__ == '__main__':
    errors = audit()
    if errors:
        raise SystemExit('\n'.join(errors))
    print(f'PASS: all {len(SHARED + CONFIGURED + RUNTIME + DEMO_ONLY_RUNTIME)} dev/prod/e2e seed baselines classified and project parity verified.')
