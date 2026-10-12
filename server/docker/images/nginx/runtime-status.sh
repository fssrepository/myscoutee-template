#!/usr/bin/env sh
# Backend leases replace the gateway's former health polling loop.
set -eu
exec /scripts/runtime-status-receiver
