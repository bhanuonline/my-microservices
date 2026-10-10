#!/usr/bin/env bash
# =====================================================================
# verify-stack.sh — one-shot health check for a running stack
#
# Auto-detects which containers are running (nano/minimal/full) and
# verifies the ones that are up. Doesn't start or stop anything.
#
# Checks:
#   1. docker compose ps          (all expected containers Up)
#   2. docker stats               (RAM budget per container)
#   3. Infra probes               (MySQL / Kafka / Redis — only if up)
#   4. Spring Boot health URLs    (each service's /actuator/health)
#   5. Eureka registration        (services registered + visible)
#
# Exit code: 0 = all green, 1 = any check failed. Pipeline-friendly.
#
# Usage:
#   ./scripts/verify-stack.sh           # pretty output + checks
#   ./scripts/verify-stack.sh --quiet   # only prints failures
#   make verify                         # wrapper
# =====================================================================

set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# ─── Pretty-print palette ─────────────────────────────────────────────
if [ -t 1 ]; then
  C_RESET=$'\033[0m'
  C_BOLD=$'\033[1m'
  C_DIM=$'\033[2m'
  C_UNDER=$'\033[4m'

  # Foreground
  C_BLACK=$'\033[30m';  C_RED=$'\033[31m';     C_GREEN=$'\033[32m'
  C_YELLOW=$'\033[33m'; C_BLUE=$'\033[34m';    C_MAGENTA=$'\033[35m'
  C_CYAN=$'\033[36m';   C_WHITE=$'\033[37m'

  # Bright foreground
  C_BRED=$'\033[91m';   C_BGREEN=$'\033[92m';  C_BYELLOW=$'\033[93m'
  C_BBLUE=$'\033[94m';  C_BMAGENTA=$'\033[95m';C_BCYAN=$'\033[96m'

  # Background
  BG_RED=$'\033[41m';   BG_GREEN=$'\033[42m';  BG_YELLOW=$'\033[43m'
  BG_BLUE=$'\033[44m';  BG_MAGENTA=$'\033[45m';BG_CYAN=$'\033[46m'
else
  C_RESET="";C_BOLD="";C_DIM="";C_UNDER=""
  C_BLACK="";C_RED="";C_GREEN="";C_YELLOW="";C_BLUE="";C_MAGENTA="";C_CYAN="";C_WHITE=""
  C_BRED="";C_BGREEN="";C_BYELLOW="";C_BBLUE="";C_BMAGENTA="";C_BCYAN=""
  BG_RED="";BG_GREEN="";BG_YELLOW="";BG_BLUE="";BG_MAGENTA="";BG_CYAN=""
fi

QUIET=false
[ "${1:-}" = "--quiet" ] && QUIET=true

# ─── Decorated helpers ────────────────────────────────────────────────

banner() {
  $QUIET && return
  local msg="$1"
  echo
  printf "${C_BOLD}${C_BBLUE}╔══════════════════════════════════════════════════════════════════════════╗${C_RESET}\n"
  printf "${C_BOLD}${C_BBLUE}║${C_RESET} ${C_BOLD}${C_WHITE}%-72s${C_RESET} ${C_BOLD}${C_BBLUE} ║${C_RESET}\n" "$msg"
  printf "${C_BOLD}${C_BBLUE}╚══════════════════════════════════════════════════════════════════════════╝${C_RESET}\n"
}

section() {
  $QUIET && return
  local num="$1" title="$2"
  echo
  printf "${BG_BLUE}${C_WHITE}${C_BOLD} %s ${C_RESET} ${C_BOLD}${C_BBLUE}%s${C_RESET}\n" "$num" "$title"
  printf "${C_DIM}${C_BLUE}─────────────────────────────────────────────────────────────────────────────${C_RESET}\n"
}

pass() { $QUIET && return; printf "   ${BG_GREEN}${C_BLACK}${C_BOLD} PASS ${C_RESET} ${C_GREEN}%s${C_RESET}\n" "$1"; }
fail() { printf "   ${BG_RED}${C_WHITE}${C_BOLD} FAIL ${C_RESET} ${C_BRED}${C_BOLD}%s${C_RESET}\n" "$1"; FAILS=$((FAILS+1)); }
warn() { $QUIET && return; printf "   ${BG_YELLOW}${C_BLACK}${C_BOLD} WARN ${C_RESET} ${C_YELLOW}%s${C_RESET}\n" "$1"; }
skip() { $QUIET && return; printf "   ${C_DIM}${BG_BLUE}${C_WHITE} SKIP ${C_RESET} ${C_DIM}%s${C_RESET}\n" "$1"; }

