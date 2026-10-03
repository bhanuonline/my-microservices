#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
#  start-stack.sh — bring up the whole my-microservices stack on your Mac
#
#  Infra runs in docker (containers you already have in docker-compose.yml).
#  Apps run as plain `java -jar` processes with logs in logs/<service>.log and
#  pids in logs/<service>.pid so stop-stack.sh can kill them cleanly.
#
#  Usage:
#    ./start-stack.sh                 # build if needed, start everything
#    ./start-stack.sh --skip-build    # start fast, assume JARs are current
#    ./start-stack.sh --infra-only    # just the docker containers
#    ./start-stack.sh --apps-only     # assume infra is up
#    ./start-stack.sh -h              # help
#
#  Stop with: ./stop-stack.sh
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

JAVA_HOME_PATH="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-17.0.2.jdk/Contents/Home}"
export JAVA_HOME="$JAVA_HOME_PATH"
export PATH="$JAVA_HOME/bin:$PATH"

LOG_DIR="$ROOT/logs"
mkdir -p "$LOG_DIR"

# ─── service table ───────────────────────────────────────────────────────────
# NAME                MODULE_DIR         PORT    SPRING_PROFILE   JAR_PATTERN
# shape = "name:module:port:profile:jar"
#
# Order matters — eureka + auth first, then infra-dependent, then the UI.
# Payment-service is not yet in docker-compose; it runs on 8084 here.
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
)

SKIP_BUILD=false
INFRA_ONLY=false
APPS_ONLY=false

for arg in "$@"; do
  case "$arg" in
    --skip-build) SKIP_BUILD=true ;;
    --infra-only) INFRA_ONLY=true ;;
    --apps-only)  APPS_ONLY=true  ;;
    -h|--help)
      sed -n '2,20p' "$0" | sed 's/^# //'
      exit 0
      ;;
    *) echo "unknown flag: $arg" ; exit 1 ;;
  esac
done

# ─── 0. sanity ───────────────────────────────────────────────────────────────
command -v docker >/dev/null  || { echo "docker not on PATH"; exit 1; }
command -v mvn >/dev/null     || { echo "maven not on PATH"; exit 1; }
java -version 2>&1 | head -1

# ─── 1. docker infra ─────────────────────────────────────────────────────────
if ! $APPS_ONLY; then
  echo
  echo "─── starting infra containers ───"
  docker compose up -d \
      mysql-user mysql-product mysql-auth \
      kafka \
      redis \
      elasticsearch \
      zipkin prometheus grafana loki promtail \
      vault-dev \
      schema-registry

  echo "waiting for infra to become healthy…"
  wait_healthy() {
    local svc="$1" ; local max=60 ; local i=0
    while [ $i -lt $max ]; do
      status=$(docker inspect -f '{{.State.Health.Status}}' "$svc" 2>/dev/null || echo "no-health")
      if [ "$status" = "healthy" ] || [ "$status" = "no-health" ]; then
        printf "  %-20s ✓\n" "$svc"
        return 0
      fi
      sleep 2 ; i=$((i+1))
    done
    printf "  %-20s ✗ (timed out)\n" "$svc"
    return 1
  }
  for svc in mysql-user mysql-product mysql-auth kafka elasticsearch redis; do
    wait_healthy "$svc" || true
  done
fi

$INFRA_ONLY && { echo "infra-only mode, exiting."; exit 0; }

# ─── 2. build the jars (if needed) ───────────────────────────────────────────
if ! $SKIP_BUILD; then
  echo
  echo "─── mvn package (-DskipTests) ───"
  mvn -q -DskipTests package
fi

# ─── 3. launch each spring boot app ──────────────────────────────────────────
echo
echo "─── starting apps ───"

is_running() {
  local pidfile="$1"
  [ -f "$pidfile" ] && kill -0 "$(cat "$pidfile")" 2>/dev/null
}

start_app() {
  local name="$1" dir="$2" port="$3" profile="$4" jarpat="$5"
  local pidfile="$LOG_DIR/${name}.pid"
  local logfile="$LOG_DIR/${name}.log"

  if is_running "$pidfile"; then
    printf "  %-20s ALREADY RUNNING (pid %s)\n" "$name" "$(cat "$pidfile")"
    return 0
  fi

  local jar
  jar=$(ls -1 "$dir"/$jarpat 2>/dev/null | head -1 || true)
  if [ -z "$jar" ]; then
    printf "  %-20s ✗ no jar found at %s/%s\n" "$name" "$dir" "$jarpat"
    return 1
  fi

  local jvmopts="-Dserver.port=$port"
  [ -n "$profile" ] && jvmopts="$jvmopts -Dspring.profiles.active=$profile"

  nohup java $jvmopts -jar "$jar" >"$logfile" 2>&1 &
  echo $! > "$pidfile"
  printf "  %-20s started on :%s (pid %s, log %s)\n" "$name" "$port" "$!" "$logfile"
  sleep 1
}

# Launch in dependency-order waves so downstream services can register with Eureka
# BEFORE anything calls them. Each wave is parallel internally; we sleep between
# waves to give the previous one a head start on service registration.
WAVES=(
  "eureka-server"
  "auth-server resource-server"
  "api-gateway user-service product-service order-service payment-service notification"
  "order-query product-query graphql-bff"
  "shop-ui"
)

for row in "${APPS[@]}"; do declare -g "APP_${row%%:*}=$row"; done

for wave in "${WAVES[@]}"; do
  for name in $wave; do
    varname="APP_${name//-/_}"
    # Variable names can't have dashes; look up with name-mangling.
    row="${!varname:-}"
    if [ -z "$row" ]; then
      # Alt lookup: scan APPS array
      for r in "${APPS[@]}"; do [[ "$r" == "$name:"* ]] && row="$r" && break; done
    fi
    [ -z "$row" ] && { echo "  $name — not in app table, skipping"; continue; }
    IFS=':' read -r n dir port profile jarpat <<< "$row"
    start_app "$n" "$dir" "$port" "$profile" "$jarpat"
  done
  echo "  ── wave settling (5s) ──"
  sleep 5
done

# ─── 4. summary ──────────────────────────────────────────────────────────────
echo
echo "─── READY ───"
cat <<EOF
  Shop UI          http://localhost:8089
  API Gateway      http://localhost:8080
  GraphQL BFF      http://localhost:8087/graphiql
  Product Query    http://localhost:8088/products/search
  Order Query      http://localhost:8086/orders/search
  Auth Server      http://localhost:8095
  Eureka           http://localhost:8761
  Zipkin           http://localhost:9411
  Prometheus       http://localhost:9090
  Grafana          http://localhost:3000   (admin / admin)
  Elasticsearch    http://localhost:9200

Logs:   $LOG_DIR/<service>.log
Stop:   ./stop-stack.sh
EOF
