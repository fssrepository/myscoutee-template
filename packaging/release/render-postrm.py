#!/usr/bin/env python3
"""Embed the selected purge inventory: dpkg retains postrm after /opt removal."""
import json
import re
import sys
from pathlib import Path


def render(packaging):
    release = json.loads((packaging / 'scripts/release-project.json').read_text())
    policy = json.loads((packaging / 'debian/purge-policy.json').read_text())
    if (set(policy) != {'schemaVersion', 'volumes'} or policy['schemaVersion'] != 1
            or not isinstance(policy['volumes'], list) or len(policy['volumes']) > 32):
        raise ValueError('Invalid purge policy')
    rows = []
    for volume in policy['volumes']:
        if (set(volume) != {'environment', 'default'}
                or not re.fullmatch(r'[A-Z][A-Z0-9_]{1,63}', volume['environment'])
                or not re.fullmatch(r'[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}', volume['default'])):
            raise ValueError('Invalid purge volume')
        rows.append(volume['environment'] + '\t' + volume['default'])
    if len({item['environment'] for item in policy['volumes']}) != len(rows):
        raise ValueError('Duplicate purge volume')
    repositories = sorted({image['repository'] for image in release['images']})
    if not repositories or not all(re.fullmatch(r'[a-z][a-z0-9-]{1,63}', value) for value in repositories):
        raise ValueError('Invalid managed repositories')
    source = (packaging / 'debian/postrm').read_text()
    for marker, value in [('PACKAGE_VOLUME_RULES', '\n'.join(rows)),
                          ('PACKAGE_IMAGE_REPOSITORIES', ','.join(repositories))]:
        token = '@' + marker + '@'
        if source.count(token) != 1:
            raise ValueError('Missing or duplicate purge template marker')
        source = source.replace(token, value)
    return source


if __name__ == '__main__':
    try:
        Path(sys.argv[2]).write_text(render(Path(sys.argv[1])))
    except (ValueError, KeyError, TypeError, OSError) as error:
        sys.exit(f'Cannot render package purge inventory: {error}')
