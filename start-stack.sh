#!/usr/bin/env bash
# ═════════════════════════════════════════════════════════════════════════════
#  start-stack.sh — bring up the my-microservices stack on your Mac
# ═════════════════════════════════════════════════════════════════════════════
#
#  Infra  runs in docker (containers defined in docker-compose.yml)
#  Apps   run as plain `java -jar` processes
#         logs → logs/<service>.log
#         pids → logs/<service>.pid   (used by stop-stack.sh)
#
#  Usage:
#    ./start-stack.sh                 # full stack        ~10  GB RAM
#    ./start-stack.sh --lean          # shop + backoffice ~5   GB RAM
#    ./start-stack.sh --minimal       # shop checkout     ~2.5 GB RAM
#    ./start-stack.sh --demo          # smallest usable   ~1.8 GB RAM
#    ./start-stack.sh --skip-build    # skip mvn package
#    ./start-stack.sh --infra-only    # just the containers
#    ./start-stack.sh --apps-only     # assume infra up, launch apps
#    ./start-stack.sh -h              # help
#
#  Stop:  ./stop-stack.sh
# ═════════════════════════════════════════════════════════════════════════════

set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

# ═══════════════════════════════════════════════════════════════════════════
# Pretty-print helpers — used everywhere below so the output is readable
# ═══════════════════════════════════════════════════════════════════════════

# ANSI colors (fall back to no-color if stdout is not a tty)
if [ -t 1 ]; then
  C_RESET=$'\033[0m'
  C_BOLD=$'\033[1m'
  C_DIM=$'\033[2m'
  C_RED=$'\033[31m'
  C_GREEN=$'\033[32m'
  C_YELLOW=$'\033[33m'
  C_BLUE=$'\033[34m'
  C_CYAN=$'\033[36m'
else
  C_RESET=""; C_BOLD=""; C_DIM=""; C_RED=""; C_GREEN=""; C_YELLOW=""; C_BLUE=""; C_CYAN=""
fi

# Big section banner
banner() {
  local msg="$1"
  echo
  echo "${C_BOLD}${C_BLUE}╔══════════════════════════════════════════════════════════════════════════╗${C_RESET}"
  printf  "${C_BOLD}${C_BLUE}║ %-72s ║${C_RESET}\n" "$msg"
  echo "${C_BOLD}${C_BLUE}╚══════════════════════════════════════════════════════════════════════════╝${C_RESET}"
}

# Smaller sub-step heading
step() { echo; echo "${C_BOLD}${C_CYAN}▶ $1${C_RESET}"; }

# Status lines
ok()   { printf "  ${C_GREEN}✓${C_RESET} %s\n"  "$1"; }
warn() { printf "  ${C_YELLOW}!${C_RESET} %s\n" "$1"; }
err()  { printf "  ${C_RED}✗${C_RESET} %s\n"    "$1"; }
info() { printf "  ${C_DIM}·${C_RESET} %s\n"    "$1"; }

# ═══════════════════════════════════════════════════════════════════════════
# Pin JDK 17 — the project is Java source=17
# ═══════════════════════════════════════════════════════════════════════════

JAVA_HOME_17="${JAVA_HOME_17:-/Library/Java/JavaVirtualMachines/jdk-17.0.2.jdk/Contents/Home}"
if [ ! -d "$JAVA_HOME_17" ]; then
  err "JDK 17 not found at $JAVA_HOME_17"
  info "Install one, or set JAVA_HOME_17=/path/to/jdk-17 and re-run."
  exit 1
fi
export JAVA_HOME="$JAVA_HOME_17"
export PATH="$JAVA_HOME/bin:$PATH"

LOG_DIR="$ROOT/logs"
mkdir -p "$LOG_DIR"

# ═══════════════════════════════════════════════════════════════════════════
# Service catalogue
# ═══════════════════════════════════════════════════════════════════════════
#
#  Row format:  name:module-dir:port:spring-profile:jar-pattern
#  Spring profile is empty for most — only set when the service needs one.
#
APPS=(
  "eureka-server:eureka-server:8761::target/eureka-server-*.jar"
  "auth-server:auth-server:8095::target/auth-server-*.jar"
  "resource-server:resource-server:8096::target/resource-server-*.jar"
  "api-gateway:api-gateway:8080::target/api-gateway-*.jar"
  "user-service:user-service:8081::target/user-service-*.jar"
  "product-service:product-service:8082::target/product-service-*.jar"
  "order-service:order-service:8083::target/order-service-*.jar"
  "payment-service:paymentservice:8084::target/payment-service-*.jar"
  "notification:notification:9999::target/notification-*.jar"
  "order-query:order-query:8086::target/order-query-*.jar"
  "product-query:product-query:8088::target/product-query-*.jar"
  "graphql-bff:graphql-bff:8087::target/graphql-bff-*.jar"
  "shop-ui:shop-ui:8089::target/shop-ui-*.jar"
  "backoffice-ui:backoffice-ui:8090::target/backoffice-ui-*.jar"
)

