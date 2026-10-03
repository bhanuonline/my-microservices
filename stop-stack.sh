#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
#  stop-stack.sh — tear down what start-stack.sh brought up
#
#  Flags:
#    --apps-only       stop only the Spring Boot JVMs, leave infra running
#    --infra-only      only the docker containers
#    --purge           also docker compose down -v (drops volumes = data loss)
# ─────────────────────────────────────────────────────────────────────────────

set -uo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

LOG_DIR="$ROOT/logs"
APPS_ONLY=false
INFRA_ONLY=false
PURGE=false

for arg in "$@"; do
  case "$arg" in
    --apps-only)  APPS_ONLY=true ;;
    --infra-only) INFRA_ONLY=true ;;
    --purge)      PURGE=true ;;
    -h|--help)
      sed -n '2,10p' "$0" | sed 's/^# //'
      exit 0
      ;;
    *) echo "unknown flag: $arg" ; exit 1 ;;
  esac
done

# ─── stop apps ───────────────────────────────────────────────────────────────
if ! $INFRA_ONLY; then
  echo "─── stopping apps ───"
  if [ -d "$LOG_DIR" ]; then
    for pidfile in "$LOG_DIR"/*.pid; do
      [ -f "$pidfile" ] || continue
      name=$(basename "$pidfile" .pid)
      pid=$(cat "$pidfile")
      if kill -0 "$pid" 2>/dev/null; then
        kill "$pid" 2>/dev/null || true
        # Give it 5s to die gracefully, then SIGKILL.
        for _ in 1 2 3 4 5; do
          kill -0 "$pid" 2>/dev/null || break
          sleep 1
        done
        kill -9 "$pid" 2>/dev/null || true
        printf "  %-20s stopped (pid %s)\n" "$name" "$pid"
      else
        printf "  %-20s not running\n" "$name"
      fi
      rm -f "$pidfile"
    done
  fi
fi

$APPS_ONLY && exit 0

# ─── stop infra ──────────────────────────────────────────────────────────────
echo "─── stopping infra ───"
if $PURGE; then
  echo "  (PURGE: dropping volumes — data lost)"
  docker compose down -v
else
  docker compose down
fi

echo "done."
