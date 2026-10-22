#!/usr/bin/env bash
set -euo pipefail

SOURCE_FRONTEND_ROOT="${FRONTEND_ROOT:-/workspace/frontend}"
SYNC_SCRIPT="${SYNC_SCRIPT:-/scripts/sync_frontend_assets.sh}"
BUILD_MODE="${FRONTEND_BUILD_MODE:-}"
BUILD_CONFIGURATION="${FRONTEND_BUILD_CONFIGURATION:-}"
BUILD_LOG_FILE="${BUILD_LOG_FILE:-}"
BUILD_FRONTEND_ROOT="${BUILD_FRONTEND_ROOT:-}"
SOURCE_SYNC_DEBOUNCE_SECONDS="${SOURCE_SYNC_DEBOUNCE_SECONDS:-1}"
SOURCE_SYNC_EXCLUDE_REGEX="(^${SOURCE_FRONTEND_ROOT}/(local|dev|e2e|docs|prod|out-tsc|\\.agent)(/|$))|(/(node_modules|dist|\\.angular|\\.git)(/|$))"
TEMP_BUILD_ROOT_CREATED="false"
BUILD_OUTPUT_DIR=""

if [[ ! -d "${SOURCE_FRONTEND_ROOT}" ]]; then
  echo "Error: frontend root not found: ${SOURCE_FRONTEND_ROOT}" >&2
  exit 1
fi

if [[ ! -d "${SOURCE_FRONTEND_ROOT}/node_modules" ]]; then
  echo "Error: frontend/node_modules is missing. Run npm install in the frontend workspace first." >&2
  exit 1
fi

resolve_build_configuration() {
  if [[ -n "${BUILD_CONFIGURATION}" ]]; then
    printf '%s\n' "${BUILD_CONFIGURATION}"
    return 0
  fi

  if [[ "${BUILD_MODE}" == "prod" ]]; then
    printf '%s\n' "production"
  else
    printf '%s\n' "dev"
  fi
}

resolve_build_mode() {
  if [[ -n "${BUILD_MODE}" ]]; then
    printf '%s\n' "${BUILD_MODE}"
    return 0
  fi

  case "${BUILD_CONFIGURATION}" in
    e2e|github|production)
      printf '%s\n' "prod"
      ;;
    *)
      printf '%s\n' "dev"
      ;;
  esac
}

validate_build_configuration() {
  case "${BUILD_CONFIGURATION}" in
    dev|e2e|github|production)
      ;;
    *)
      echo "Error: unsupported FRONTEND_BUILD_CONFIGURATION: ${BUILD_CONFIGURATION}" >&2
      exit 1
      ;;
  esac

  case "${BUILD_MODE}" in
    dev|prod)
      ;;
    *)
      echo "Error: FRONTEND_BUILD_MODE must be 'dev' or 'prod'." >&2
      exit 1
      ;;
  esac
}

resolve_build_output_dir() {
  case "${BUILD_CONFIGURATION}" in
    dev)
      printf '%s\n' "${BUILD_FRONTEND_ROOT}/dev"
      ;;
    e2e)
      printf '%s\n' "${BUILD_FRONTEND_ROOT}/e2e"
      ;;
    github)
      printf '%s\n' "${BUILD_FRONTEND_ROOT}/docs"
      ;;
    production)
      printf '%s\n' "${BUILD_FRONTEND_ROOT}/prod"
      ;;
    *)
      printf '%s\n' "${BUILD_FRONTEND_ROOT}/dev"
      ;;
  esac
}

initialize_build_workspace() {
  if [[ -z "${BUILD_FRONTEND_ROOT}" ]]; then
    BUILD_FRONTEND_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/myscoutee-frontend-sync.XXXXXX")"
    TEMP_BUILD_ROOT_CREATED="true"
  fi

  if [[ "${BUILD_FRONTEND_ROOT}" == "${SOURCE_FRONTEND_ROOT}" ]]; then
    echo "Error: BUILD_FRONTEND_ROOT must be different from FRONTEND_ROOT." >&2
    exit 1
  fi

  mkdir -p "${BUILD_FRONTEND_ROOT}"
  BUILD_OUTPUT_DIR="$(resolve_build_output_dir)"
}

