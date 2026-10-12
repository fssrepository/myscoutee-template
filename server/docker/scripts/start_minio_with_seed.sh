#!/bin/sh
set -eu

seed_ready_file="${MINIO_SEED_READY_FILE:-/tmp/myscoutee-minio-seeded}"
minio_pid=""

stop_minio() {
  if [ -n "${minio_pid}" ] && kill -0 "${minio_pid}" 2>/dev/null; then
    kill -TERM "${minio_pid}" 2>/dev/null || true
    wait "${minio_pid}" 2>/dev/null || true
  fi
}

trap 'stop_minio; exit 143' INT TERM

rm -f "${seed_ready_file}"

/usr/bin/docker-entrypoint.sh "$@" &
minio_pid="$!"

if /bin/sh /scripts/bootstrap_minio_seed.sh; then
  touch "${seed_ready_file}"
else
  status="$?"
  echo "MinIO seed failed with exit code ${status}." >&2
  stop_minio
  exit "${status}"
fi

wait "${minio_pid}"
