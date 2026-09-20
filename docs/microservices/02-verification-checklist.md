# 02 — Verification checklist

Copy-paste commands to check every component is truly working, not just "running".

## ⚡ ONE-COMMAND STATUS — use this first

The fastest "is anything working" check. Two ways:

### Option 1 — the status.sh script (pretty output, colored)

```bash
cd /Users/bhanupratap/My/my-microservices
./status.sh
```

Shows all Docker containers + Spring Boot health + Eureka registrations in one screen. Green = good, yellow = warning, red = down.

### Option 2 — inline one-liner (works from anywhere)

```bash
docker ps --format 'table {{.Names}}\t{{.Status}}' && for p in 8080 8081 8082 8083 8090 8091 8095 8761; do echo -n "port $p: "; curl -s -o /dev/null -w "%{http_code}\n" http://localhost:$p/actuator/health; done
```

Output shape:
```
NAME             STATUS
mysql-user       Up (healthy)
kafka            Up (healthy)
...
port 8080: 200      ← gateway UP
port 8081: 200      ← user-service UP
port 8083: 000      ← nothing on 8083 (order-service down)
port 8095: 302      ← auth-server up (login redirect is normal)
```

**Read the numbers as:** `200` = fully healthy, `302` = alive but redirecting (fine for auth-server), `000` = nothing listening, `404` = something's there but health endpoint missing.

---

## Process management — port conflicts, killing zombies

```bash
# What's listening on a port? (order-svc=8083, user=8081, product=8082, etc.)
lsof -i :8083

# Kill by PID (from lsof output)
kill 24702
kill -9 24702         # force-kill if regular kill doesn't work

# One-liner: kill whatever owns a port
lsof -ti :8083 | xargs kill

# Verify it's free
lsof -i :8083         # should return NOTHING

# What ALL Spring services are running right now?
for p in 8080 8081 8082 8083 8090 8091 8095 8761; do
  s=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:$p/actuator/health 2>/dev/null)
  echo "port $p → $s"
done
# 200 = healthy | 000 = nothing there | 404 = something there, wrong path
```

## Docker container management

```bash
# Where am I? See all containers (running + stopped)
docker ps -a

# Just names + status + ports (cleaner)
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'

# Containers from OUR compose only
cd /Users/bhanupratap/My/my-microservices
docker compose ps

# Stop + REMOVE all compose containers (needed after config changes to
# recreate with new settings — plain 'stop' won't reload env vars)
docker compose down

# Nuclear: also wipe data volumes
docker compose down -v

# Bring back only the infra pieces
docker compose up -d mysql-user mysql-product mysql-auth kafka zipkin

# Peek inside a container (logs)
docker compose logs -f kafka          # tail live (Ctrl+C stops the tail, NOT the container)
docker compose logs --tail 50 mysql-user

# Run a command INSIDE a container
docker exec kafka env | grep KAFKA    # inspect env vars
docker exec -it mysql-user mysql -uroot -ppass1234 userdb   # interactive shell
```

## Kafka inspection

```bash
# List all topics
docker exec kafka kafka-topics.sh --bootstrap-server localhost:9092 --list

# What is Kafka advertising to clients?
docker exec kafka kafka-broker-api-versions.sh --bootstrap-server localhost:9092 2>&1 | head -3

# Peek at latest messages on a topic (Ctrl+C to stop)
docker exec kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic user.registered --from-beginning --max-messages 5

# Publish a manual message (for DLQ / testing)
docker exec -it kafka kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic user.registered
# Type message, Enter, Ctrl+D to end

# Consumer groups + lag
docker exec kafka kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list
docker exec kafka kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group notification-service --describe

# Reset a consumer group offset (to reprocess from start)
docker exec kafka kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group notification-service --reset-offsets --to-earliest \
  --topic user.registered --execute
```

## MySQL inspection

```bash
# Quick "does it respond?" check
docker exec mysql-user mysql -uroot -ppass1234 -e "SHOW DATABASES;"

# Interactive shell
docker exec -it mysql-user mysql -uroot -ppass1234 userdb

# One-shot query — outbox pattern
docker exec mysql-user mysql -uroot -ppass1234 userdb -e \
  "SELECT id, aggregate_type, destination, status, created_at, sent_at FROM outbox_events ORDER BY created_at DESC LIMIT 10;"

# One-shot query — users
docker exec mysql-user mysql -uroot -ppass1234 userdb -e \
  "SELECT id, name, email FROM user;"

# Show tables in a database
docker exec mysql-user mysql -uroot -ppass1234 userdb -e "SHOW TABLES;"
```

## Building + running Java services

```bash
# Full reactor build (all modules) — sanity check after config changes
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.0.2.jdk/Contents/Home \
  ./api-gateway/mvnw -DskipTests clean install

# Build ONE module + its dependencies
./api-gateway/mvnw -pl notification -am -DskipTests clean install

# Just compile (fastest — no jar packaging)
cd notification
./mvnw compile

# Start a Spring Boot service (foreground — occupies terminal)
cd /Users/bhanupratap/My/my-microservices/eureka-server
./mvnw spring-boot:run
# Ctrl+C to stop

# Check which endpoints an actuator exposes
curl -s http://localhost:8080/actuator | jq '._links | keys'

# Dep tree of one module (to catch conflicts / see what's actually pulled in)
./api-gateway/mvnw -f notification/pom.xml dependency:tree
```

