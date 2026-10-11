#!/usr/bin/env bash
# =====================================================================
# run-service.sh — start a business service via `mvn spring-boot:run`
# with JVM remote debug enabled on a per-service port.
#
# Usage:
#   ./scripts/run-service.sh <service> [profile] [--suspend]
#
# Examples:
#   ./scripts/run-service.sh user                     # debug on :5005, dev profile, no suspend
#   ./scripts/run-service.sh order dev                # same but explicit profile
#   ./scripts/run-service.sh payment dev,canary       # multiple profiles
#   ./scripts/run-service.sh user dev --suspend       # pause JVM until debugger attaches
#
# Debug ports (so you can run all 5 at once without conflict):
#   user         → 5005
#   product      → 5006
#   order        → 5007
#   payment      → 5008
#   notification → 5009
#   graphql-bff  → 5010
#   order-query  → 5011
#   product-query→ 5012
#   shop-ui      → 5013
#   backoffice-ui→ 5014
#
# Attach from IntelliJ:
#   Run → Edit Configurations → + → Remote JVM Debug
#   Host: localhost  |  Port: <above>  |  then click Debug
# =====================================================================

set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# ─── Pretty output (colored if TTY) ───────────────────────────────────
if [ -t 1 ]; then
  C_RESET=$'\033[0m'; C_BOLD=$'\033[1m'; C_DIM=$'\033[2m'
  C_GREEN=$'\033[32m'; C_YELLOW=$'\033[33m'; C_RED=$'\033[31m'; C_CYAN=$'\033[36m'
else
  C_RESET="";C_BOLD="";C_DIM="";C_GREEN="";C_YELLOW="";C_RED="";C_CYAN=""
fi
info() { printf "${C_DIM}%s${C_RESET}\n" "$1"; }
note() { printf "${C_BOLD}${C_CYAN}▶${C_RESET} %s\n" "$1"; }
err()  { printf "${C_RED}ERROR:${C_RESET} %s\n" "$1" >&2; }

# ─── Service → (module path, port, debug port) table ─────────────────
# Format: service_key:module_dir:service_port:debug_port
SERVICES=(
  "user:services/user-service:8081:5005"
  "product:services/product-service:8082:5006"
  "order:services/order-service:8083:5007"
  "payment:services/payment-service:8091:5008"
  "notification:services/notification:8099:5009"
  "graphql-bff:bff/graphql-bff:8087:5010"
  "order-query:services/order-query:8086:5011"
  "product-query:services/product-query:8088:5012"
  "shop-ui:ui/shop-ui:8089:5013"
  "backoffice-ui:ui/backoffice-ui:8090:5014"
)

lookup() {
  local target="$1"
  for row in "${SERVICES[@]}"; do
    [ "${row%%:*}" = "$target" ] && echo "$row" && return 0
  done
  return 1
}

usage() {
  echo "Usage: $0 <service> [profile] [--suspend]"
  echo
  echo "Known services:"
  for row in "${SERVICES[@]}"; do
    IFS=':' read -r name _ port dbg <<< "$row"
    printf "  %-14s port :%-5s debug :%s\n" "$name" "$port" "$dbg"
  done
  echo
  echo "Default profile:  dev"
  echo "Default suspend:  n  (JVM starts immediately, waits for debugger in background)"
}

# ─── Parse args ───────────────────────────────────────────────────────
if [ $# -eq 0 ] || [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  usage; exit 0
fi

SERVICE="$1"; shift
PROFILE="dev"
SUSPEND="n"

for arg in "$@"; do
  case "$arg" in
    --suspend) SUSPEND="y" ;;
    *)         PROFILE="$arg" ;;
  esac
done

# ─── Resolve service ──────────────────────────────────────────────────
row=$(lookup "$SERVICE") || { err "unknown service: $SERVICE"; echo; usage; exit 1; }
IFS=':' read -r name module port dbg_port <<< "$row"

# ─── Pin Java 17 ──────────────────────────────────────────────────────
JAVA_HOME_17=$(/usr/libexec/java_home -v 17 2>/dev/null)
if [ -z "$JAVA_HOME_17" ] || [ ! -d "$JAVA_HOME_17" ]; then
  err "Java 17 not found via /usr/libexec/java_home -v 17"
  info "Install with:  brew install openjdk@17"
  exit 1
fi
export JAVA_HOME="$JAVA_HOME_17"
export PATH="$JAVA_HOME/bin:$PATH"

# ─── Preflight: debug port free? ──────────────────────────────────────
if lsof -i :$dbg_port >/dev/null 2>&1; then
  err "debug port :$dbg_port is already in use"
  info "Something is listening on it. Stop that or pick another port:"
  info "   lsof -i :$dbg_port"
  exit 1
fi

if lsof -i :$port >/dev/null 2>&1; then
  err "service port :$port is already in use"
  info "Probably: another instance is running (make $name OR docker container)"
  info "Fix:   lsof -ti :$port | xargs kill"
  exit 1
fi

# ─── Build JVM debug flag ─────────────────────────────────────────────
JDWP="-agentlib:jdwp=transport=dt_socket,server=y,suspend=$SUSPEND,address=*:$dbg_port"

# ─── Launch ───────────────────────────────────────────────────────────
note "Starting $name"
info "  module:     $module"
info "  service:    http://localhost:$port"
info "  debugger:   localhost:$dbg_port   (suspend=$SUSPEND)"
info "  profile:    $PROFILE"
info "  java:       $(java -version 2>&1 | head -1 | awk -F'\"' '{print $2}')"
echo
if [ "$SUSPEND" = "y" ]; then
  printf "${C_YELLOW}NOTE:${C_RESET} suspend=y — JVM will pause at startup until you attach a debugger.\n"
  printf "       Attach from IntelliJ to ${C_BOLD}localhost:$dbg_port${C_RESET} to continue.\n\n"
fi
info "Press Ctrl+C to stop the service."
echo

exec mvn -pl "$module" -am spring-boot:run \
    -Dspring-boot.run.profiles="$PROFILE" \
    -Dspring-boot.run.jvmArguments="$JDWP"
