#!/usr/bin/env bash
# Fixture transport only; no remote defaults and no production DB access.
set -euo pipefail
[[ "${1:-}" == demo_db ]] || { echo 'SSH fixtures permit demo_db only.' >&2; exit 2; }
config="${E2E_MONGO_SSH_CONFIG:?Select a private release-client SSH configuration.}"
container="${E2E_MONGO_CONTAINER:?Select the installed fixture Mongo container.}"
[[ "$container" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]] || exit 2
[[ -f "$config" && ! -L "$config" && "$(stat -c %u "$config")" == "$(id -u)" ]] || exit 2
case "$(stat -c %a "$config")" in 400|600) ;; *) echo 'SSH configuration must have mode 0400 or 0600.' >&2; exit 2;; esac
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$config"
source "$root/packaging/client-tools/remote-transport.sh"
remote_transport_setup "$config"
trap remote_transport_cleanup EXIT
command="$(remote_transport_quote_command docker exec -i "$container" sh -c \
  'exec mongosh --quiet --username "$MONGO_APP_USERNAME" --password "$MONGO_APP_PASSWORD" --authenticationDatabase "$1" "$1" --file /dev/stdin' fixture demo_db)"
if [[ "$REMOTE_USER" == root ]]; then
  remote_transport_ssh_raw "exec $command"
elif [[ -n "$REMOTE_SUDO_PASSWORD" ]]; then
  { printf '%s\n' "$REMOTE_SUDO_PASSWORD"; cat; } \
    | remote_transport_ssh_raw "exec sudo -S -p '' -- $command"
else
  remote_transport_ssh_raw "exec sudo -n -- $command"
fi
