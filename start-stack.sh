#!/usr/bin/env bash
# ═════════════════════════════════════════════════════════════════════════════
#  start-stack.sh — bring up the my-microservices stack on your Mac
# ═════════════════════════════════════════════════════════════════════════════
#
#  Model:
#    INFRA  (mysql-shared, kafka, eureka, config-server, …) → Docker containers
#    APPS   (user-service, product-service, …)              → java -jar on host
#           logs → logs/<service>.log
#           pids → logs/<service>.pid     (used by stop-stack.sh)
#
#  Usage:
#    ./start-stack.sh                   # auto-detect: first run → full bootstrap,
#                                       # second run → quick resume everything up
#    ./start-stack.sh --all             # explicit: everything (default profile=full)
#    ./start-stack.sh --lean            # ~5 GB — shop + backoffice + CQRS reads
#    ./start-stack.sh --minimal         # ~2.5 GB — shop checkout
#    ./start-stack.sh --demo            # ~1.8 GB — smallest usable (gateway permit-all)
#
#    ./start-stack.sh --infra-only      # just the Docker containers
#    ./start-stack.sh --apps-only       # assume infra up, launch JVMs only
#    ./start-stack.sh --skip-build      # skip `mvn package`
#
#    ./start-stack.sh user-service                  # start ONE named service + deps
#    ./start-stack.sh user-service,product-service  # subset
#
#    ./start-stack.sh --list            # list known services
#    ./start-stack.sh --status          # what's running right now
#    ./start-stack.sh -h                # help
#
#  Stop:  ./stop-stack.sh  (--apps-only / --infra-only / --purge)
# ═════════════════════════════════════════════════════════════════════════════

set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

# ═══════════════════════════════════════════════════════════════════════════
# Pretty-print helpers
# ═══════════════════════════════════════════════════════════════════════════

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

banner() {
  local msg="$1"
  echo
  echo "${C_BOLD}${C_BLUE}╔══════════════════════════════════════════════════════════════════════════╗${C_RESET}"
  printf  "${C_BOLD}${C_BLUE}║ %-72s ║${C_RESET}\n" "$msg"
  echo "${C_BOLD}${C_BLUE}╚══════════════════════════════════════════════════════════════════════════╝${C_RESET}"
}

step() { echo; echo "${C_BOLD}${C_CYAN}▶ $1${C_RESET}"; }
ok()   { printf "  ${C_GREEN}✓${C_RESET} %s\n"  "$1"; }
warn() { printf "  ${C_YELLOW}!${C_RESET} %s\n" "$1"; }
err()  { printf "  ${C_RED}✗${C_RESET} %s\n"    "$1"; }
info() { printf "  ${C_DIM}·${C_RESET} %s\n"    "$1"; }

# ═══════════════════════════════════════════════════════════════════════════
# Pin JDK 17 — project is Java source=17
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
#   Row format:   name:module-dir:port:spring-profile:jar-pattern:deps
#   deps         = space-separated list of other APP names that must be up
#                  (infra deps are tracked separately in INFRA_FOR per profile)
#
APPS=(
  "eureka-server:eureka-server:8761::target/eureka-server-*.jar:"
  "config-server:config-server:8888::target/config-server-*.jar:eureka-server"
  "auth-server:auth-server:8095::target/auth-server-*.jar:eureka-server"
  "resource-server:resource-server:8096::target/resource-server-*.jar:eureka-server auth-server"
  "api-gateway:api-gateway:8080::target/api-gateway-*.jar:eureka-server auth-server"
  "user-service:user-service:8081::target/user-service-*.jar:eureka-server config-server auth-server"
  "product-service:product-service:8082::target/product-service-*.jar:eureka-server config-server"
  "order-service:order-service:8083::target/order-service-*.jar:eureka-server config-server product-service"
  "payment-service:paymentservice:8084::target/payment-service-*.jar:eureka-server config-server"
  "notification:notification:9999::target/notification-*.jar:eureka-server"
  "order-query:order-query:8086::target/order-query-*.jar:eureka-server"
  "product-query:product-query:8088::target/product-query-*.jar:eureka-server"
  "graphql-bff:graphql-bff:8087::target/graphql-bff-*.jar:eureka-server"
  "shop-ui:shop-ui:8089::target/shop-ui-*.jar:api-gateway"
  "backoffice-ui:backoffice-ui:8090::target/backoffice-ui-*.jar:api-gateway"
)

