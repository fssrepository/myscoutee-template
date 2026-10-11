#! /bin/bash
set -euo pipefail

db_name="${MONGO_APP_DATABASE:-${MONGO_INITDB_DATABASE:-demo_db}}"
seed_dir_name="${MONGO_SEED_DIRECTORY:-${db_name}}"
seed_root="${MONGO_SEED_ROOT:-/docker-entrypoint-initdb.d}"
mongo_host="${MONGO_IMPORT_HOST:-127.0.0.1}"
mongo_port="${MONGO_IMPORT_PORT:-27017}"
mongo_username="${MONGO_IMPORT_USERNAME:-${MONGO_APP_USERNAME:-guest}}"
mongo_password="${MONGO_IMPORT_PASSWORD:-${MONGO_APP_PASSWORD:-guest}}"
replace_collections="${MONGO_IMPORT_REPLACE_COLLECTIONS:-false}"
single_file="${MONGO_IMPORT_SINGLE_FILE:-}"
# The adjacent policy travels with this script in Compose, DEB, E2E and KVM bundles.
source "$(dirname -- "${BASH_SOURCE[0]}")/mongo-seed-policy.inc"

json_file_has_documents() {
  local jsonfile="$1"

  grep -q '{' "${jsonfile}"
}

database_has_wildcard_seed_rule() {
  local target_db="$1"
  local old_ifs="${IFS}"
  local db_rules=()

  IFS=';'
  read -r -a db_rules <<< "${allowed_collections_by_db}"
  IFS="${old_ifs}"

  local db_rule
  for db_rule in "${db_rules[@]}"; do
    local rule_db="${db_rule%%:*}"
    local collections_csv="${db_rule#*:}"

    if [ "${rule_db}" != "${target_db}" ]; then
      continue
    fi

    local collection_rules=()
    IFS=','
    read -r -a collection_rules <<< "${collections_csv}"
    IFS="${old_ifs}"

    local allowed_collection
    for allowed_collection in "${collection_rules[@]}"; do
      if [ "${allowed_collection}" = "*" ]; then
        return 0
      fi
    done
  done

  return 1
}

collection_is_seed_managed() {
  local target_db="$1"
  local collection_name="$2"
  local old_ifs="${IFS}"
  local db_rules=()

  IFS=';'
  read -r -a db_rules <<< "${allowed_collections_by_db}"
  IFS="${old_ifs}"

  local db_rule
  for db_rule in "${db_rules[@]}"; do
    local rule_db="${db_rule%%:*}"
    local collections_csv="${db_rule#*:}"

    if [ "${rule_db}" != "${target_db}" ]; then
      continue
    fi

    local collection_rules=()
    IFS=','
    read -r -a collection_rules <<< "${collections_csv}"
    IFS="${old_ifs}"

    local allowed_collection
    for allowed_collection in "${collection_rules[@]}"; do
      if [ "${allowed_collection}" = "*" ] || [ "${allowed_collection}" = "${collection_name}" ]; then
        return 0
      fi
    done
  done

  return 1
}

import_json_file() {
  local target_db="$1"
  local jsonfile="$2"
  local collection_name
  local target_collection
  collection_name="$(basename "${jsonfile}" .json)"
  target_collection="$(target_collection_name "${collection_name}")"

  if ! collection_is_seed_managed "${target_db}" "${collection_name}"; then
    echo "Skipping runtime-managed collection ${target_db}.${collection_name}; seed import is disabled for this collection."
    return 0
  fi

  # All seed files are JSON arrays of documents. If the file contains no object
  # start at all, it is just an empty array like "[]", which mongoimport rejects.
  if ! json_file_has_documents "${jsonfile}"; then
    if collection_should_replace_on_import "${collection_name}" && database_has_wildcard_seed_rule "${target_db}"; then
      clear_collection "${target_db}" "${target_collection}"
      ensure_collection_indexes "${target_db}" "${target_collection}"
      echo "Cleared ${target_db}.${target_collection} because the seed file ${jsonfile} is empty."
      return 0
    fi

    echo "Skipping empty JSON array seed ${jsonfile}"
    return 0
  fi

  local handler="${seed_import_handlers[${collection_name}]:-}"
  if [ -n "${handler}" ]; then
    "${handler}" "${target_db}" "${jsonfile}"
    return 0
  fi

  if collection_should_replace_on_import "${collection_name}"; then
    clear_collection "${target_db}" "${target_collection}"
  fi

  ensure_collection_indexes_before_import "${target_db}" "${target_collection}"

  mongoimport \
    -h "${mongo_host}" \
    --port "${mongo_port}" \
    -d "${target_db}" \
    -c "${target_collection}" \
    -u "${mongo_username}" \
    -p "${mongo_password}" \
    --mode=upsert \
    --upsertFields _id \
    --file "$jsonfile" \
    --jsonArray \
    --type json

  ensure_collection_versions "${target_db}" "${target_collection}"
  ensure_collection_indexes "${target_db}" "${target_collection}"
}

import_seed_directory() {
  local target_db="$1"
  local target_seed_dir_name="$2"
  local seed_dir="${seed_root}/${target_seed_dir_name}"

  if [ ! -d "${seed_dir}" ]; then
    echo "Seed directory ${seed_dir} does not exist, skipping mongoimport."
    ensure_runtime_indexes "${target_db}"
    return 0
  fi

  find "${seed_dir}" -maxdepth 1 \( -type f -o -type l \) -name '*.json' | sort | while read -r jsonfile; do
    import_json_file "${target_db}" "${jsonfile}"
  done
  ensure_runtime_indexes "${target_db}"
}

import_single_file() {
  local target_db="$1"
  local jsonfile="$2"

  if [ ! -f "${jsonfile}" ]; then
    echo "Seed file ${jsonfile} does not exist, skipping mongoimport."
    return 0
  fi

  import_json_file "${target_db}" "${jsonfile}"
}

if [ -n "${single_file}" ]; then
  import_single_file "${db_name}" "${single_file}"
elif [ "${MONGO_IMPORT_ALL_SEED_DATABASES:-false}" = "true" ]; then
  import_seed_directory "demo_db" "demo_db"
  if [ "${db_name}" != "demo_db" ]; then
    import_seed_directory "${db_name}" "${seed_dir_name}"
  fi
else
  import_seed_directory "${db_name}" "${seed_dir_name}"
fi
# The official Mongo entrypoint sources this file. Return control to it so it
# can stop the temporary daemon and exec the final mongod; never exit its shell.