sync_source_frontend_into_build_workspace() {
  rsync -a --delete \
    --exclude '/node_modules' \
    --exclude '/local' \
    --exclude '/dev' \
    --exclude '/e2e' \
    --exclude '/docs' \
    --exclude '/prod' \
    --exclude '/out-tsc' \
    --exclude '/.angular' \
    --exclude '/.git' \
    --exclude '/.agent' \
    "${SOURCE_FRONTEND_ROOT}/" "${BUILD_FRONTEND_ROOT}/"

  rm -rf "${BUILD_FRONTEND_ROOT}/node_modules"
  ln -s "${SOURCE_FRONTEND_ROOT}/node_modules" "${BUILD_FRONTEND_ROOT}/node_modules"
}

apply_build_workspace_overrides() {
  :
}

wait_for_host_port() {
  local host_port="$1"
  local host="${host_port%%:*}"
  local port="${host_port##*:}"

  if [[ -z "${host}" || -z "${port}" || "${host}" == "${port}" ]]; then
    echo "Skipping invalid WAIT_FOR_HOSTS entry: ${host_port}" >&2
    return 0
  fi

  until bash -c ">/dev/tcp/${host}/${port}" >/dev/null 2>&1; do
    echo "Waiting for ${host}:${port} ..."
    sleep 1
  done
}

wait_for_dependencies() {
  local wait_for_hosts="${WAIT_FOR_HOSTS:-}"
  if [[ -z "${wait_for_hosts}" ]]; then
    return 0
  fi

  for host_port in ${wait_for_hosts}; do
    wait_for_host_port "${host_port}"
  done
}

wait_for_initial_build_output() {
  local debounce_seconds="${SYNC_DEBOUNCE_SECONDS:-2}"

  while true; do
    if ! kill -0 "${build_pid}" >/dev/null 2>&1; then
      wait "${build_pid}"
    fi

    # Angular can announce completion before its output files reach disk.
    if grep -q 'Application bundle generation complete\.' "${BUILD_LOG_FILE}" \
      && grep -q 'Watch mode enabled\. Watching for file changes' "${BUILD_LOG_FILE}" \
      && [[ -f "${BUILD_OUTPUT_DIR}/index.html" ]]; then
      break
    fi

    sleep 1
  done

  if [[ -d "${BUILD_OUTPUT_DIR}" ]] && command -v inotifywait >/dev/null 2>&1; then
    while inotifywait -q -r -t "${debounce_seconds}" -e create,delete,modify,move "${BUILD_OUTPUT_DIR}" >/dev/null 2>&1; do
      :
    done
  fi
}

wait_for_source_quiet_period() {
  if ! command -v inotifywait >/dev/null 2>&1; then
    return 0
  fi

  while inotifywait -q -r -t "${SOURCE_SYNC_DEBOUNCE_SECONDS}" \
    -e create,delete,modify,move \
    --exclude "${SOURCE_SYNC_EXCLUDE_REGEX}" \
    "${SOURCE_FRONTEND_ROOT}" >/dev/null 2>&1; do
    :
  done
}

watch_frontend_source_changes() {
  if ! command -v inotifywait >/dev/null 2>&1; then
    echo "Source watch skipped: inotifywait is not available." >&2
    return 0
  fi

  echo "Watching frontend source at ${SOURCE_FRONTEND_ROOT} ..."
  while inotifywait -q -r -e create,delete,modify,move \
    --exclude "${SOURCE_SYNC_EXCLUDE_REGEX}" \
    "${SOURCE_FRONTEND_ROOT}" >/dev/null 2>&1; do
    wait_for_source_quiet_period
    echo "Syncing frontend source into temp build workspace..."
    sync_source_frontend_into_build_workspace
    apply_build_workspace_overrides
  done
}

list_child_pids() {
  local parent_pid="$1"
  local children_file="/proc/${parent_pid}/task/${parent_pid}/children"

  if [[ -r "${children_file}" ]]; then
    tr ' ' '\n' < "${children_file}" 2>/dev/null | sed '/^$/d' || true
    return 0
  fi

  if command -v pgrep >/dev/null 2>&1; then
    pgrep -P "${parent_pid}" 2>/dev/null || true
  fi
}

