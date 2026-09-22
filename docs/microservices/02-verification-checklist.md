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

### Container credentials — same for all 3

| DB | Container name | Host port | User | Password | Database |
|---|---|---|---|---|---|
| user-service | `mysql-user` | **3307** | `root` | `pass1234` | `userdb` |
| product-service | `mysql-product` | **3308** | `root` | `pass1234` | `productdb` |
| auth-server | `mysql-auth` | **3309** | `root` | `pass1234` | `authdb` |

Note: use **host ports** (3307/8/9) from your Mac. Inside the docker network, MySQL always runs on 3306.

### Option A — docker exec (fastest, no install needed)

Runs `mysql` CLI inside the container. You never leave your terminal.

```bash
# Quick "does it respond?" check
docker exec mysql-user mysql -uroot -ppass1234 -e "SHOW DATABASES;"

# Interactive shell (Ctrl+D or 'exit' to leave)
docker exec -it mysql-user mysql -uroot -ppass1234 userdb
docker exec -it mysql-product mysql -uroot -ppass1234 productdb
docker exec -it mysql-auth mysql -uroot -ppass1234 authdb

# One-shot query — outbox pattern
docker exec mysql-user mysql -uroot -ppass1234 userdb -e \
  "SELECT id, aggregate_type, destination, status, created_at, sent_at FROM outbox_events ORDER BY created_at DESC LIMIT 10;"

# One-shot query — users
docker exec mysql-user mysql -uroot -ppass1234 userdb -e \
  "SELECT id, name, email FROM user;"

# Show tables in a database
docker exec mysql-user mysql -uroot -ppass1234 userdb -e "SHOW TABLES;"
```

**Word by word:**
- `docker exec` = run a command inside a running container
- `-it` = interactive + TTY (needed for the mysql> prompt)
- `mysql-user` = container name
- `mysql -u... -p...` = the command to run inside
- `userdb` = which database to `USE` on connect

### Option B — Local `mysql` client on your Mac

If you have `mysql` CLI installed (`brew install mysql-client`):

```bash
mysql -h 127.0.0.1 -P 3307 -uroot -ppass1234 userdb
mysql -h 127.0.0.1 -P 3308 -uroot -ppass1234 productdb
mysql -h 127.0.0.1 -P 3309 -uroot -ppass1234 authdb
```

### Option C — GUI tool (DBeaver / TablePlus / DataGrip / MySQL Workbench)

Create a new MySQL connection with these settings:

- **Host:** `localhost`
- **Port:** `3307` (or 3308 / 3309)
- **User:** `root`
- **Password:** `pass1234`
- **Database:** `userdb` (or productdb / authdb)

**⚠️ Extra flags required** — same MySQL 8 auth issue we hit earlier. In the client's "Driver properties" / "Advanced" / "JDBC parameters" tab, add:

```
useSSL=false
allowPublicKeyRetrieval=true
```

Otherwise you get `Public Key Retrieval is not allowed` when connecting.

Test connection → Save. You get a browsable schema tree, query editor, table view.

### Common quick queries

```sql
-- once inside the mysql> prompt:
SHOW DATABASES;
USE userdb;
SHOW TABLES;
DESCRIBE user;                  -- table structure
SELECT * FROM user LIMIT 10;
SELECT COUNT(*) FROM user;

-- outbox status
SELECT status, COUNT(*) FROM outbox_events GROUP BY status;

-- see what's pending / stuck
SELECT * FROM outbox_events WHERE status='PENDING' ORDER BY created_at LIMIT 20;

-- exit
exit
-- (or Ctrl+D)
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

---

## Spring Boot debug commands

### 1. Enable remote debugger (attach IntelliJ / any debugger)

The most useful. Adds a JDWP listener so you can put breakpoints in IntelliJ.

```bash
# Ad-hoc — start with debug JVM args
./mvnw spring-boot:run \
  -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
```

**Word by word:**
- `-agentlib:jdwp=...` — Java Debug Wire Protocol agent
- `server=y` — the JVM listens for a debugger to connect
- `suspend=n` — start immediately; use `y` to break on `main()`
- `address=*:5005` — port 5005 on all interfaces

Then in IntelliJ: Run → Edit Configurations → + → Remote JVM Debug → `localhost:5005`.

**This project already has a `debug` Maven profile in every service's pom.** Just:
```bash
./mvnw spring-boot:run -Pdebug
```

**Debug port per service (from your poms — CORE microservices):**

| Service | Debug port |
|---|---|
| eureka-server | 5006 |
| auth-server | 5007 |
| api-gateway | 5008 |
| user-service | 5009 |
| product-service | 5010 |
| order-service | 5011 |
| payment-service | 5012 |
| notification | 5013 |

**Side/practice modules (NOT in main reactor — some collide with core ports):**

| Module | Debug port | Collides with |
|---|---|---|
| admin | 5009 | user-service |
| interview | 5008 | api-gateway |
| jwtAuthApp | 5011 | order-service |
| process | 5013 | notification |
| service | 5010 | product-service |
| algolia | 5014 | — |
| spring-security-apps | 5015 | — |

⚠️ If two services share a debug port and both use `-Pdebug`, the second will fail with `Address already in use`. Colliding modules are all side-projects — don't debug them at the same time as their core-service twin.

### 2. Enable Spring's DEBUG-level startup logs (auto-config decisions)

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--debug"
# or in application.yml:
#   debug: true
```

