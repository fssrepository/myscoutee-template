#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/release-project.sh"
release_project_load "${SCRIPT_DIR}" "$(stat -c %u "${BASH_SOURCE[0]}")" || {
  echo 'The package-owned release project policy is missing or unsafe.' >&2; exit 1;
}
database="${MYSCOUTEE_MIGRATION_DATABASE:-production_db}"
case "$database" in production_db|demo_db) ;; *) echo 'Unsupported migration database.' >&2; exit 1;; esac
exec 9>/run/lock/${RELEASE_PROJECT_NAME}-data-migrations.lock
flock -n 9 || { echo 'Another data migration is running.' >&2; exit 1; }
directory="/tmp/${RELEASE_PROJECT_NAME}-migrations-$$"
trap 'docker exec mongodb rm -rf "$directory" >/dev/null' EXIT
docker cp "${SCRIPT_DIR}/../migrations" "mongodb:$directory"
docker cp "${SCRIPT_DIR}/release-system-seeds.json" "mongodb:$directory/release-system-seeds.json"
for script in run-data-migrations.js sync-release-system-seeds.js; do
 docker exec -i -e "MYSCOUTEE_MIGRATION_DIRECTORY=$directory" \
  -e "MYSCOUTEE_MIGRATION_SEED_DIRECTORY=${MYSCOUTEE_MIGRATION_SEED_DIRECTORY:-/docker-entrypoint-initdb.d/myscoutee_db}" \
  mongodb sh -c 'exec mongosh --quiet --username "$MONGO_INITDB_ROOT_USERNAME" \
    --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin "$1" --file /dev/stdin' qa "$database" \
  < "${SCRIPT_DIR}/$script"
done