# Bash 3.2-compatible lookup (macOS default)
lookup_app() {
  local target="$1"
  for r in "${APPS[@]}"; do
    [ "${r%%:*}" = "$target" ] && echo "$r" && return 0
  done
  return 1
}

all_app_names() {
  for r in "${APPS[@]}"; do echo "${r%%:*}"; done
}

# ═══════════════════════════════════════════════════════════════════════════
# Argument parsing
# ═══════════════════════════════════════════════════════════════════════════

SKIP_BUILD=false
INFRA_ONLY=false
APPS_ONLY=false
PROFILE=""           # unset → auto; else full/lean/minimal/demo
SPECIFIC=""          # if non-empty: comma list of services to start (and their deps)
SHOW_LIST=false
SHOW_STATUS=false
EXPLICIT_ALL=false   # user passed --all

for arg in "$@"; do
  case "$arg" in
    --skip-build) SKIP_BUILD=true ;;
    --infra-only) INFRA_ONLY=true ;;
    --apps-only)  APPS_ONLY=true  ;;
    --all)        PROFILE=full; EXPLICIT_ALL=true ;;
    --lean)       PROFILE=lean    ;;
    --minimal)    PROFILE=minimal ;;
    --demo)       PROFILE=demo    ;;
    --list)       SHOW_LIST=true  ;;
    --status)     SHOW_STATUS=true ;;
    -h|--help)
      sed -n '2,33p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    --*) err "unknown flag: $arg" ; exit 1 ;;
    *)
      # Positional: service name(s). Multiple comma-separated OK.
      if [ -z "$SPECIFIC" ]; then SPECIFIC="$arg"
      else                        SPECIFIC="$SPECIFIC,$arg"
      fi
      ;;
  esac
done

# ═══════════════════════════════════════════════════════════════════════════
# --list
# ═══════════════════════════════════════════════════════════════════════════

if $SHOW_LIST; then
  banner "Known services"
  echo
  printf "  ${C_BOLD}%-18s %-6s %s${C_RESET}\n" "NAME" "PORT" "DEPENDS ON"
  for r in "${APPS[@]}"; do
    IFS=':' read -r n _ port _ _ deps <<< "$r"
    printf "  %-18s %-6s %s\n" "$n" "$port" "${deps:-—}"
  done
  echo
  info "profiles: --all (default), --lean, --minimal, --demo"
  exit 0
fi

# ═══════════════════════════════════════════════════════════════════════════
# --status
# ═══════════════════════════════════════════════════════════════════════════

