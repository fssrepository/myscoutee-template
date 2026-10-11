#!/usr/bin/env bash
set -uo pipefail

seed_root="${MONGODB_SEED_ROOT:-/docker-entrypoint-initdb.d}"
seed_directory_name="${MONGO_SEED_DIRECTORY:-demo_db}"
watch_root="${seed_root}/${seed_directory_name}"
target_database="${MONGO_APP_DATABASE:-${MONGO_DEMO_DATABASE:-demo_db}}"
auth_database="${MONGO_AUTH_DATABASE:-${target_database}}"
mongo_host="${MONGO_IMPORT_HOST:-mongodb}"
mongo_port="${MONGO_IMPORT_PORT:-27017}"
mongo_username="${MONGO_IMPORT_USERNAME:-${MONGO_APP_USERNAME:-guest}}"
mongo_password="${MONGO_IMPORT_PASSWORD:-${MONGO_APP_PASSWORD:-guest}}"
mongo_import_script="${MONGO_IMPORT_SCRIPT:-${seed_root}/mongo_import.sh}"
debounce_seconds="${MONGODB_SEED_SYNC_DEBOUNCE_SECONDS:-1}"
reconcile_seconds="${MONGODB_SEED_SYNC_RECONCILE_SECONDS:-2}"
max_reconcile_passes="${MONGODB_SEED_SYNC_MAX_RECONCILE_PASSES:-0}"
# This state belongs to this watcher. Startup still imports the complete seed,
# so no on-disk cache needs to survive a process restart.
declare -A fingerprints=()
declare -A content_hashes=()

log() {
  printf '[mongodb-seed-sync] %s\n' "$*"
}

fail() {
  printf '[mongodb-seed-sync] ERROR: %s\n' "$*" >&2
  exit 1
}

is_non_negative_integer() {
  [[ "$1" =~ ^[0-9]+$ ]]
}

is_positive_integer() {
  [[ "$1" =~ ^[1-9][0-9]*$ ]]
}

is_positive_number() {
  [[ "$1" =~ ^[0-9]+([.][0-9]+)?$ ]] && [[ "$1" != "0" ]] && [[ "$1" != "0.0" ]]
}

file_fingerprint() {
  local file="$1"
  stat -L -c '%s:%y:%z:%i' -- "${file}" 2>/dev/null
}

file_content_hash() {
  local file="$1"
  local digest
  digest="$(sha256sum < "${file}")" || return 1
  printf '%s' "${digest%% *}"
}

seed_file_fingerprints() {
  # One stat process per argument-size batch, rather than several subprocesses
  # per file. NUL-delimited pairs also preserve spaces/newlines in paths.
  seed_files | xargs -0 -r stat -L --printf='%n\0%s:%y:%z:%i\0' --
}

seed_files() {
  find "${watch_root}" -maxdepth 1 \( -type f -o -type l \) -name '*.json' \
    ! -name 'i18n_bundles.json' -print0 | sort -z
  # Publish the i18n version only after every changed message document has
  # been imported. Otherwise the API may cache a new version while the
  # replacement i18n_messages collection is still empty or incomplete.
  find "${watch_root}" -maxdepth 1 \( -type f -o -type l \) -name 'i18n_bundles.json' -print0 \
    | sort -z
}

wait_until_file_stable() {
  local file="$1"
  local previous=""
  local current=""
  while [[ -f "${file}" ]]; do
    previous="$(file_fingerprint "${file}")"
    sleep "${debounce_seconds}"
    current="$(file_fingerprint "${file}")"
    if [[ -n "${current}" && "${previous}" == "${current}" ]]; then
      printf '%s' "${current}"
      return 0
    fi
    log "Seed file changed while settling; waiting again: ${file}" >&2
  done
  return 1
}

run_import_all() {
  MONGO_IMPORT_HOST="${mongo_host}" \
  MONGO_IMPORT_PORT="${mongo_port}" \
  MONGO_IMPORT_USERNAME="${mongo_username}" \
  MONGO_IMPORT_PASSWORD="${mongo_password}" \
  MONGO_APP_DATABASE="${target_database}" \
  MONGO_SEED_DIRECTORY="${seed_directory_name}" \
  MONGO_IMPORT_REPLACE_COLLECTIONS=true \
    "${mongo_import_script}"
}

run_import_file() {
  local file="$1"
  MONGO_IMPORT_HOST="${mongo_host}" \
  MONGO_IMPORT_PORT="${mongo_port}" \
  MONGO_IMPORT_USERNAME="${mongo_username}" \
  MONGO_IMPORT_PASSWORD="${mongo_password}" \
  MONGO_APP_DATABASE="${target_database}" \
  MONGO_SEED_DIRECTORY="${seed_directory_name}" \
  MONGO_IMPORT_SINGLE_FILE="${file}" \
  MONGO_IMPORT_REPLACE_COLLECTIONS=true \
    "${mongo_import_script}"
}

