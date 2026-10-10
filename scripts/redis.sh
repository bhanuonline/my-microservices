#!/usr/bin/env bash
# =====================================================================
# redis.sh — drop into the Redis shell inside the redis container
#
# Redis has no password in this project (dev-only), so no secrets needed.
#
# Usage:
#   ./scripts/redis.sh                 # redis-cli interactive prompt
#   ./scripts/redis.sh KEYS '*'        # one-shot command
#   ./scripts/redis.sh INFO memory
#   ./scripts/redis.sh MONITOR         # tail all commands (Ctrl+C to stop)
# =====================================================================

set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# ─── Preflight ────────────────────────────────────────────────────────
if ! docker inspect -f '{{.State.Running}}' redis 2>/dev/null | grep -q true; then
  echo "ERROR: redis container isn't running."
  echo "       Start it with:  make up-nano  (or any larger mode)"
  exit 1
fi

# ─── Build the redis-cli command ──────────────────────────────────────
if [ $# -gt 0 ]; then
  # Args passed → one-shot command
  exec docker exec -it redis redis-cli "$@"
else
  # No args → interactive prompt
  exec docker exec -it redis redis-cli
fi
