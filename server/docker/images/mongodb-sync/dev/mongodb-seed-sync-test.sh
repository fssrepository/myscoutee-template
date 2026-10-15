#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TEST_ROOT="$(mktemp -d /tmp/mongodb-seed-sync-test.XXXXXX)"
WATCHER_PID=""

cleanup() {
  local status=$?
  if [[ -n "${WATCHER_PID}" ]]; then
    kill "${WATCHER_PID}" >/dev/null 2>&1 || true
    wait "${WATCHER_PID}" >/dev/null 2>&1 || true
  fi
  if (( status != 0 )); then
    [[ ! -f "${TEST_ROOT}/watcher.log" ]] || {
      printf '%s\n' '--- watcher log ---' >&2
      cat "${TEST_ROOT}/watcher.log" >&2
    }
    [[ ! -f "${TEST_IMPORT_LOG:-}" ]] || {
      printf '%s\n' '--- import log ---' >&2
      cat "${TEST_IMPORT_LOG}" >&2
    }
  fi
  rm -rf "${TEST_ROOT}"
  return "${status}"
}
trap cleanup EXIT

mkdir -p "${TEST_ROOT}/seed/demo_db" "${TEST_ROOT}/bin"
printf '[{"version":"v1"}]\n' > "${TEST_ROOT}/seed/demo_db/chats.json"
printf '[{"version":"v1"}]\n' > "${TEST_ROOT}/seed/demo_db/i18n_messages.json"
printf '[{"version":"v1"}]\n' > "${TEST_ROOT}/seed/demo_db/i18n_bundles.json"
printf '[{"version":"v1"}]\n' > "${TEST_ROOT}/seed/demo_db/unchanged seed.json"
printf '[{"version":"v1"}]\n' > "${TEST_ROOT}/seed/demo_db/"$'newline\nseed.json'

cat > "${TEST_ROOT}/bin/mongosh" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF

cat > "${TEST_ROOT}/fake-import.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [[ -z "${MONGO_IMPORT_SINGLE_FILE:-}" ]]; then
  [[ ! -e "${MONGODB_SEED_SYNC_READY_FILE}" ]]
  printf 'initial\n' >> "${TEST_IMPORT_LOG}"
  if [[ "${TEST_INITIAL_CHANGE:-false}" == true ]]; then
    printf '[{"version":"v2"}]\n' > "${TEST_ROOT}/seed/demo_db/chats.json"
    printf '[{"version":"v1"}]\n' > "${TEST_ROOT}/seed/demo_db/chats.json"
  fi
  return 0 2>/dev/null || exit 0
