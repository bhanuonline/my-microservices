#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
#  dev-start.sh  —  angle-app local developer start script
#
#  What it does (in order):
#    1. Checks Java 17 is available
#    2. Checks MySQL  (Docker container "mysql-shared" on :3306)
#    3. Checks Redis  (Docker container "angle-redis"  on :6379)
#    4. Frees port 9010 if something old is still there
#    5. Prints env-var status (Angel + Anthropic keys)
#    6. Starts the Spring Boot app
#
#  Dependencies used by angle-app:
#    REQUIRED  MySQL  :3306  — candle cache L3, all app data
#    REQUIRED  Redis  :6379  — candle cache L2 (bias.cache.provider=redis-mysql)
#    OPTIONAL  Angel One API — live data & broker stream (app still starts without it)
#    OPTIONAL  Anthropic API — AI signals & portfolio picks (degrades gracefully)
#
#  Usage:
#    ./dev-start.sh            # normal start
#    ./dev-start.sh --no-redis # skip Redis (switches cache to caffeine-mysql)
#    ./dev-start.sh --help
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail

# ── colours ───────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

ok()   { echo -e "  ${GREEN}✔${RESET}  $*"; }
warn() { echo -e "  ${YELLOW}⚠${RESET}  $*"; }
fail() { echo -e "  ${RED}✘${RESET}  $*"; }
info() { echo -e "  ${CYAN}→${RESET}  $*"; }
hdr()  { echo -e "\n${BOLD}$*${RESET}"; }

# ── flags ─────────────────────────────────────────────────────────────────────
NO_REDIS=false
for arg in "$@"; do
  case "$arg" in
    --no-redis) NO_REDIS=true ;;
    --help|-h)
      echo "Usage: $0 [--no-redis] [--help]"
      echo "  --no-redis   Start without Redis (uses caffeine-mysql cache instead)"
      exit 0 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"   # root docker-compose.yml lives here

echo ""
echo -e "${BOLD}╔══════════════════════════════════════════╗${RESET}"
echo -e "${BOLD}║        angle-app  dev-start              ║${RESET}"
echo -e "${BOLD}╚══════════════════════════════════════════╝${RESET}"

# ─────────────────────────────────────────────────────────────────────────────
# 1. Java 17
# ─────────────────────────────────────────────────────────────────────────────
hdr "1. Java"

JAVA17="/Library/Java/JavaVirtualMachines/jdk-17.0.2.jdk/Contents/Home"
if [ -d "$JAVA17" ]; then
  export JAVA_HOME="$JAVA17"
  ok "Java 17 found → $JAVA_HOME"
else
  # Fallback: look for any JDK 17 via /usr/libexec/java_home
  JAVA17_ALT=$(/usr/libexec/java_home -v 17 2>/dev/null || true)
  if [ -n "$JAVA17_ALT" ]; then
    export JAVA_HOME="$JAVA17_ALT"
    ok "Java 17 found (alt) → $JAVA_HOME"
  else
    fail "Java 17 not found. Install it: brew install --cask temurin@17"
    exit 1
  fi
fi

# ─────────────────────────────────────────────────────────────────────────────
# 2. MySQL  (:3306)
# ─────────────────────────────────────────────────────────────────────────────
hdr "2. MySQL :3306"

mysql_tcp_ok() {
  # Try a TCP ping without a unix socket
  mysql -u root -ppass1234 --protocol=TCP -P 3306 -e "SELECT 1;" \
    --connect-timeout=3 -s --skip-column-names 2>/dev/null | grep -q "1"
}

if mysql_tcp_ok; then
  ok "MySQL is already running"
else
  info "MySQL not responding — checking Docker..."
  if docker inspect mysql-shared &>/dev/null; then
    STATUS=$(docker inspect --format='{{.State.Status}}' mysql-shared)
    if [ "$STATUS" = "running" ]; then
      warn "Container 'mysql-shared' is running but not reachable yet — waiting..."
      for i in $(seq 1 15); do
        sleep 2
        mysql_tcp_ok && ok "MySQL ready after ${i}×2s" && break
        [ "$i" = "15" ] && fail "MySQL still not reachable after 30s. Check: docker logs mysql-shared" && exit 1
      done
    else
      info "Starting existing 'mysql-shared' container (status=$STATUS)..."
      docker start mysql-shared
      sleep 5
      mysql_tcp_ok && ok "MySQL started" || { fail "MySQL failed to start. Run: docker logs mysql-shared"; exit 1; }
    fi
  else
    info "Container 'mysql-shared' not found — starting from docker-compose..."
    (cd "$COMPOSE_DIR" && docker compose up -d mysql-shared)
    info "Waiting for MySQL to be healthy..."
    for i in $(seq 1 20); do
      sleep 3
      mysql_tcp_ok && ok "MySQL ready after ${i}×3s" && break
      [ "$i" = "20" ] && fail "MySQL did not become healthy in 60s." && exit 1
    done
  fi
