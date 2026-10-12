#!/usr/bin/env bash
set -euo pipefail
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"
if [[ -e frontend/.git && -n "$(git -C frontend status --porcelain)" ]]; then
  echo 'Commit or preserve frontend changes before changing its checkout.' >&2
  exit 1
fi
git submodule sync -- frontend
git submodule update --init --checkout -- frontend
git -C frontend submodule update --init --checkout -- components
echo 'Frontend and remote components are checked out at their recorded commits.'