import_file_until_stable() {
  local file="$1"
  local before=""
  local after=""
  local collection=""
  local digest=""

  while [[ -f "${file}" ]]; do
    before="$(wait_until_file_stable "${file}")" || return 0
    digest="$(file_content_hash "${file}")" || return 1
    after="$(file_fingerprint "${file}")" || continue
    [[ "${before}" == "${after}" ]] || continue
    if [[ "${digest}" == "${content_hashes[${file}]:-}" ]]; then
      # A touch or an identical rewrite does not reset the Mongo collection.
      fingerprints["${file}"]="${after}"
      return 0
    fi
    collection="${file##*/}"
    collection="${collection%.json}"
    log "Importing ${target_database}.${collection} from ${file}"
    # An importer may partially write before failing, or read a newer version
    # than we hashed. Never deduplicate against the old successful import then.
    unset 'fingerprints[${file}]' 'content_hashes[${file}]'
    if ! run_import_file "${file}"; then
      log "Import failed; the unchanged fingerprint was not acknowledged and will be retried: ${file}"
      return 1
    fi
    after="$(file_fingerprint "${file}")"
    if [[ -n "${after}" && "${before}" == "${after}" ]]; then
      fingerprints["${file}"]="${after}"
      content_hashes["${file}"]="${digest}"
      log "Imported stable seed ${target_database}.${collection} (${after})"
      return 0
    fi
    log "Seed changed during import; importing the newer version next: ${file}"
  done
}

prime_fingerprint_state() {
  local file=""
  local fingerprint=""
  local digest=""
  while IFS= read -r -d '' file && IFS= read -r -d '' fingerprint; do
    digest="$(file_content_hash "${file}")" || continue
    if [[ "${fingerprint}" == "$(file_fingerprint "${file}")" ]]; then
      fingerprints["${file}"]="${fingerprint}"
      content_hashes["${file}"]="${digest}"
    fi
  done < <(seed_file_fingerprints)
}

reconcile_changed_files() {
  local file=""
  local current=""
  local failures=0
  local -A present=()
  while IFS= read -r -d '' file && IFS= read -r -d '' current; do
    present["${file}"]=1
    if [[ "${current}" != "${fingerprints[${file}]:-}" ]]; then
      import_file_until_stable "${file}" || failures=$((failures + 1))
    fi
  done < <(seed_file_fingerprints)
  for file in "${!fingerprints[@]}"; do
    if [[ -z "${present[${file}]:-}" ]]; then
      unset 'fingerprints[${file}]' 'content_hashes[${file}]'
    fi
  done
  return "${failures}"
}

wait_for_mongo() {
  until mongosh \
      --host "${mongo_host}" \
      --port "${mongo_port}" \
      --username "${mongo_username}" \
      --password "${mongo_password}" \
      --authenticationDatabase "${auth_database}" \
      --quiet \
      "${target_database}" \
      --eval 'db.runCommand({ ping: 1 }).ok' >/dev/null 2>&1; do
    log "Waiting for MongoDB ${mongo_host}:${mongo_port}/${target_database}"
    sleep 2
  done
}

wait_for_change_or_reconcile_deadline() {
  if command -v inotifywait >/dev/null 2>&1; then
    inotifywait \
      -q \
      -r \
      -t "${reconcile_seconds}" \
      -e close_write,create,move,delete \
      "${watch_root}" >/dev/null 2>&1 || true
    return
  fi
  sleep "${reconcile_seconds}"
}

[[ -d "${watch_root}" ]] || fail "Seed directory does not exist: ${watch_root}"
[[ -x "${mongo_import_script}" ]] || fail "Mongo import script is not executable: ${mongo_import_script}"
is_positive_number "${debounce_seconds}" || fail "MONGODB_SEED_SYNC_DEBOUNCE_SECONDS must be positive."
is_positive_integer "${reconcile_seconds}" || fail "MONGODB_SEED_SYNC_RECONCILE_SECONDS must be a positive integer."
is_non_negative_integer "${max_reconcile_passes}" || fail "MONGODB_SEED_SYNC_MAX_RECONCILE_PASSES must be a non-negative integer."

wait_for_mongo

# Record the pre-import state first. If any file changes while the initial
# import is running, the first reconciliation pass sees the newer fingerprint.
prime_fingerprint_state
until run_import_all; do
  log "Initial seed import failed; retrying without acknowledging any new file fingerprints."
  sleep 2
done
# If a seed changed during startup, the importer may have read an intermediate
# version. Even reverting to the original content must be imported again.
while IFS= read -r -d '' file && IFS= read -r -d '' fingerprint; do
  if [[ "${fingerprint}" != "${fingerprints[${file}]:-}" ]]; then
    unset 'content_hashes[${file}]'
  fi
done < <(seed_file_fingerprints)
log "Initial seed import completed for ${target_database}."

reconcile_pass=0
while true; do
  wait_for_change_or_reconcile_deadline
  reconcile_changed_files || true
  reconcile_pass=$((reconcile_pass + 1))
  if (( max_reconcile_passes > 0 && reconcile_pass >= max_reconcile_passes )); then
    log "Stopped after ${reconcile_pass} reconciliation pass(es) as requested."
    exit 0
  fi
done
