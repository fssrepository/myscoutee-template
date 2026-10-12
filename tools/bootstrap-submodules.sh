#!/usr/bin/env bash
set -euo pipefail
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"
url="$(git config -f .gitmodules --get submodule.frontend.url)"
if [[ -e frontend/.git && -n "$(git -C frontend status --porcelain)" ]]; then
  echo 'Commit or preserve frontend changes before changing its checkout.' >&2
  exit 1
fi
if [[ "$url" == ./tools/sources/frontend.bundle ]]; then
  bundle="$root/tools/sources/frontend.bundle"
  [[ -f "$bundle" && ! -L "$bundle" ]] || { echo 'Missing local frontend bundle.' >&2; exit 1; }
  git bundle verify "$bundle"
  expected="$(git ls-files --stage frontend | awk '$1 == "160000" {print $2}')"
  [[ -n "$expected" && "$(git bundle list-heads "$bundle" HEAD | cut -d ' ' -f 1)" == "$expected" ]] || {
    echo 'The frontend gitlink and bundle must identify the same commit.' >&2; exit 1;
  }
  git submodule init -- frontend
  git config submodule.frontend.url "$bundle"
  git -c protocol.file.allow=always submodule update --init --checkout -- frontend
else
  # Once the developer redirects .gitmodules to a real repository, ordinary
  # Git owns its checkout. The local bundle is no longer needed.
  git submodule sync -- frontend
  git submodule update --init --checkout -- frontend
fi
git -C frontend submodule update --init --checkout -- components
echo 'Frontend and remote components are checked out at their recorded commits.'
