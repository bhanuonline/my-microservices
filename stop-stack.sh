#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
#  stop-stack.sh — tear down what start-stack.sh brought up
#
#  Flags:
#    --apps-only       stop only the Spring Boot JVMs, leave infra running
#    --infra-only      only the docker containers
#    --purge           also docker compose down -v (drops volumes = data loss)
#
#    <name>[,<name>…]  stop ONLY the named apps (keep other apps + infra up)
#                      e.g.  ./stop-stack.sh user-service
#                            ./stop-stack.sh user-service,product-service
# ─────────────────────────────────────────────────────────────────────────────

set -uo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

if [ -t 1 ]; then
  C_RESET=$'\033[0m'; C_GREEN=$'\033[32m'; C_YELLOW=$'\033[33m'; C_RED=$'\033[31m'; C_DIM=$'\033[2m'
else
  C_RESET=""; C_GREEN=""; C_YELLOW=""; C_RED=""; C_DIM=""
fi

LOG_DIR="$ROOT/logs"
APPS_ONLY=false
INFRA_ONLY=false
PURGE=false
SPECIFIC=""

for arg in "$@"; do
  case "$arg" in
    --apps-only)  APPS_ONLY=true ;;
    --infra-only) INFRA_ONLY=true ;;
    --purge)      PURGE=true ;;
    -h|--help)
      sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    --*) echo "unknown flag: $arg" ; exit 1 ;;
    *)
      # Positional = specific app name(s).
      if [ -z "$SPECIFIC" ]; then SPECIFIC="$arg"
      else                        SPECIFIC="$SPECIFIC,$arg"
      fi
      ;;
  esac
done

stop_pidfile() {
  local pidfile="$1"
  local name
  name=$(basename "$pidfile" .pid)
  if [ ! -f "$pidfile" ]; then
    printf "  ${C_DIM}·${C_RESET} %-20s no pid file\n" "$name"
    return
  fi
  local pid
  pid=$(cat "$pidfile")
  if kill -0 "$pid" 2>/dev/null; then
    kill "$pid" 2>/dev/null || true
    # Grace 5s then SIGKILL.
    for _ in 1 2 3 4 5; do
      kill -0 "$pid" 2>/dev/null || break
      sleep 1
    done
    kill -9 "$pid" 2>/dev/null || true
    printf "  ${C_GREEN}✓${C_RESET} %-20s stopped (pid %s)\n" "$name" "$pid"
  else
    printf "  ${C_YELLOW}!${C_RESET} %-20s not running (stale pid)\n" "$name"
  fi
  rm -f "$pidfile"
}

# ─── Specific apps mode ──────────────────────────────────────────────────────
if [ -n "$SPECIFIC" ]; then
  echo "─── stopping specific apps ───"
  for name in ${SPECIFIC//,/ }; do
    stop_pidfile "$LOG_DIR/${name}.pid"
  done
  echo "done. Infra and other apps untouched."
  exit 0
fi

# ─── stop apps ───────────────────────────────────────────────────────────────
if ! $INFRA_ONLY; then
  echo "─── stopping apps ───"
  if [ -d "$LOG_DIR" ]; then
    found=false
    for pidfile in "$LOG_DIR"/*.pid; do
      [ -f "$pidfile" ] || continue
      found=true
      stop_pidfile "$pidfile"
    done
    $found || echo "  (no pid files — nothing to stop)"
  fi
fi

$APPS_ONLY && exit 0

# ─── stop infra ──────────────────────────────────────────────────────────────
echo "─── stopping infra ───"
if $PURGE; then
  echo "  (${C_RED}PURGE${C_RESET}: dropping volumes — data lost)"
  docker compose -f docker-compose.yml -f docker-compose.topology-individual.yml down -v
else
  docker compose -f docker-compose.yml -f docker-compose.topology-individual.yml down
fi

echo "done."
