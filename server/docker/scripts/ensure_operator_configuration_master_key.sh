#!/bin/sh
set -eu

umask 077

key_path="${OPERATOR_CONFIGURATION_MASTER_KEY_PATH:-/workspace/data/configuration/deployment-master-key}"
key_dir="${key_path%/*}"
temporary_key=""

fail() {
  echo "Operator configuration master key: $*" >&2
  exit 1
}

cleanup() {
  if [ -n "${temporary_key}" ]; then
    rm -f -- "${temporary_key}"
  fi
}

validate_key_file() {
  if [ -L "${key_path}" ]; then
    fail "${key_path} must not be a symbolic link"
  fi
  if [ ! -f "${key_path}" ]; then
    fail "${key_path} exists but is not a regular file"
  fi
  chmod 0600 "${key_path}"
}

case "${key_path}" in
  /*) ;;
  *) fail "path must be absolute: ${key_path}" ;;
esac

if [ -L "${key_dir}" ]; then
  fail "${key_dir} must not be a symbolic link"
fi
if [ -e "${key_dir}" ] && [ ! -d "${key_dir}" ]; then
  fail "${key_dir} exists but is not a directory"
fi

mkdir -p "${key_dir}"

if [ -L "${key_dir}" ] || [ ! -d "${key_dir}" ]; then
  fail "${key_dir} must be a regular directory"
fi
chmod 0700 "${key_dir}"

if [ -e "${key_path}" ] || [ -L "${key_path}" ]; then
  validate_key_file
  exit 0
fi

trap cleanup 0 1 2 15
temporary_key="$(mktemp "${key_path}.tmp.XXXXXX")"
dd if=/dev/urandom of="${temporary_key}" bs=32 count=1 status=none

key_size="$(wc -c < "${temporary_key}")"
if [ "${key_size}" -ne 32 ]; then
  fail "failed to generate exactly 32 random bytes"
fi
chmod 0600 "${temporary_key}"

if ! ln "${temporary_key}" "${key_path}" 2>/dev/null; then
  validate_key_file
fi

cleanup
temporary_key=""
trap - 0 1 2 15
