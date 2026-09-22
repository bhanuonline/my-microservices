#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
# my-microservices — one-shot status check
# Usage:  ./status.sh
# ─────────────────────────────────────────────────────────────

# colors (safe to omit if terminal doesn't support ANSI)
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

# ---------- Docker containers ----------
echo ""
echo "════════════════════════════════════════════════════════════"
echo " DOCKER CONTAINERS"
echo "════════════════════════════════════════════════════════════"
printf "%-18s %-10s %s\n" "NAME" "STATUS" "PORTS"

check_container() {
  local name=$1
  local line
  line=$(docker ps --filter "name=^${name}$" --format "{{.Status}}|{{.Ports}}" 2>/dev/null)
  if [[ -z "$line" ]]; then
    printf "%-18s ${RED}%-10s${NC} %s\n" "$name" "DOWN" "-"
    return
  fi
  local status=$(echo "$line" | cut -d'|' -f1)
  local ports=$(echo "$line" | cut -d'|' -f2)
  if [[ "$status" == *"healthy"* ]]; then
    printf "%-18s ${GREEN}%-10s${NC} %s\n" "$name" "HEALTHY" "$ports"
  elif [[ "$status" == *"unhealthy"* ]]; then
    printf "%-18s ${RED}%-10s${NC} %s\n" "$name" "UNHEALTHY" "$ports"
  elif [[ "$status" == "Up"* ]]; then
    printf "%-18s ${YELLOW}%-10s${NC} %s\n" "$name" "UP" "$ports"
  else
    printf "%-18s ${YELLOW}%-10s${NC} %s\n" "$name" "$status" "$ports"
  fi
}

for c in mysql-user mysql-product mysql-auth kafka zipkin; do
  check_container "$c"
done

# ---------- Spring Boot services (via actuator/health) ----------
echo ""
echo "════════════════════════════════════════════════════════════"
echo " SPRING BOOT SERVICES  (via /actuator/health)"
echo "════════════════════════════════════════════════════════════"
printf "%-20s %-8s %-8s %s\n" "SERVICE" "PORT" "HTTP" "STATUS"

check_service() {
  local name=$1
  local port=$2
  local body
  local code
  body=$(curl -s --max-time 2 "http://localhost:${port}/actuator/health" 2>/dev/null)
  code=$(curl -s --max-time 2 -o /dev/null -w "%{http_code}" "http://localhost:${port}/actuator/health" 2>/dev/null)

  if [[ "$code" == "000" || -z "$code" ]]; then
    printf "%-20s %-8s ${RED}%-8s${NC} %s\n" "$name" "$port" "DOWN" "-"
  elif [[ "$body" == *'"status":"UP"'* ]]; then
    printf "%-20s %-8s ${GREEN}%-8s${NC} %s\n" "$name" "$port" "$code" "UP"
  else
    printf "%-20s %-8s ${YELLOW}%-8s${NC} %s\n" "$name" "$port" "$code" "reachable but not UP"
  fi
}

check_service "eureka-server"    8761
check_service "api-gateway"      8080
check_service "auth-server"      8095
check_service "resource-server"  8096
check_service "user-service"     8081
check_service "product-service"  8082
check_service "order-service"    8083
check_service "payment-service"  8091
check_service "notification"     8090

# ---------- Eureka registrations ----------
echo ""
echo "════════════════════════════════════════════════════════════"
echo " EUREKA — REGISTERED SERVICES"
echo "════════════════════════════════════════════════════════════"
apps=$(curl -s --max-time 2 -H "Accept: application/json" http://localhost:8761/eureka/apps 2>/dev/null)
if [[ -z "$apps" ]]; then
  echo -e "  ${RED}Eureka unreachable at localhost:8761${NC}"
else
  # parse quickly without jq (name is between <name> tags in XML or "name" key in JSON)
  echo "$apps" | python3 -c "
import sys, json
try:
    d = json.loads(sys.stdin.read())
    apps = d.get('applications', {}).get('application', [])
    if not apps:
        print('  (nothing registered yet)')
    else:
        for a in apps:
            n = a.get('name', '?')
            insts = a.get('instance', [])
            count = len(insts) if isinstance(insts, list) else 1
            print(f'  {n}  ({count} instance{\"s\" if count != 1 else \"\"})')
except Exception as e:
    print(f'  parse error: {e}')
" 2>/dev/null || echo "  (couldn't parse Eureka response — is python3 installed?)"
fi

echo ""
echo "════════════════════════════════════════════════════════════"
echo " Done."
echo "════════════════════════════════════════════════════════════"
echo ""