fi
version="$(sed -n 's/.*"version":"\([^"]*\)".*/\1/p' "${MONGO_IMPORT_SINGLE_FILE}")"
printf 'file:%s:%s\n' "$(basename "${MONGO_IMPORT_SINGLE_FILE}")" "${version}" >> "${TEST_IMPORT_LOG}"
printf 'start:%s\n' "${version}" >> "${TEST_IMPORT_LOG}"
touch "${TEST_ROOT}/single-import-started-${version}"
if [[ "${TEST_FAIL_ONCE:-false}" == true && ! -f "${TEST_ROOT}/failed-once" ]]; then
  touch "${TEST_ROOT}/failed-once"
  if [[ "${TEST_REVERT_ON_FAILURE:-false}" == true ]]; then
    printf '[{"version":"v1"}]\n' > "${MONGO_IMPORT_SINGLE_FILE}"
  fi
  exit 1
fi
sleep "${TEST_IMPORT_DELAY:-1}"
printf 'finish:%s\n' "${version}" >> "${TEST_IMPORT_LOG}"
EOF

export TEST_REAL_SHA256SUM="$(command -v sha256sum)"
export TEST_REAL_STAT="$(command -v stat)"
cat > "${TEST_ROOT}/bin/sha256sum" <<'EOF'
#!/usr/bin/env bash
printf 'hash\n' >> "${TEST_ROOT}/hashes.log"
exec "${TEST_REAL_SHA256SUM}" "$@"
EOF
cat > "${TEST_ROOT}/bin/stat" <<'EOF'
#!/usr/bin/env bash
printf 'stat\n' >> "${TEST_ROOT}/stats.log"
exec "${TEST_REAL_STAT}" "$@"
EOF
chmod 0755 "${TEST_ROOT}/bin/"* "${TEST_ROOT}/fake-import.sh"
export PATH="${TEST_ROOT}/bin:${PATH}"
export TEST_ROOT
export TEST_IMPORT_LOG="${TEST_ROOT}/imports.log"
export MONGODB_SEED_SYNC_READY_FILE="${TEST_ROOT}/ready"
# A retained marker must not release the backend during a new startup import.
touch "${MONGODB_SEED_SYNC_READY_FILE}"

MONGODB_SEED_ROOT="${TEST_ROOT}/seed" \
MONGO_IMPORT_SCRIPT="${TEST_ROOT}/fake-import.sh" \
MONGODB_SEED_SYNC_DEBOUNCE_SECONDS=0.1 \
MONGODB_SEED_SYNC_RECONCILE_SECONDS=1 \
MONGODB_SEED_SYNC_MAX_RECONCILE_PASSES=1 \
  bash "${SCRIPT_DIR}/mongodb-seed-sync.sh" > "${TEST_ROOT}/watcher.log" 2>&1 &
WATCHER_PID="$!"

for _ in $(seq 1 50); do
  grep -q 'Initial seed import completed' "${TEST_ROOT}/watcher.log" && break
  sleep 0.1
done
grep -q 'Initial seed import completed' "${TEST_ROOT}/watcher.log"
[[ -f "${MONGODB_SEED_SYNC_READY_FILE}" ]]

printf '[{"version":"v2"}]\n' > "${TEST_ROOT}/seed/demo_db/i18n_messages.json"
printf '[{"version":"v2"}]\n' > "${TEST_ROOT}/seed/demo_db/i18n_bundles.json"
printf '[{"version":"v2"}]\n' > "${TEST_ROOT}/seed/demo_db/chats.json"
# An identical save changes metadata but must not reset the collection.
printf '[{"version":"v1"}]\n' > "${TEST_ROOT}/seed/demo_db/unchanged seed.json"
for _ in $(seq 1 50); do
  [[ -f "${TEST_ROOT}/single-import-started-v2" ]] && break
  sleep 0.1
done
[[ -f "${TEST_ROOT}/single-import-started-v2" ]]

# This write occurs while v2 is being imported. The old one-shot inotify
# watcher lost precisely this class of event.
printf '[{"version":"v3"}]\n' > "${TEST_ROOT}/seed/demo_db/chats.json"

wait "${WATCHER_PID}"
WATCHER_PID=""

grep -Fxq 'finish:v2' "${TEST_IMPORT_LOG}"
grep -Fxq 'finish:v3' "${TEST_IMPORT_LOG}"
grep -Fq 'Seed changed during import; importing the newer version next' "${TEST_ROOT}/watcher.log"

messages_line="$(grep -n -F 'file:i18n_messages.json:v2' "${TEST_IMPORT_LOG}" | cut -d: -f1)"
bundles_line="$(grep -n -F 'file:i18n_bundles.json:v2' "${TEST_IMPORT_LOG}" | cut -d: -f1)"
[[ -n "${messages_line}" && -n "${bundles_line}" ]]
(( messages_line < bundles_line ))
if grep -Fq 'file:unchanged seed.json:' "${TEST_IMPORT_LOG}"; then
  printf 'Identical content was imported again.\n' >&2
  exit 1
fi

wait_for_initial_import() {
  for _ in $(seq 1 100); do
    grep -q 'Initial seed import completed' "${TEST_ROOT}/watcher.log" && return 0
    sleep 0.1
  done
  return 1
}

start_watcher() {
  : > "${TEST_IMPORT_LOG}"
  : > "${TEST_ROOT}/hashes.log"
  : > "${TEST_ROOT}/stats.log"
  : > "${TEST_ROOT}/watcher.log"
  rm -f "${TEST_ROOT}/failed-once"
  MONGODB_SEED_ROOT="${TEST_ROOT}/seed" \
  MONGO_IMPORT_SCRIPT="${TEST_ROOT}/fake-import.sh" \
  MONGODB_SEED_SYNC_DEBOUNCE_SECONDS=0.1 \
  MONGODB_SEED_SYNC_RECONCILE_SECONDS=1 \
  MONGODB_SEED_SYNC_MAX_RECONCILE_PASSES="$1" \
    bash "${SCRIPT_DIR}/mongodb-seed-sync.sh" > "${TEST_ROOT}/watcher.log" 2>&1 &
  WATCHER_PID="$!"
  wait_for_initial_import
}

finish_watcher() {
  wait "${WATCHER_PID}"
  WATCHER_PID=""
  [[ ! -e "${MONGODB_SEED_SYNC_READY_FILE}" ]]
}

# Idle passes must neither read seed content nor start a stat process per file.
start_watcher 3
initial_hashes="$(wc -l < "${TEST_ROOT}/hashes.log")"
initial_stats="$(wc -l < "${TEST_ROOT}/stats.log")"
finish_watcher
[[ "$(wc -l < "${TEST_ROOT}/hashes.log")" -eq "${initial_hashes}" ]]
[[ "$(wc -l < "${TEST_ROOT}/stats.log")" -eq $((initial_stats + 3)) ]]
[[ "$(cat "${TEST_IMPORT_LOG}")" == initial ]]

export TEST_IMPORT_DELAY=0
# Same size and restored mtime still differ by ctime; rename saves work too.
start_watcher 1
cp -p "${TEST_ROOT}/seed/demo_db/chats.json" "${TEST_ROOT}/original-time"
printf '[{"version":"v4"}]\n' > "${TEST_ROOT}/seed/demo_db/chats.json"
touch -r "${TEST_ROOT}/original-time" "${TEST_ROOT}/seed/demo_db/chats.json"
printf '[{"version":"v2"}]\n' > "${TEST_ROOT}/replacement"
mv "${TEST_ROOT}/replacement" "${TEST_ROOT}/seed/demo_db/unchanged seed.json"
finish_watcher
grep -Fxq 'file:chats.json:v4' "${TEST_IMPORT_LOG}"
grep -Fxq 'file:unchanged seed.json:v2' "${TEST_IMPORT_LOG}"

# An unchanged failed import must be retried, even if an earlier version was
# imported successfully. A partial failure followed by reverting the source to
# that earlier content must also repair Mongo rather than skip on the old hash.
export TEST_FAIL_ONCE=true
for revert in false true; do
  export TEST_REVERT_ON_FAILURE="${revert}"
  printf '[{"version":"v1"}]\n' > "${TEST_ROOT}/seed/demo_db/chats.json"
  start_watcher 2
  printf '[{"version":"v2"}]\n' > "${TEST_ROOT}/seed/demo_db/chats.json"
  finish_watcher
  [[ "$(grep -c '^file:chats.json:' "${TEST_IMPORT_LOG}")" -eq 2 ]]
  if [[ "${revert}" == true ]]; then
    grep -Fxq 'finish:v1' "${TEST_IMPORT_LOG}"
  else
    grep -Fxq 'finish:v2' "${TEST_IMPORT_LOG}"
  fi
done

unset TEST_FAIL_ONCE TEST_REVERT_ON_FAILURE
export TEST_INITIAL_CHANGE=true
printf '[{"version":"v1"}]\n' > "${TEST_ROOT}/seed/demo_db/chats.json"
start_watcher 1
finish_watcher
grep -Fxq 'file:chats.json:v1' "${TEST_IMPORT_LOG}"

printf 'mongodb seed sync tests passed\n'