# Progress bar — int percent (0-100) → colored bar
bar() {
  local pct=${1%.*}; [ -z "$pct" ] && pct=0
  local filled=$(( pct / 5 ))   # 20-char bar
  [ $filled -gt 20 ] && filled=20
  local empty=$(( 20 - filled ))
  local color=$C_GREEN
  [ $pct -gt 70 ] && color=$C_YELLOW
  [ $pct -gt 90 ] && color=$C_RED
  printf "${color}"
  local i
  for ((i=0;i<filled;i++)); do printf "█"; done
  printf "${C_DIM}"
  for ((i=0;i<empty;i++)); do printf "░"; done
  printf "${C_RESET}"
}

FAILS=0
TOTAL_CHECKS=0

# ─── MYSQL root password from .env ────────────────────────────────────
MYSQL_PW="$(grep -E '^MYSQL_ROOT_PASSWORD=' .env 2>/dev/null | cut -d= -f2)"

# Helper: is this container currently running?
is_running() {
  docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null | grep -q true
}

# ═══════════════════════════════════════════════════════════════════════
# HEADER
# ═══════════════════════════════════════════════════════════════════════

banner "my-microservices · stack verifier"
if ! $QUIET; then
  printf "   ${C_DIM}project:${C_RESET}  %s\n" "$(basename "$ROOT")"
  printf "   ${C_DIM}docker:${C_RESET}   %s\n" "$(docker --version 2>/dev/null | awk '{print $3}' | tr -d ,)"
  printf "   ${C_DIM}time:${C_RESET}     %s\n" "$(date '+%Y-%m-%d %H:%M:%S')"
fi

if [ -z "$MYSQL_PW" ]; then
  echo
  warn ".env missing MYSQL_ROOT_PASSWORD — MySQL probe will be skipped"
fi

# ═══════════════════════════════════════════════════════════════════════
# 1. Compose ps — what's up
# ═══════════════════════════════════════════════════════════════════════

section "1" "Containers running"

if ! docker compose ps -q 2>/dev/null | grep -q .; then
  fail "no containers running in this compose project"
  echo
  printf "${BG_RED}${C_WHITE}${C_BOLD} SUMMARY ${C_RESET} ${C_BRED}1 check failed. Run \`make up-nano\` (or up-minimal) first.${C_RESET}\n\n"
  exit 1
fi

docker compose ps --format 'table {{.Name}}\t{{.Status}}' 2>/dev/null | tail -n +2 | while read -r name status; do
  TOTAL_CHECKS=$((TOTAL_CHECKS+1))
  if echo "$status" | grep -q 'healthy'; then
    pass "$(printf '%-18s ${C_DIM}%s${C_RESET}' "$name" "$status")"
  elif echo "$status" | grep -qE '^Up'; then
    pass "$(printf '%-18s ${C_DIM}%s${C_RESET}' "$name" "$status")"
  else
    fail "$(printf '%-18s %s' "$name" "$status")"
  fi
done

# ═══════════════════════════════════════════════════════════════════════
# 2. docker stats — RAM budget with progress bars
# ═══════════════════════════════════════════════════════════════════════

section "2" "Resource usage"

if ! $QUIET; then
  printf "   ${C_BOLD}${C_UNDER}%-18s %-10s %-28s %s${C_RESET}\n" "NAME" "CPU%" "MEM" "USAGE"

  docker stats --no-stream --format '{{.Name}}|{{.CPUPerc}}|{{.MemUsage}}|{{.MemPerc}}' 2>/dev/null | \
  while IFS='|' read -r name cpu mem_use mem_pct; do
    pct_num="${mem_pct%\%}"
    pct_num="${pct_num%.*}"
    [ -z "$pct_num" ] && pct_num=0
    color=$C_GREEN
    [ "$pct_num" -gt 70 ] 2>/dev/null && color=$C_YELLOW
    [ "$pct_num" -gt 90 ] 2>/dev/null && color=$C_RED
    printf "   %-18s %-10s %-28s %s ${color}%s${C_RESET}\n" \
      "$name" "$cpu" "$mem_use" "$(bar $pct_num)" "$mem_pct"
  done
fi

over=$(docker stats --no-stream --format '{{.Name}} {{.MemPerc}}' 2>/dev/null | awk '{ gsub("%",""); if ($2+0 > 90) print $1 }')
if [ -n "$over" ]; then
  for c in $over; do fail "$c is at >90% of its mem_limit — risk of OOM-kill"; done
else
  $QUIET || pass "all containers within memory budget (≤90%)"
fi

# ═══════════════════════════════════════════════════════════════════════
# 3. Infra probes — mysql / kafka / redis (only if running)
# ═══════════════════════════════════════════════════════════════════════

section "3" "Infrastructure  ·  MySQL · Kafka · Redis"

