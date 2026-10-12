#!/bin/sh
set -eu

seed_root="${MINIO_SEED_ROOT:-/seed/media}"
endpoint="${MINIO_ENDPOINT:-http://127.0.0.1:9000}"
bucket="${MEDIA_BUCKET:-myscoutee-media}"
alias_name="${MINIO_SEED_ALIAS:-myscoutee}"
access_key="${MINIO_ROOT_USER:-minioadmin}"
secret_key="${MINIO_ROOT_PASSWORD:-minioadmin}"
wait_attempts="${MINIO_SEED_WAIT_ATTEMPTS:-120}"
wait_attempt=1

until mc alias set "${alias_name}" "${endpoint}" "${access_key}" "${secret_key}" >/dev/null 2>&1; do
  if [ "${wait_attempts}" != "0" ] && [ "${wait_attempt}" -ge "${wait_attempts}" ]; then
    echo "MinIO did not become reachable at ${endpoint} after ${wait_attempts} attempts." >&2
    exit 1
  fi
  echo "Waiting for MinIO at ${endpoint}..."
  wait_attempt=$((wait_attempt + 1))
  sleep 2
done

mc mb --ignore-existing "${alias_name}/${bucket}" >/dev/null

if [ ! -d "${seed_root}" ]; then
  echo "No MinIO seed directory found at ${seed_root}."
  exit 0
fi

# One client process streams the packaged tree with bounded concurrency. Copy
# only source-owned keys: never mirror/remove a bucket containing user uploads.
# mc detects image Content-Type from the source extension.
mc cp --recursive --max-workers 4 --quiet "${seed_root%/}/" "${alias_name}/${bucket}/" >/dev/null
echo "Seeded packaged MinIO media."