# ═══════════════════════════════════════════════════════════════════════════
# Argument parsing
# ═══════════════════════════════════════════════════════════════════════════

SKIP_BUILD=false
INFRA_ONLY=false
APPS_ONLY=false
PROFILE=full          # full | lean | minimal | demo

for arg in "$@"; do
  case "$arg" in
    --skip-build) SKIP_BUILD=true ;;
    --infra-only) INFRA_ONLY=true ;;
    --apps-only)  APPS_ONLY=true  ;;
    --lean)       PROFILE=lean    ;;
    --minimal)    PROFILE=minimal ;;
    --demo)       PROFILE=demo    ;;
    -h|--help)
      sed -n '2,21p' "$0" | sed 's/^# //'
      exit 0
      ;;
    *) err "unknown flag: $arg" ; exit 1 ;;
  esac
done

# ═══════════════════════════════════════════════════════════════════════════
# Profile → what runs
# ═══════════════════════════════════════════════════════════════════════════
#
#  minimal  — ~2.5 GB. Shop checkout. No ES → no CQRS reads.
#  lean     — ~5   GB. Shop + backoffice + CQRS reads. No observability.
#  full     — ~10  GB. Everything, including Grafana / Zipkin / Prometheus.
#
case "$PROFILE" in
  demo)
    # Smallest usable mode. Drops auth-server + mysql-auth; gateway runs
    # permit-all under spring profile 'demo' so the shop still works.
    # Not valid in prod — no auth, no JWT validation.
    PROFILE_DESC="shop checkout, no auth (gateway permit-all)"
    PROFILE_RAM="~1.8 GB"
    INFRA_CONTAINERS=(mysql-user mysql-product kafka redis)
    INFRA_HEALTHCHECKS=(mysql-product kafka redis)
    PROFILE_WAVES=(
      "eureka-server"
      "api-gateway product-service order-service"
      "shop-ui"
    )
    ;;
  minimal)
    PROFILE_DESC="shop + checkout (no ES, no observability)"
    PROFILE_RAM="~2.5 GB"
    INFRA_CONTAINERS=(mysql-user mysql-product mysql-auth kafka redis)
    INFRA_HEALTHCHECKS=(mysql-product mysql-auth kafka redis)
    PROFILE_WAVES=(
      "eureka-server"
      "auth-server"
      "api-gateway product-service order-service"
      "shop-ui"
    )
    ;;
  lean)
    PROFILE_DESC="shop + backoffice + CQRS reads (no observability)"
    PROFILE_RAM="~5 GB"
    INFRA_CONTAINERS=(mysql-user mysql-product mysql-auth kafka redis elasticsearch)
    INFRA_HEALTHCHECKS=(mysql-user mysql-product mysql-auth kafka redis elasticsearch)
    PROFILE_WAVES=(
      "eureka-server"
      "auth-server"
      "api-gateway product-service order-service"
      "order-query product-query"
      "shop-ui backoffice-ui"
    )
    ;;
  full|*)
    PROFILE_DESC="everything — all 14 apps + full observability"
    PROFILE_RAM="~10 GB"
    INFRA_CONTAINERS=(mysql-user mysql-product mysql-auth kafka redis elasticsearch
                      zipkin prometheus grafana loki promtail vault-dev schema-registry)
    INFRA_HEALTHCHECKS=(mysql-user mysql-product mysql-auth kafka elasticsearch redis)
    PROFILE_WAVES=(
      "eureka-server"
      "auth-server resource-server"
      "api-gateway user-service product-service order-service payment-service notification"
      "order-query product-query graphql-bff"
      "shop-ui backoffice-ui"
    )
    ;;
esac

# ═══════════════════════════════════════════════════════════════════════════
# Opening banner — what we're about to do
# ═══════════════════════════════════════════════════════════════════════════