## Java env

```bash
# See which Java versions are installed
/usr/libexec/java_home -V

# Set JAVA_HOME for current shell
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.0.2.jdk/Contents/Home

# Make it permanent (add to ~/.zshrc)
echo 'export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.0.2.jdk/Contents/Home' >> ~/.zshrc
```

## Config file gotchas — finding & fixing

```bash
# Find all Spring config files in the project
find . -name "application*.yml" -o -name "application*.properties" | grep -v target

# Check for BOTH .yml AND .properties in one service (bad — one silently overrides the other)
find . -path "*/src/main/resources/application.properties" | grep -v target

# Find hardcoded ports
grep -rn "server.port\|server:" --include="application*" . | grep -v target

# Verify a JDBC URL includes MySQL 8 required flags
grep -rn "jdbc:mysql" --include="application*" . | grep -v allowPublicKeyRetrieval
# → anything printed here is missing the flag and will fail to connect
```

## Zipkin / tracing

```bash
# Zipkin UI
open http://localhost:9411

# Query API — recent traces
curl -s 'http://localhost:9411/api/v2/traces?limit=5&lookback=3600000' | jq

# Search by service name
curl -s 'http://localhost:9411/api/v2/traces?serviceName=order-service&limit=3' | jq
```

## Debugging URLs — keep these open

## Debugging URLs — keep these open

| Tab | URL |
|---|---|
| Eureka dashboard | http://localhost:8761 |
| Zipkin | http://localhost:9411 |
| Gateway routes | http://localhost:8080/actuator/gateway/routes |
| Gateway health | http://localhost:8080/actuator/health |
| Order health (breaker state) | http://localhost:8083/actuator/health |
| Order retry events | http://localhost:8083/actuator/retryevents |
| Order breaker events | http://localhost:8083/actuator/circuitbreakerevents |
| Order bulkhead events | http://localhost:8083/actuator/bulkheadevents |

## Health check — one command

```bash
for p in 8080 8081 8082 8083 8091 8095 8761; do
  s=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:$p/actuator/health)
  echo "port $p: $s"
done
```

Expected: all `200`.

## Eureka registration check

```bash
curl -s http://localhost:8761/eureka/apps -H "Accept: application/json" | jq '.applications.application[].name'
```

Expected: `API-GATEWAY`, `USER-SERVICE`, `PRODUCT-SERVICE`, `ORDER-SERVICE`, `PAYMENT-SERVICE`.

## Kafka topic peek

```bash
# List topics
docker exec kafka kafka-topics.sh --bootstrap-server localhost:9092 --list

# Peek at a specific topic
docker exec kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic user.registered --from-beginning --max-messages 5

# Consumer groups
docker exec kafka kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list

# Lag per group
docker exec kafka kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group notification-service --describe
```

## MySQL peek

```bash
# outbox
docker exec mysql-user mysql -uroot -ppass1234 userdb -e \
  "SELECT id, aggregate_type, destination, status, created_at, sent_at FROM outbox_events ORDER BY created_at DESC LIMIT 10;"

# users
docker exec mysql-user mysql -uroot -ppass1234 userdb -e \
  "SELECT id, name, email FROM user;"
```

## First real request — golden path

```bash
curl -X POST http://localhost:8081/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice","email":"alice@example.com","password":"secret123"}'
```

Then verify all 6 stages fired:

1. ✅ 201 response with user body
2. ✅ user-service log: "Publishing UserRegisteredEvent"
3. ✅ Outbox row inserted (query above)
4. ✅ Wait 500ms, outbox row → status=SENT
5. ✅ Kafka `user.registered` topic has one message
6. ✅ notification log: "Received UserRegisteredEvent"

If any stage fails, the exact spot tells you where to look. Add findings to [troubleshooting.md](troubleshooting.md).

## Resilience4j smoke test

```bash
# 1. Baseline: hit order — should succeed
curl -X POST http://localhost:8083/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"quantity":2}'

# 2. Kill product-service (stop it in IDE / kill the process)

# 3. Hammer order-service — first few slow, then instant fallback
for i in 1 2 3 4 5 6 7; do
  time curl -s -X POST http://localhost:8083/api/v1/orders \
    -H "Content-Type: application/json" \
    -d '{"productId":1,"quantity":2}'
  echo
done

# 4. Watch breaker trip
curl -s http://localhost:8083/actuator/health | jq '.components.circuitBreakers'
```

## DLQ smoke test

```bash
# 1. Publish garbage directly to a consumed topic
docker exec -it kafka kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic user.registered
# Type: not-a-real-event
# Ctrl+D

# 2. Wait ~5s (3 retries × backoff)

# 3. Check DLT topic
docker exec kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic user.registered.DLT --from-beginning --max-messages 1
```