fi

# ─────────────────────────────────────────────────────────────────────────────
# 3. Redis  (:6379)
# ─────────────────────────────────────────────────────────────────────────────
hdr "3. Redis :6379"

CACHE_PROVIDER_OVERRIDE=""

redis_ok() {
  redis-cli -h 127.0.0.1 -p 6379 ping 2>/dev/null | grep -q "PONG"
}

if $NO_REDIS; then
  warn "--no-redis flag set: skipping Redis, switching cache to caffeine-mysql"
  CACHE_PROVIDER_OVERRIDE="caffeine-mysql"
else
  if redis_ok; then
    ok "Redis is already running"
  else
    info "Redis not running — starting Docker container..."
    # Re-use root compose if possible, otherwise docker run
    if grep -q '"angle-redis"' <(docker ps -a --format '{{.Names}}') 2>/dev/null || \
       docker inspect angle-redis &>/dev/null 2>&1; then
      docker start angle-redis 2>/dev/null || true
    else
      docker run -d \
        --name angle-redis \
        -p 6379:6379 \
        --restart unless-stopped \
        redis:7-alpine \
        redis-server --appendonly yes --maxmemory 64mb --maxmemory-policy allkeys-lru
    fi
    info "Waiting for Redis..."
    for i in $(seq 1 10); do
      sleep 1
      redis_ok && ok "Redis ready" && break
      [ "$i" = "10" ] && {
        warn "Redis did not start. Falling back to caffeine-mysql cache."
        CACHE_PROVIDER_OVERRIDE="caffeine-mysql"
      }
    done
  fi
fi

# ─────────────────────────────────────────────────────────────────────────────
# 4. Free port 9010
# ─────────────────────────────────────────────────────────────────────────────
hdr "4. Port 9010"

OLD_PID=$(lsof -ti :9010 2>/dev/null || true)
if [ -n "$OLD_PID" ]; then
  warn "Port 9010 is in use by PID $OLD_PID — killing..."
  kill "$OLD_PID" 2>/dev/null || true
  sleep 2
  ok "Port 9010 freed"
else
  ok "Port 9010 is free"
fi

# ─────────────────────────────────────────────────────────────────────────────
# 5. Environment / API keys
# ─────────────────────────────────────────────────────────────────────────────
hdr "5. API Keys (optional — app starts without these, features degrade)"

check_env() {
  local var="$1" label="$2"
  if [ -n "${!var:-}" ]; then
    ok "$label → set (${#!var} chars)"
  else
    warn "$label → NOT SET  (export $var=...)"
  fi
}

check_env ANGEL_API_KEY       "Angel One API key     (live data / broker stream)"
check_env ANGEL_CLIENT_CODE   "Angel One client code (live data)"
check_env ANGEL_PASSWORD      "Angel One password    (live data)"
check_env ANGEL_TOTP_SECRET   "Angel One TOTP secret (live data)"
check_env ANTHROPIC_API_KEY   "Anthropic API key     (AI signals / portfolio)"

# ─────────────────────────────────────────────────────────────────────────────
# 6. Start the app
# ─────────────────────────────────────────────────────────────────────────────
hdr "6. Starting angle-app on :9010"

MVN_OPTS=""
if [ -n "$CACHE_PROVIDER_OVERRIDE" ]; then
  MVN_OPTS="-Dbias.cache.provider=$CACHE_PROVIDER_OVERRIDE"
  info "Cache override: bias.cache.provider=$CACHE_PROVIDER_OVERRIDE"
fi

echo ""
echo -e "${BOLD}  App URL : http://localhost:9010${RESET}"
echo -e "${BOLD}  Login   : admin / admin123${RESET}"
echo -e "${BOLD}  Journal : http://localhost:9010/journal${RESET}"
echo -e "${BOLD}  Screener: http://localhost:9010/screener${RESET}"
echo -e "${BOLD}  Admin   : http://localhost:9010/admin/instruments${RESET}"
echo ""
echo -e "  Press ${BOLD}Ctrl+C${RESET} to stop."
echo ""

cd "$SCRIPT_DIR"
exec mvn spring-boot:run $MVN_OPTS