banner "my-microservices launcher · profile=$PROFILE · $PROFILE_RAM"
info "$PROFILE_DESC"
info "containers:  ${INFRA_CONTAINERS[*]}"
echo "  ${C_DIM}·${C_RESET} waves:"
wave_num=1
for w in "${PROFILE_WAVES[@]}"; do
  printf "        wave %d → %s\n" "$wave_num" "$w"
  wave_num=$((wave_num+1))
done

# ═══════════════════════════════════════════════════════════════════════════
# 0. Sanity checks
# ═══════════════════════════════════════════════════════════════════════════

step "0 · sanity"
command -v docker >/dev/null  || { err "docker not on PATH"; exit 1; }
command -v mvn    >/dev/null  || { err "maven not on PATH"; exit 1; }
ok "docker: $(docker --version | awk '{print $3}' | tr -d ,)"
ok "maven:  $(mvn --version 2>/dev/null | head -1 | awk '{print $3}')"
ok "java:   $(java -version 2>&1 | head -1 | awk -F'"' '{print $2}')"
ok "logs:   $LOG_DIR"

# ═══════════════════════════════════════════════════════════════════════════
# 1. Docker infra
# ═══════════════════════════════════════════════════════════════════════════

if ! $APPS_ONLY; then
  step "1 · starting infra containers (${#INFRA_CONTAINERS[@]} total)"
  for c in "${INFRA_CONTAINERS[@]}"; do
    info "will start: $c"
  done
  echo
  docker compose up -d "${INFRA_CONTAINERS[@]}"

  step "2 · waiting for infra health (up to 2 minutes)"
  wait_healthy() {
    local svc="$1" ; local max=60 ; local i=0
    while [ $i -lt $max ]; do
      status=$(docker inspect -f '{{.State.Health.Status}}' "$svc" 2>/dev/null || echo "no-health")
      if [ "$status" = "healthy" ] || [ "$status" = "no-health" ]; then
        ok "$svc  (healthy)"
        return 0
      fi
      sleep 2 ; i=$((i+1))
    done
    warn "$svc  (health probe timed out — continuing anyway)"
    return 1
  }
  for svc in "${INFRA_HEALTHCHECKS[@]}"; do
    wait_healthy "$svc" || true
  done
else
  info "skipping infra (--apps-only)"
fi

if $INFRA_ONLY; then
  banner "infra-only mode — containers are up, apps NOT launched"
  info "stop with: ./stop-stack.sh --infra-only"
  exit 0
fi

# ═══════════════════════════════════════════════════════════════════════════
# 2. Build jars (unless --skip-build)
# ═══════════════════════════════════════════════════════════════════════════

if ! $SKIP_BUILD; then
  step "3 · building jars  (mvn -DskipTests package)"
  info "this takes ~30s on a warm cache, ~2m cold"
  if mvn -q -DskipTests package; then
    ok "all modules built"
  else
    err "maven build failed — see output above"
    exit 1
  fi
else
  info "skipping maven build (--skip-build)"
fi

# ═══════════════════════════════════════════════════════════════════════════
# 3. Launch Spring Boot apps in dependency waves
# ═══════════════════════════════════════════════════════════════════════════

step "4 · launching apps in dependency waves"
info "each service runs as java -jar in the background"
info "logs → logs/<service>.log  ·  pids → logs/<service>.pid"

is_running() {
  local pidfile="$1"
  [ -f "$pidfile" ] && kill -0 "$(cat "$pidfile")" 2>/dev/null
}

start_app() {
  local name="$1" dir="$2" port="$3" sprofile="$4" jarpat="$5"
  local pidfile="$LOG_DIR/${name}.pid"
  local logfile="$LOG_DIR/${name}.log"

  if is_running "$pidfile"; then
    printf "    ${C_YELLOW}=${C_RESET} %-18s already running (pid %s)\n" "$name" "$(cat "$pidfile")"
    return 0
  fi

  local jar
  jar=$(ls -1 "$dir"/$jarpat 2>/dev/null | head -1 || true)
  if [ -z "$jar" ]; then
    printf "    ${C_RED}✗${C_RESET} %-18s no jar at %s/%s\n" "$name" "$dir" "$jarpat"
    return 1
  fi

  # Build the active-profiles list: table default + launch-mode override.
  # api-gateway specifically needs the 'demo' profile in --demo mode so that
  # DemoProfileSecurityConfig activates and the JWT issuer-uri is cleared.
  local profiles="$sprofile"
  if [ "$PROFILE" = "demo" ] && [ "$name" = "api-gateway" ]; then
    profiles="${profiles:+${profiles},}demo"
  fi

  local jvmopts="-Dserver.port=$port"
  [ -n "$profiles" ] && jvmopts="$jvmopts -Dspring.profiles.active=$profiles"

  nohup java $jvmopts -jar "$jar" >"$logfile" 2>&1 &
  echo $! > "$pidfile"
  printf "    ${C_GREEN}+${C_RESET} %-18s started on :%s (pid %s)\n" "$name" "$port" "$!"
  sleep 1
}