if is_running mysql-shared && [ -n "$MYSQL_PW" ]; then
  schemas=$(docker exec mysql-shared mysql -uroot -p"$MYSQL_PW" \
      -N -e "SHOW DATABASES;" 2>/dev/null | grep -cE '^userdb|^productdb|^authdb|^paymentdb')
  if [ "$schemas" -eq 4 ]; then
    pass "mysql-shared ${C_DIM}·${C_RESET} 4 schemas  ${C_DIM}(userdb · productdb · authdb · paymentdb)${C_RESET}"
  else
    fail "mysql-shared · only $schemas/4 schemas found"
  fi
elif is_running mysql-shared; then
  warn "mysql-shared running but MYSQL_ROOT_PASSWORD missing — skipping probe"
else
  skip "mysql-shared not running (expected in some modes)"
fi

if is_running kafka; then
  if docker exec kafka kafka-broker-api-versions.sh --bootstrap-server localhost:9092 >/dev/null 2>&1; then
    pass "kafka         ${C_DIM}·${C_RESET} broker responds to API-versions"
  else
    fail "kafka · broker not responding"
  fi
else
  skip "kafka not running"
fi

if is_running redis; then
  if docker exec redis redis-cli ping 2>/dev/null | grep -q PONG; then
    pass "redis         ${C_DIM}·${C_RESET} ${C_BOLD}PONG${C_RESET}"
  else
    fail "redis · not responding to ping"
  fi
else
  skip "redis not running"
fi

# ═══════════════════════════════════════════════════════════════════════
# 4. Spring Boot health URLs
# ═══════════════════════════════════════════════════════════════════════

section "4" "Spring Boot services  ·  /actuator/health"

check_http() {
  local name="$1" port="$2" container="$3"
  if ! is_running "$container"; then
    skip "$(printf '%-18s container not running' "$name")"
    return
  fi
  local code
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "http://localhost:$port/actuator/health" 2>/dev/null)
  local badge
  if [ "$code" = "200" ]; then
    pass "$(printf '%-18s ${C_BOLD}%s${C_RESET} ${C_DIM}on :%s${C_RESET}' "$name" "HTTP 200" "$port")"
  elif [ "$code" = "000" ]; then
    fail "$(printf '%-18s no response on :%s' "$name" "$port")"
  else
    fail "$(printf '%-18s HTTP %s on :%s' "$name" "$code" "$port")"
  fi
}

check_http eureka-server    8761 eureka-server
check_http config-server    8888 config-server
check_http auth-server      8095 auth-server
check_http api-gateway      8080 api-gateway
check_http resource-server  8096 resource-server
check_http user-service     8081 user-service
check_http product-service  8082 product-service
check_http order-service    8083 order-service
check_http payment-service  8091 payment-service
check_http notification     8099 notification

# ═══════════════════════════════════════════════════════════════════════
# 5. Eureka registration
# ═══════════════════════════════════════════════════════════════════════

section "5" "Eureka registration"

if is_running eureka-server; then
  apps=$(curl -s --max-time 5 http://localhost:8761/eureka/apps 2>/dev/null \
      | grep -oE '<name>[^<]+' | sed 's/<name>//' | sort -u)
  if [ -z "$apps" ]; then
    warn "no services registered yet with Eureka (give it 30-60s after boot)"
  else
    count=$(echo "$apps" | wc -l | tr -d ' ')
    pass "$count service(s) registered"
    if ! $QUIET; then
      echo "$apps" | while read -r app; do
        printf "          ${C_BMAGENTA}▸${C_RESET} ${C_BOLD}%s${C_RESET}\n" "$app"
      done
    fi
  fi
else
  skip "eureka-server not running — skipping registration check"
fi

# ═══════════════════════════════════════════════════════════════════════
# Summary banner
# ═══════════════════════════════════════════════════════════════════════

echo
echo
if [ $FAILS -eq 0 ]; then
  printf "${BG_GREEN}${C_BLACK}${C_BOLD}                                                                              ${C_RESET}\n"
  printf "${BG_GREEN}${C_BLACK}${C_BOLD}   ✓  ALL GREEN  ·  stack is healthy                                          ${C_RESET}\n"
  printf "${BG_GREEN}${C_BLACK}${C_BOLD}                                                                              ${C_RESET}\n"
  echo
  exit 0
else
  printf "${BG_RED}${C_WHITE}${C_BOLD}                                                                              ${C_RESET}\n"
  printf "${BG_RED}${C_WHITE}${C_BOLD}   ✗  %d CHECK(S) FAILED                                                       ${C_RESET}\n" "$FAILS"
  printf "${BG_RED}${C_WHITE}${C_BOLD}                                                                              ${C_RESET}\n"
  echo
  printf "   ${C_DIM}Dig in:${C_RESET} ${C_BOLD}docker compose logs --tail 50 <service>${C_RESET}\n"
  echo
  exit 1
fi
