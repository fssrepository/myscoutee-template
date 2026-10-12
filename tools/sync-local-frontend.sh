#!/usr/bin/env bash
set -euo pipefail
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
[[ "$(git -C "$root" config -f .gitmodules --get submodule.frontend.url)" == ./tools/sources/frontend.bundle ]] || {
  echo 'Frontend now uses a repository URL; publish its commit to that repository.' >&2; exit 1;
}
[[ -e "$root/frontend/.git" && -z "$(git -C "$root/frontend" status --porcelain)" ]] || {
  echo 'Commit frontend and component changes before updating the local bundle.' >&2; exit 1;
}
install -d "$root/tools/sources"
temporary="$(mktemp "$root/tools/sources/.frontend.XXXXXX")"
trap 'rm -f -- "$temporary"' EXIT
git -C "$root/frontend" bundle create "$temporary" HEAD
git -C "$root/frontend" bundle verify "$temporary"
chmod 0644 "$temporary"
mv -- "$temporary" "$root/tools/sources/frontend.bundle"
echo 'Stage frontend and tools/sources/frontend.bundle together in the parent commit.'