# Bash 3.2-compatible lookup (macOS default is 3.2 — no declare -g, no ${!var})
lookup_app() {
  local target="$1"
  for r in "${APPS[@]}"; do
    [ "${r%%:*}" = "$target" ] && echo "$r" && return 0
  done
  return 1
}

WAVES=("${PROFILE_WAVES[@]}")

wave_num=1
for wave in "${WAVES[@]}"; do
  echo
  echo "  ${C_BOLD}── wave $wave_num ──${C_RESET}  $wave"
  for name in $wave; do
    row=$(lookup_app "$name") || { warn "$name — not in app table, skipping"; continue; }
    IFS=':' read -r n dir port sprofile jarpat <<< "$row"
    start_app "$n" "$dir" "$port" "$sprofile" "$jarpat"
  done
  if [ $wave_num -lt ${#WAVES[@]} ]; then
    info "wave settling (5s before next wave)"
    sleep 5
  fi
  wave_num=$((wave_num+1))
done

# ═══════════════════════════════════════════════════════════════════════════
# 4. READY summary
# ═══════════════════════════════════════════════════════════════════════════

banner "READY · profile=$PROFILE · $PROFILE_RAM"

echo "  ${C_BOLD}Browse the stack:${C_RESET}"
printf  "    %-18s %s\n" "Shop UI"      "http://localhost:8089"
case "$PROFILE" in
  demo)
    printf "    %-18s %s\n" "API Gateway"    "http://localhost:8080  (permit-all!)"
    printf "    %-18s %s\n" "Eureka"         "http://localhost:8761"
    ;;
  minimal)
    printf "    %-18s %s\n" "API Gateway"    "http://localhost:8080"
    printf "    %-18s %s\n" "Auth Server"    "http://localhost:8095"
    printf "    %-18s %s\n" "Eureka"         "http://localhost:8761"
    ;;
  lean)
    printf "    %-18s %s\n" "Backoffice"     "http://localhost:8090"
    printf "    %-18s %s\n" "API Gateway"    "http://localhost:8080"
    printf "    %-18s %s\n" "Product Query"  "http://localhost:8088/products/search"
    printf "    %-18s %s\n" "Order Query"    "http://localhost:8086/orders/search"
    printf "    %-18s %s\n" "Auth Server"    "http://localhost:8095"
    printf "    %-18s %s\n" "Eureka"         "http://localhost:8761"
    printf "    %-18s %s\n" "Elasticsearch"  "http://localhost:9200"
    ;;
  *)
    printf "    %-18s %s\n" "Backoffice"     "http://localhost:8090"
    printf "    %-18s %s\n" "API Gateway"    "http://localhost:8080"
    printf "    %-18s %s\n" "GraphQL BFF"    "http://localhost:8087/graphiql"
    printf "    %-18s %s\n" "Product Query"  "http://localhost:8088/products/search"
    printf "    %-18s %s\n" "Order Query"    "http://localhost:8086/orders/search"
    printf "    %-18s %s\n" "Auth Server"    "http://localhost:8095"
    printf "    %-18s %s\n" "Eureka"         "http://localhost:8761"
    printf "    %-18s %s\n" "Zipkin"         "http://localhost:9411"
    printf "    %-18s %s\n" "Prometheus"     "http://localhost:9090"
    printf "    %-18s %s\n" "Grafana"        "http://localhost:3000  (admin/admin)"
    printf "    %-18s %s\n" "Elasticsearch"  "http://localhost:9200"
    ;;
esac

echo
echo "  ${C_BOLD}Operations:${C_RESET}"
printf  "    %-18s %s\n" "tail a log"   "tail -f $LOG_DIR/<service>.log"
printf  "    %-18s %s\n" "list pids"    "cat $LOG_DIR/*.pid"
printf  "    %-18s %s\n" "stop apps"    "./stop-stack.sh --apps-only"
printf  "    %-18s %s\n" "stop all"     "./stop-stack.sh"

echo
info "apps need ~30-60 seconds past launch before HTTP health responds"
info "if a service dies silently, check its log for 'APPLICATION FAILED TO START'"
echo
