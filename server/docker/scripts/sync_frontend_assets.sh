#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../../.." && pwd)"

DEFAULT_FRONTEND_ROOT="${REPO_ROOT}/frontend"
DEFAULT_NGINX_HTML_DIR="${REPO_ROOT}/server/docker/conf/nginx/app/myscoutee"

MODE="${1:-dev}"
WATCH_MODE="${2:-}"

if [[ "${MODE}" == "watch" ]]; then
  MODE="${FRONTEND_BUILD_MODE:-dev}"
  WATCH_MODE="watch"
fi

if [[ "${MODE}" != "dev" && "${MODE}" != "prod" ]]; then
  echo "Usage: $0 [dev|prod] [watch]" >&2
  echo "Example: $0 dev watch" >&2
  echo "Env overrides: FRONTEND_ROOT, FRONTEND_SOURCE_DIR, NGINX_HTML_DIR" >&2
  exit 1
fi

FRONTEND_ROOT="${FRONTEND_ROOT:-${DEFAULT_FRONTEND_ROOT}}"
if [[ "${MODE}" == "dev" ]]; then
  DEFAULT_SOURCE_DIR="${FRONTEND_ROOT}/dev"
else
  DEFAULT_SOURCE_DIR="${FRONTEND_ROOT}/prod"
fi

SOURCE_DIR="${FRONTEND_SOURCE_DIR:-${DEFAULT_SOURCE_DIR}}"
TARGET_DIR="${NGINX_HTML_DIR:-${DEFAULT_NGINX_HTML_DIR}}"
SKIP_INITIAL_SYNC="${SKIP_INITIAL_SYNC:-false}"

if ! command -v rsync >/dev/null 2>&1; then
  echo "Error: rsync is required." >&2
  exit 1
fi

mkdir -p "${TARGET_DIR}"

SYNC_DEBOUNCE_SECONDS="${SYNC_DEBOUNCE_SECONDS:-2}"

wait_for_source_dir() {
  if [[ -d "${SOURCE_DIR}" ]]; then
    return 0
  fi

  if [[ "${WATCH_MODE}" != "watch" ]]; then
    echo "Error: source directory not found: ${SOURCE_DIR}" >&2
    exit 1
  fi

  until [[ -d "${SOURCE_DIR}" ]]; do
    echo "Waiting for frontend source directory: ${SOURCE_DIR}" >&2
    sleep 1
  done
}

wait_for_quiet_period() {
  if [[ "${WATCH_MODE}" != "watch" ]]; then
    return 0
  fi
  if ! command -v inotifywait >/dev/null 2>&1; then
    return 0
  fi

  while inotifywait -q -r -t "${SYNC_DEBOUNCE_SECONDS}" -e create,delete,modify,move "${SOURCE_DIR}" >/dev/null 2>&1; do
    :
  done
}

sync_once() {
  local rsync_status=0
  local rsync_log
  local attempt=1
  local max_attempts=5
  local -a delete_args=()
  rsync_log="$(mktemp)"

  wait_for_source_dir

  if [[ "${MODE}" == "prod" ]]; then
    delete_args=(--delete)
  fi

  while true; do
    : > "${rsync_log}"
    if rsync -a "${delete_args[@]}" "${SOURCE_DIR}/" "${TARGET_DIR}/" 2>"${rsync_log}"; then
      # Development uses the stack release from Compose; production keeps the
      # metadata stamped into its signed frontend artifact.
      if [[ "${MODE}" == "dev" && -n "${MYSCOUTEE_VERSION:-}" ]]; then
        node - "${TARGET_DIR}/app-version.json" <<'NODE'
const fs = require('node:fs');
fs.writeFileSync(process.argv[2], JSON.stringify({ version: process.env.MYSCOUTEE_VERSION }) + '\n');
NODE
      fi
      : > "${TARGET_DIR}/.frontend-ready"
      rm -f "${rsync_log}"
      echo "Synced ${SOURCE_DIR} -> ${TARGET_DIR} at $(date '+%Y-%m-%d %H:%M:%S')"
      return 0
    else
      rsync_status=$?
    fi

    if [[ "${rsync_status}" -ne 24 || "${attempt}" -ge "${max_attempts}" ]]; then
      cat "${rsync_log}" >&2 || true
      rm -f "${rsync_log}"
      return "${rsync_status}"
    fi

    attempt=$((attempt + 1))
    sleep 1
  done
}

wait_for_source_dir

if [[ "${SKIP_INITIAL_SYNC}" != "true" ]]; then
  wait_for_quiet_period
  sync_once
fi

if [[ "${WATCH_MODE}" == "watch" ]]; then
  if ! command -v inotifywait >/dev/null 2>&1; then
    echo "Error: watch mode needs inotifywait (inotify-tools)." >&2
    exit 1
  fi

  echo "Watching ${SOURCE_DIR} ..."
  while true; do
    wait_for_source_dir
    if ! inotifywait -r -e create,delete,modify,move "${SOURCE_DIR}" >/dev/null 2>&1; then
      sleep 1
      continue
    fi
    wait_for_quiet_period
    sync_once
  done
fi
