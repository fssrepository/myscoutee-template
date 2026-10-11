#!/usr/bin/env bash
set -euo pipefail

bootrun_pid=""
continuous_pid=""

cleanup() {
  trap - EXIT INT TERM

  if [[ -n "${continuous_pid}" ]] && kill -0 "${continuous_pid}" >/dev/null 2>&1; then
    kill "${continuous_pid}" >/dev/null 2>&1 || true
  fi
  if [[ -n "${bootrun_pid}" ]] && kill -0 "${bootrun_pid}" >/dev/null 2>&1; then
    kill "${bootrun_pid}" >/dev/null 2>&1 || true
  fi

  if [[ -n "${continuous_pid}" ]]; then
    wait "${continuous_pid}" 2>/dev/null || true
  fi
  if [[ -n "${bootrun_pid}" ]]; then
    wait "${bootrun_pid}" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

./gradlew :projects:profile:bootRun &
bootrun_pid=$!

if [[ "${PROFILEMANAGEMENT_HOT_RELOAD:-true}" != "true" ]]; then
  wait "${bootrun_pid}"
  exit $?
fi

echo "Waiting for profilemanagement:8080 before starting the continuous compiler..."
until bash -c ">/dev/tcp/127.0.0.1/8080" >/dev/null 2>&1; do
  if ! kill -0 "${bootrun_pid}" >/dev/null 2>&1; then
    wait "${bootrun_pid}"
    exit $?
  fi
  sleep 1
done

echo "Profilemanagement is ready; starting the continuous compiler..."
./gradlew :projects:profile:classes --continuous &
continuous_pid=$!

set +e
wait -n "${bootrun_pid}" "${continuous_pid}"
child_status=$?
set -e
exit "${child_status}"