if $SHOW_STATUS; then
  banner "Current stack status"

  step "Docker containers"
  if docker compose ps --format '{{.Name}}\t{{.Status}}' 2>/dev/null | grep -q .; then
    docker compose ps --format 'table {{.Name}}\t{{.Status}}\t{{.Ports}}' | sed 's/^/  /'
  else
    info "no containers running (compose project idle)"
  fi

  step "Spring Boot apps (host JVMs)"
  anyrun=false
  for pidfile in "$LOG_DIR"/*.pid; do
    [ -f "$pidfile" ] || continue
    name=$(basename "$pidfile" .pid)
    pid=$(cat "$pidfile")
    if kill -0 "$pid" 2>/dev/null; then
      ok "$name (pid $pid)"
      anyrun=true
    else
      warn "$name (stale pid file, process gone)"
    fi
  done
  $anyrun || info "no apps running"
  echo
  exit 0
fi

# ═══════════════════════════════════════════════════════════════════════════
# Auto-detect first-time run (if no profile and no specific service given)
# ═══════════════════════════════════════════════════════════════════════════

IS_FIRST_RUN=false

detect_first_run() {
  # Treat as first run if:
  #   (1) no jars in target/ for any module, OR
  #   (2) no containers up for this compose project, OR
  #   (3) no pid files AND no jars
  local any_jar=false
  for r in "${APPS[@]}"; do
    local dir="${r#*:}"; dir="${dir%%:*}"
    local pat="${r##*:}"
    pat="${pat%:*}"   # strip trailing deps field — pattern is 2nd-to-last
    # re-split properly
    IFS=':' read -r _ dir _ _ pat _ <<< "$r"
    if ls "$dir"/$pat >/dev/null 2>&1; then any_jar=true; fi
  done

  local any_container=false
  if docker compose ps -q 2>/dev/null | grep -q .; then any_container=true; fi

  if ! $any_jar || ! $any_container; then
    IS_FIRST_RUN=true
  fi
}

if [ -z "$PROFILE" ] && [ -z "$SPECIFIC" ]; then
  detect_first_run
fi

# Default profile = full (unless the user named a specific service).
if [ -z "$PROFILE" ] && [ -z "$SPECIFIC" ]; then
  PROFILE=full
fi

# ═══════════════════════════════════════════════════════════════════════════
# Profile → infra container list + compose profiles + app waves
# ═══════════════════════════════════════════════════════════════════════════
#
# NOTE: infra names here MUST match docker-compose.yml service keys.
# Shared-db topology: mysql-shared (one container, 4 schemas).
#
case "$PROFILE" in
  demo)
    PROFILE_DESC="shop checkout, no auth (gateway permit-all)"
    PROFILE_RAM="~1.8 GB"
    INFRA_CONTAINERS=(mysql-shared kafka)
    INFRA_HEALTHCHECKS=(mysql-shared kafka)
    COMPOSE_PROFILES_VAL=""
    PROFILE_WAVES=(
      "eureka-server"
      "api-gateway product-service order-service"
      "shop-ui"
    )
    ;;
  minimal)
    PROFILE_DESC="shop + checkout (no ES, no observability)"
    PROFILE_RAM="~2.5 GB"
    INFRA_CONTAINERS=(mysql-shared kafka)
    INFRA_HEALTHCHECKS=(mysql-shared kafka)
    COMPOSE_PROFILES_VAL=""
    PROFILE_WAVES=(
      "eureka-server config-server"
      "auth-server"
      "api-gateway product-service order-service user-service"
      "shop-ui"
    )
    ;;
  lean)
    PROFILE_DESC="shop + backoffice + CQRS reads (adds elasticsearch + cache)"
    PROFILE_RAM="~5 GB"
    INFRA_CONTAINERS=(mysql-shared kafka redis elasticsearch)
    INFRA_HEALTHCHECKS=(mysql-shared kafka redis elasticsearch)
    COMPOSE_PROFILES_VAL="cache,search"
    PROFILE_WAVES=(
      "eureka-server config-server"
      "auth-server"
      "api-gateway product-service order-service user-service"
      "order-query product-query"
      "shop-ui backoffice-ui"
    )
    ;;
  full|*)
    PROFILE=full
    PROFILE_DESC="everything — all apps + full observability"
    PROFILE_RAM="~10 GB"
    # 'full' profile in compose expands to every optional service.
    INFRA_CONTAINERS=()  # empty → bring up everything in the compose project
    INFRA_HEALTHCHECKS=(mysql-shared kafka)
    COMPOSE_PROFILES_VAL="full"
    PROFILE_WAVES=(
      "eureka-server config-server"
      "auth-server resource-server"
      "api-gateway user-service product-service order-service payment-service notification"
      "order-query product-query graphql-bff"
      "shop-ui backoffice-ui"
    )
    ;;
esac

# ═══════════════════════════════════════════════════════════════════════════
# If a specific service was named: compute closure of (service + its deps)
# ═══════════════════════════════════════════════════════════════════════════

SPECIFIC_RESOLVED=()

resolve_specific() {
  local requested="${SPECIFIC//,/ }"
  local -a queue=()
  local -a seen=()

  for s in $requested; do queue+=("$s"); done

  while [ ${#queue[@]} -gt 0 ]; do
    local svc="${queue[0]}"
    queue=("${queue[@]:1}")

    # already seen?
    for s in "${seen[@]}"; do
      [ "$s" = "$svc" ] && continue 2
    done
    seen+=("$svc")

    local row
    row=$(lookup_app "$svc") || { err "unknown service: $svc"; exit 1; }
    local deps="${row##*:}"
    for d in $deps; do queue+=("$d"); done
  done

  # Reverse so deps come first — simple topological insert order.
  SPECIFIC_RESOLVED=()
  for ((i=${#seen[@]}-1; i>=0; i--)); do
    SPECIFIC_RESOLVED+=("${seen[$i]}")
  done
}

if [ -n "$SPECIFIC" ]; then
  resolve_specific
  # Specific-service mode uses full infra by default (safest — all deps covered).
  if [ -z "$PROFILE" ] || [ "$PROFILE" = "full" ]; then
    PROFILE_DESC="specific services: ${SPECIFIC//,/ }  (+ resolved deps)"
    PROFILE_RAM="depends on which services + current infra"
    INFRA_CONTAINERS=()        # bring up full infra
    COMPOSE_PROFILES_VAL="full"
    INFRA_HEALTHCHECKS=(mysql-shared kafka)
    PROFILE=specific
  fi
fi

# ═══════════════════════════════════════════════════════════════════════════
# Opening banner
# ═══════════════════════════════════════════════════════════════════════════

banner "my-microservices launcher · profile=$PROFILE · $PROFILE_RAM"
info "$PROFILE_DESC"
if $IS_FIRST_RUN; then
  info "${C_BOLD}first-time run detected${C_RESET} — full bootstrap (build + infra + apps)"
fi
if [ -n "${COMPOSE_PROFILES_VAL:-}" ]; then
  info "COMPOSE_PROFILES=$COMPOSE_PROFILES_VAL"
fi
if [ "${#INFRA_CONTAINERS[@]}" -gt 0 ]; then
  info "infra:  ${INFRA_CONTAINERS[*]}"
else
  info "infra:  (compose project — all services for the active profile)"
fi

if [ "$PROFILE" = "specific" ]; then
  info "apps (resolved order): ${SPECIFIC_RESOLVED[*]}"
else
  echo "  ${C_DIM}·${C_RESET} waves:"
  wave_num=1
  for w in "${PROFILE_WAVES[@]}"; do
    printf "        wave %d → %s\n" "$wave_num" "$w"
    wave_num=$((wave_num+1))
  done
fi

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
  step "1 · starting infra containers"

  # Export compose profiles if set, so `docker compose up` sees them.
  if [ -n "${COMPOSE_PROFILES_VAL:-}" ]; then
    export COMPOSE_PROFILES="$COMPOSE_PROFILES_VAL"
  fi

  if [ "${#INFRA_CONTAINERS[@]}" -gt 0 ]; then
    for c in "${INFRA_CONTAINERS[@]}"; do info "starting: $c"; done
    echo
    docker compose up -d "${INFRA_CONTAINERS[@]}"
  else
    info "bringing up all services for profile(s): ${COMPOSE_PROFILES_VAL:-default}"
    echo
    docker compose up -d
  fi

  step "2 · waiting for infra health (up to 2 minutes)"
  wait_healthy() {
    local svc="$1" ; local max=60 ; local i=0
    while [ $i -lt $max ]; do
      status=$(docker inspect -f '{{.State.Health.Status}}' "$svc" 2>/dev/null || echo "no-health")
      if [ "$status" = "healthy" ] || [ "$status" = "no-health" ]; then
        ok "$svc  (${status})"
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
  info "run an app from your IDE / or:  mvn -pl user-service spring-boot:run"
  exit 0
fi

# ═══════════════════════════════════════════════════════════════════════════
# 2. Build jars
# ═══════════════════════════════════════════════════════════════════════════

# Figure out whether we actually need to build.
need_build=false
if ! $SKIP_BUILD; then
  if $IS_FIRST_RUN; then
    need_build=true
  else
    # If any required jar is missing, build.
    for r in "${APPS[@]}"; do
      IFS=':' read -r _ dir _ _ pat _ <<< "$r"
      if ! ls "$dir"/$pat >/dev/null 2>&1; then
        need_build=true
        break
      fi
    done
  fi
fi

if $need_build; then
  step "3 · building jars  (mvn -DskipTests package)"
  info "this takes ~30s on a warm cache, ~2m cold"
  if mvn -q -DskipTests package; then
    ok "all modules built"
  else
    err "maven build failed — see output above"
    exit 1
  fi
else
  if $SKIP_BUILD; then
    info "skipping maven build (--skip-build)"
  else
    info "all jars present — skipping build"
  fi
fi

# ═══════════════════════════════════════════════════════════════════════════
# 3. Launch Spring Boot apps
# ═══════════════════════════════════════════════════════════════════════════

step "4 · launching apps"

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

  # Build the active-profiles list.
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

if [ "$PROFILE" = "specific" ]; then
  # Specific service(s) mode — start resolved list in dep order.
  for name in "${SPECIFIC_RESOLVED[@]}"; do
    row=$(lookup_app "$name") || { warn "$name — not in catalogue, skipping"; continue; }
    IFS=':' read -r n dir port sprofile jarpat deps <<< "$row"
    start_app "$n" "$dir" "$port" "$sprofile" "$jarpat"
  done
else
  # Wave-based launch (full / lean / minimal / demo).
  wave_num=1
  for wave in "${PROFILE_WAVES[@]}"; do
    echo
    echo "  ${C_BOLD}── wave $wave_num ──${C_RESET}  $wave"
    for name in $wave; do
      row=$(lookup_app "$name") || { warn "$name — not in catalogue, skipping"; continue; }
      IFS=':' read -r n dir port sprofile jarpat deps <<< "$row"
      start_app "$n" "$dir" "$port" "$sprofile" "$jarpat"
    done
    if [ $wave_num -lt ${#PROFILE_WAVES[@]} ]; then
      info "wave settling (5s before next wave)"
      sleep 5
    fi
    wave_num=$((wave_num+1))
  done
fi

# ═══════════════════════════════════════════════════════════════════════════
# 4. READY summary
# ═══════════════════════════════════════════════════════════════════════════

banner "READY · profile=$PROFILE · $PROFILE_RAM"

echo "  ${C_BOLD}Browse the stack:${C_RESET}"
case "$PROFILE" in
  demo)
    printf "    %-18s %s\n" "Shop UI"      "http://localhost:8089"
    printf "    %-18s %s\n" "API Gateway"  "http://localhost:8080  (permit-all!)"
    printf "    %-18s %s\n" "Eureka"       "http://localhost:8761"
    ;;
  minimal)
    printf "    %-18s %s\n" "Shop UI"      "http://localhost:8089"
    printf "    %-18s %s\n" "API Gateway"  "http://localhost:8080"
    printf "    %-18s %s\n" "Auth Server"  "http://localhost:8095"
    printf "    %-18s %s\n" "Eureka"       "http://localhost:8761"
    ;;
  lean)
    printf "    %-18s %s\n" "Shop UI"      "http://localhost:8089"
    printf "    %-18s %s\n" "Backoffice"   "http://localhost:8090"
    printf "    %-18s %s\n" "API Gateway"  "http://localhost:8080"
    printf "    %-18s %s\n" "Product Query" "http://localhost:8088/products/search"
    printf "    %-18s %s\n" "Order Query"  "http://localhost:8086/orders/search"
    printf "    %-18s %s\n" "Auth Server"  "http://localhost:8095"
    printf "    %-18s %s\n" "Eureka"       "http://localhost:8761"
    printf "    %-18s %s\n" "Elasticsearch" "http://localhost:9200"
    ;;
  specific)
    for name in "${SPECIFIC_RESOLVED[@]}"; do
      row=$(lookup_app "$name") || continue
      IFS=':' read -r n _ port _ _ _ <<< "$row"
      printf "    %-18s %s\n" "$n" "http://localhost:$port"
    done
    ;;
  *)
    printf "    %-18s %s\n" "Shop UI"      "http://localhost:8089"
    printf "    %-18s %s\n" "Backoffice"   "http://localhost:8090"
    printf "    %-18s %s\n" "API Gateway"  "http://localhost:8080"
    printf "    %-18s %s\n" "GraphQL BFF"  "http://localhost:8087/graphiql"
    printf "    %-18s %s\n" "Product Query" "http://localhost:8088/products/search"
    printf "    %-18s %s\n" "Order Query"  "http://localhost:8086/orders/search"
    printf "    %-18s %s\n" "Auth Server"  "http://localhost:8095"
    printf "    %-18s %s\n" "Eureka"       "http://localhost:8761"
    printf "    %-18s %s\n" "Zipkin"       "http://localhost:9411"
    printf "    %-18s %s\n" "Prometheus"   "http://localhost:9090"
    printf "    %-18s %s\n" "Grafana"      "http://localhost:3000"
    printf "    %-18s %s\n" "Mailhog"      "http://localhost:8025"
    printf "    %-18s %s\n" "Kafka UI"     "http://localhost:8090"
    ;;
esac

echo
echo "  ${C_BOLD}Operations:${C_RESET}"
printf  "    %-18s %s\n" "tail a log"      "tail -f $LOG_DIR/<service>.log"
printf  "    %-18s %s\n" "status check"    "./start-stack.sh --status"
printf  "    %-18s %s\n" "stop apps"       "./stop-stack.sh --apps-only"
printf  "    %-18s %s\n" "stop all"        "./stop-stack.sh"
printf  "    %-18s %s\n" "list services"   "./start-stack.sh --list"

echo
info "apps need ~30-60 seconds past launch before HTTP health responds"
info "if a service dies silently, check logs/<service>.log for 'APPLICATION FAILED TO START'"
echo
