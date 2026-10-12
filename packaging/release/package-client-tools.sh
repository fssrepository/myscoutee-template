#!/usr/bin/env bash
set -euo pipefail

# Build only the small client archive. No DEB, images or application build.
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
VERSION="${MYSCOUTEE_VERSION:-1.3.0}"
DIST_DIR="${MYSCOUTEE_DIST_DIR:-${REPO_ROOT}/packaging/dist}"
if [[ ! "${VERSION}" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-qa\.[1-9][0-9]*)?$ ]]; then
  echo "MYSCOUTEE_VERSION must be a SemVer core or QA prerelease." >&2
  exit 1
fi
source "${REPO_ROOT}/packaging/scripts/release-project.sh"
release_project_load "${REPO_ROOT}/packaging/scripts" "$(id -u)" || {
  echo 'Invalid release project policy.' >&2; exit 1;
}
install -d "${DIST_DIR}"
WORK_DIR="$(mktemp -d "${DIST_DIR}/.client-tools.XXXXXX")"
trap 'rm -rf -- "${WORK_DIR}"' EXIT
install -d "${WORK_DIR}/payload/verify-deployment"
selection="${REPO_ROOT}/packaging/client-tools/manifest.json"
jq -e 'type == "array" and length > 0 and length == (unique | length)
  and all(.[]; type == "string" and
    (. == "README.md" or . == "install.env.example" or test("^[A-Za-z0-9][A-Za-z0-9_-]*\\.sh$")))
  and (index("install.sh") != null) and (index("verify-release.sh") != null)
  and (index("remote-transport.sh") != null)' "$selection" >/dev/null || {
  echo 'Invalid client-tools selection.' >&2; exit 1;
}
while IFS= read -r helper; do
  [[ -f "${REPO_ROOT}/packaging/client-tools/${helper}" && ! -L "${REPO_ROOT}/packaging/client-tools/${helper}" ]] || {
    echo "Client helper must be a regular file: ${helper}" >&2; exit 1;
  }
  install -m 0644 "${REPO_ROOT}/packaging/client-tools/${helper}" "${WORK_DIR}/payload/${helper}"
done < <(jq -r '.[]' "$selection")
cp -a "${REPO_ROOT}/packaging/verify-deployment/." "${WORK_DIR}/payload/verify-deployment/"
for helper in release-project.sh release-project.json; do
  install -m 0644 "${REPO_ROOT}/packaging/scripts/${helper}" "${WORK_DIR}/payload/${helper}"
done
printf '%s\n' "${VERSION}" > "${WORK_DIR}/payload/VERSION"
find "${WORK_DIR}/payload" -type d -exec chmod 0755 {} +
find "${WORK_DIR}/payload" -type f -exec chmod 0644 {} +
find "${WORK_DIR}/payload" -type f -name '*.sh' -exec chmod 0755 {} +
chmod 0755 "${WORK_DIR}/payload/verify-deployment/run.mjs"
archive="${RELEASE_PROJECT_NAME}-client-tools_${VERSION}.tar.gz"
tar --sort=name --mtime="@${SOURCE_DATE_EPOCH:-0}" --owner=0 --group=0 --numeric-owner \
  -C "${WORK_DIR}/payload" -cf - . | gzip -n > "${WORK_DIR}/${archive}"
chmod 0644 "${WORK_DIR}/${archive}"
(cd "${WORK_DIR}" && sha256sum "${archive}" > "${archive}.sha256")
chmod 0644 "${WORK_DIR}/${archive}.sha256"
mv -f -- "${WORK_DIR}/${archive}" "${DIST_DIR}/${archive}"
mv -f -- "${WORK_DIR}/${archive}.sha256" "${DIST_DIR}/${archive}.sha256"
echo "Client tools ready: ${DIST_DIR}/${archive}"