Prints the **Condition Evaluation Report** — every auto-config class + whether it applied + why. Killer for "why isn't Spring wiring this bean?"

### 3. Log level for a specific package

```yaml
# application.yml (persistent)
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.springframework.security: TRACE
    org.springframework.cloud.gateway: TRACE
    com.example: DEBUG
```

Or per-run:
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--logging.level.org.hibernate.SQL=DEBUG"
```

### 4. Change log level at RUNTIME — no restart

```bash
# Current level
curl http://localhost:8081/actuator/loggers/com.example.userservice

# Set to DEBUG
curl -X POST http://localhost:8081/actuator/loggers/com.example.userservice \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel":"DEBUG"}'

# Reset
curl -X POST http://localhost:8081/actuator/loggers/com.example.userservice \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel":null}'
```
Requires `loggers` in `management.endpoints.web.exposure.include` (already exposed on user-service).

### 5. Inspect running state via actuator

```bash
# All exposed endpoints
curl -s http://localhost:8081/actuator | jq

# Every resolved property + its source (yml, env var, etc.)
curl -s http://localhost:8081/actuator/env | jq

# One property — value + WHERE it came from
curl -s http://localhost:8081/actuator/env/server.port | jq

# @ConfigurationProperties beans
curl -s http://localhost:8081/actuator/configprops | jq

# Bean names in the app context
curl -s http://localhost:8081/actuator/beans | jq '.contexts.application.beans | keys' | head -30

# Detailed health
curl -s http://localhost:8081/actuator/health | jq

# Thread dump (for hung apps)
curl -s http://localhost:8081/actuator/threaddump | jq

# Heap dump (downloads .hprof to open in Eclipse MAT / VisualVM)
curl -X POST http://localhost:8081/actuator/heapdump -o heap.hprof

# Metrics
curl -s http://localhost:8081/actuator/metrics | jq
curl -s http://localhost:8081/actuator/metrics/jvm.memory.used | jq
curl -s http://localhost:8081/actuator/metrics/http.server.requests | jq
```

### 6. Startup timing — see what's slow to boot

```yaml
management:
  endpoints:
    web:
      exposure:
        include: startup
spring:
  application:
    admin:
      enabled: true
```
After startup:
```bash
curl -s http://localhost:8081/actuator/startup | \
  jq '.timeline.events | sort_by(-.duration) | .[0:20]'
```

### 7. Pattern-specific debug endpoints (already exposed in this project)

```bash
# Resilience4j
curl -s http://localhost:8083/actuator/circuitbreakerevents/productClient | jq
curl -s http://localhost:8083/actuator/retryevents/productClient | jq
curl -s http://localhost:8083/actuator/bulkheadevents/productClient | jq

# Gateway routes (live table)
curl -s http://localhost:8080/actuator/gateway/routes | jq

# Eureka registrations
curl -s http://localhost:8761/eureka/apps -H "Accept: application/json" | jq
```

### 8. JVM introspection (any running Java process, needs JDK on your Mac)

```bash
# Find the Spring PID
jps -lv | grep user-service

# Full thread dump to stdout
jstack <PID>

# Live heap histogram — object counts by class
jmap -histo <PID> | head -30

# Full heap dump to file
jmap -dump:live,format=b,file=user-svc.hprof <PID>

# JVM version + args
jinfo <PID>

# GC stats live (every 1s for 10 iterations)
jstat -gc <PID> 1s 10
```

### 9. IntelliJ debugging shortcuts

- **Run → Attach to Process** — debug an already-running JVM (no need to relaunch)
- **Debug icon** in gutter next to `main()` — starts app in debug mode
- **F9** resume | **F7** step into | **F8** step over | **Shift+F8** step out
- **Alt+F8** — evaluate expression at breakpoint
- **Ctrl+Shift+F8** — breakpoints dialog; enable **exception breakpoints** to break on any throw
- **Right-click breakpoint → Condition** — break only when e.g. `orderId.equals("abc-123")`

### 10. Live config reload — no restart

For property changes only (not class/schema changes):

```bash
# Edit application.yml → save → then trigger reload:
curl -X POST http://localhost:8081/actuator/refresh
# → JSON array of the keys that changed
```
Only affects `@RefreshScope` beans. See `RefreshDemoController` for a working example.

### Cheat-sheet — "I want to..."

| I want to... | Command |
|---|---|
| Attach IntelliJ debugger | `./mvnw spring-boot:run -Pdebug` then attach to :5005 (varies per service) |
| See why Spring wired / didn't wire a bean | Start with `--debug`, read Condition Evaluation Report |
| Change log level without restart | `POST /actuator/loggers/<pkg>` with `configuredLevel` |
| See a property's current value + source | `GET /actuator/env/<property.name>` |
| Reload `@Value` after yml edit | `POST /actuator/refresh` (bean must be `@RefreshScope`) |
| Trace slow startup | Expose `startup` endpoint, then query it |
| Diagnose thread starvation / deadlock | `GET /actuator/threaddump` or `jstack <PID>` |
| Diagnose memory leak | `POST /actuator/heapdump`, open in Eclipse MAT |
| See circuit breaker state | `GET /actuator/health` → `.components.circuitBreakers` |