kill_process_tree() {
  local target_pid="$1"
  local child_pid

  if [[ -z "${target_pid}" ]] || ! kill -0 "${target_pid}" >/dev/null 2>&1; then
    return 0
  fi

  for child_pid in $(list_child_pids "${target_pid}"); do
    kill_process_tree "${child_pid}"
  done

  kill "${target_pid}" >/dev/null 2>&1 || true
}

cleanup() {
  if [[ -n "${build_pid:-}" ]]; then
    kill_process_tree "${build_pid}"
    wait "${build_pid}" 2>/dev/null || true
  fi
  if [[ -n "${sync_pid:-}" ]]; then
    kill_process_tree "${sync_pid}"
    wait "${sync_pid}" 2>/dev/null || true
  fi
  if [[ -n "${source_sync_pid:-}" ]]; then
    kill_process_tree "${source_sync_pid}"
    wait "${source_sync_pid}" 2>/dev/null || true
  fi
  if [[ -n "${BUILD_LOG_FILE:-}" && -f "${BUILD_LOG_FILE}" ]]; then
    rm -f "${BUILD_LOG_FILE}" || true
  fi
  if [[ "${TEMP_BUILD_ROOT_CREATED}" == "true" && -n "${BUILD_FRONTEND_ROOT}" && -d "${BUILD_FRONTEND_ROOT}" ]]; then
    rm -rf "${BUILD_FRONTEND_ROOT}" || true
  fi
}
trap cleanup EXIT INT TERM

BUILD_CONFIGURATION="$(resolve_build_configuration)"
BUILD_MODE="$(resolve_build_mode)"
validate_build_configuration

initialize_build_workspace
sync_source_frontend_into_build_workspace
apply_build_workspace_overrides

if [[ -z "${BUILD_LOG_FILE}" ]]; then
  BUILD_LOG_FILE="$(mktemp)"
else
  : > "${BUILD_LOG_FILE}"
fi

cd "${BUILD_FRONTEND_ROOT}"

echo "Starting Angular watch build in isolated workspace ${BUILD_FRONTEND_ROOT} using ${BUILD_CONFIGURATION}..."
WATCHPACK_POLLING="${WATCHPACK_POLLING:-true}" \
WATCHPACK_POLLING_INTERVAL="${WATCHPACK_POLLING_INTERVAL:-1000}" \
CHOKIDAR_USEPOLLING="${CHOKIDAR_USEPOLLING:-true}" \
CHOKIDAR_INTERVAL="${CHOKIDAR_INTERVAL:-1000}" \
NG_BUILD_MAX_WORKERS="${NG_BUILD_MAX_WORKERS:-12}" \
  node ./node_modules/@angular/cli/bin/ng.js build myscoutee --watch --configuration "${BUILD_CONFIGURATION}" \
  > >(tee -a "${BUILD_LOG_FILE}") 2>&1 &
build_pid=$!

watch_frontend_source_changes &
source_sync_pid=$!

echo "Waiting for the first fresh dev build output..."
wait_for_initial_build_output

echo "Waiting for dependent services before syncing nginx assets..."
wait_for_dependencies

echo "Syncing fresh build into nginx..."
FRONTEND_SOURCE_DIR="${BUILD_OUTPUT_DIR}" \
  NGINX_HTML_DIR="${NGINX_HTML_DIR:-/workspace/nginx-html}" \
  bash "${SYNC_SCRIPT}" "${BUILD_MODE}"

echo "Starting nginx asset sync watcher..."
(
  while true; do
    FRONTEND_SOURCE_DIR="${BUILD_OUTPUT_DIR}" \
      NGINX_HTML_DIR="${NGINX_HTML_DIR:-/workspace/nginx-html}" \
      bash "${SYNC_SCRIPT}" "${BUILD_MODE}" watch || true
    echo "nginx asset sync watcher restarting in 3s..."
    sleep 3
  done
) &
sync_pid=$!

wait -n "${build_pid}" "${source_sync_pid}"
