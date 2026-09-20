# 01 — Startup runbook

Order matters. Each phase depends on the previous being healthy.

## Why start in phases (dependency chain)

Microservices don't start in random order. Every service has dependencies. If you start something before its dependency is ready, it crashes on boot with "connection refused".

### The rule

> **Start bottom-up, stop top-down.**
> Deepest dependency first, then things that depend on it. Reverse for shutdown.

### The dependency stack for THIS project

```
                    Layer 1 — INFRASTRUCTURE (Docker)
                    ┌────────────┐  ┌────────┐  ┌────────┐
                    │ MySQL x3   │  │ Kafka  │  │ Zipkin │
                    └─────▲──────┘  └───▲────┘  └───▲────┘
                          │             │           │
                    ┌─────┴─────────────┴───────────┴────────┐
                    │  Layer 2 — SUPPORT SERVICES            │
                    │  Eureka → Auth-server → Gateway        │
                    └──────────────▲─────────────────────────┘
                                   │
                    ┌──────────────┴─────────────────────────┐
                    │  Layer 3 — DOMAIN SERVICES             │
                    │  user, product, order, payment, notif  │
                    └────────────────────────────────────────┘
```

### Why infrastructure first

- **Cheap and fast to start** — Docker containers boot in 5-15s (parallel).
- **Slow-to-connect** — MySQL takes 10s to be ready for connections; Kafka ~5s.
- **Zero retry logic in apps** — Spring Boot tries to connect ONCE on startup. Fails = crash.

If we started apps first, every app would fail with "Connection refused", we'd start MySQL, then have to restart every app manually. Wasted time.

### Why the specific Layer 2 order

| Order | Service | Reason |
|---|---|---|
| 1 | Eureka | Everyone will register with it. Must exist first. |
| 2 | Auth-server | Gateway needs its JWKS (public key) to validate JWTs. |
| 3 | Gateway | Depends on both above. |

### Why Layer 3 can start in parallel

Domain services (user, product, order, payment, notification) don't depend on each other for STARTUP. They only talk to each other for RUNTIME requests. So once Layer 2 is up, start them all together.

### Shutdown = reverse order

```
   Stop:  Layer 3  →  Layer 2  →  Layer 1
          (apps)     (support)   (infra)
```

Why? Because stopping the gateway first means no new requests arrive → in-flight requests can drain → then you can safely stop the services they depend on.

### What automates this in real environments

You do this manually now to *feel* the dependency chain. In real deployments:

- **Docker Compose** — `depends_on: [condition: service_healthy]` (already wired in this project's `docker-compose.yml`). `docker compose up -d --build` starts everything in the right order.
- **Kubernetes** — init containers, readiness probes, `startupProbe`.
- **Systemd (bare metal)** — `After=`, `Requires=` in unit files.

The concept is the same everywhere. Manual practice = intuition later.

---

## One-time prep

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.0.2.jdk/Contents/Home
# add to ~/.zshrc to make permanent

cd /Users/bhanupratap/My/my-microservices
./api-gateway/mvnw clean install -DskipTests   # sanity build, one-off
```

Expected: `BUILD SUCCESS` for all 11 modules.

---

## Phase 1 — Infrastructure

### The command (word-by-word)

```bash
docker compose up -d mysql-user mysql-product mysql-auth kafka zipkin
```

| Part | Meaning |
|---|---|
| `docker compose` | The compose CLI. Reads `docker-compose.yml` in current dir. |
| `up` | Create + start containers. Pulls images if missing. |
| `-d` | Detached mode — runs in background, gives terminal back. Without `-d` logs stream to your terminal and Ctrl+C stops them. |
| `mysql-user mysql-product mysql-auth kafka zipkin` | Which services to start (names come from `services:` block in yml). Naming these 5 explicitly = infra only, no Java apps. |

If you just run `docker compose up -d` (no service names), Docker tries to start **all 12** services including the Java apps — building their Docker images (~10 min first time). We don't want that. Java apps we start manually next, one at a time, to watch their logs.

### Then check status

```bash
docker compose ps
```

**Wait until:** all show `(healthy)` — ~15s for MySQL, ~10s for Kafka. Zipkin has no healthcheck, `Up` is fine.

### Verify each is actually working

```bash
# MySQL — each should list its db
docker exec mysql-user mysql -uroot -ppass1234 -e "SHOW DATABASES" | grep userdb
docker exec mysql-product mysql -uroot -ppass1234 -e "SHOW DATABASES" | grep productdb
docker exec mysql-auth mysql -uroot -ppass1234 -e "SHOW DATABASES" | grep authdb

# Kafka
docker exec kafka kafka-topics.sh --bootstrap-server localhost:9092 --list
# empty is fine — topics auto-create on first publish

# Zipkin
open http://localhost:9411
# UI loads, empty
```

### Useful variants once you're comfortable

```bash
# See logs (last 20 lines) for one service
docker compose logs --tail 20 mysql-user

# Tail logs live (Ctrl+C only stops the tail, NOT the container)
docker compose logs -f kafka

# Stop one container (keeps data volume)
docker compose stop mysql-user

# Start it again
docker compose start mysql-user

# Remove containers (keeps data)
docker compose down

# NUCLEAR: remove containers AND data volumes
docker compose down -v
```

### Known gotcha — Bitnami Kafka image

If Kafka fails with `manifest for bitnami/kafka:... not found`:

Bitnami moved their public catalog to a paid tier in August 2025. The free public `bitnami/kafka` image no longer exists.

**Fix:** in `docker-compose.yml`, change:
```yaml
image: bitnami/kafka:latest   # broken
```
to:
```yaml
image: bitnamilegacy/kafka:3.7   # same bits, moved to legacy repo (free, frozen)
```

Long-term: migrate to `apache/kafka` (official) — but env-var names differ, needs compose reconfig. See [troubleshooting.md](troubleshooting.md).

---

## Phase 2 — Support services

Start in this order. Wait for each to be UP before starting the next.
Each command occupies its own terminal tab — leave the process running.

### About `mvnw`

Every module folder (eureka-server, auth-server, api-gateway, user-service, etc.)
contains its own `mvnw` script — they're all identical. Two styles work:

**Style B — from the service folder (used below, cleanest for single service):**
```bash
cd <service-folder>
./mvnw spring-boot:run
```

**Style A — from project root with `-pl`:**
```bash
cd /Users/bhanupratap/My/my-microservices
./<any-service>/mvnw -pl <target-service> spring-boot:run
```

Both do the same thing. Style B reads more naturally when starting one service at a time.

### 2a — Eureka

```bash
cd /Users/bhanupratap/My/my-microservices/eureka-server
./mvnw spring-boot:run
```

**Watch for:**
```
Tomcat started on port(s): 8761 (http)
Started EurekaServerApplication in X seconds
```

**Verify from another terminal:**
- `curl -s http://localhost:8761/actuator/health` → `{"status":"UP"}`
- Browser http://localhost:8761 → Eureka dashboard loads, "no instances" is normal

### 2b — Auth-server

**FIRST BOOT ONLY** — flip `auth-server/src/main/resources/application.properties`:
```
spring.jpa.hibernate.ddl-auto=update
```
After it boots once (creates tables), flip back to `validate`.

```bash
cd /Users/bhanupratap/My/my-microservices/auth-server
./mvnw spring-boot:run
```

**Watch for:**
```
Tomcat started on port(s): 8095 (http)
Started AuthServerApplication in X seconds
```
Plus Hibernate log lines creating `oauth2_authorization` and related tables (first boot only).

**Verify:**
- `curl -s http://localhost:8095/.well-known/openid-configuration | head -c 300`
  → JSON starting with `{"issuer":"http://localhost:8095",...`

### 2c — API Gateway

```bash
cd /Users/bhanupratap/My/my-microservices/api-gateway
./mvnw spring-boot:run
```

**Watch for:**
```
Netty started on port 8080
Started ApiGatewayApplication in X seconds
```
Note it says **Netty**, not Tomcat — gateway is reactive, that's correct.

You should also see registration with Eureka:
```
DiscoveryClient_API-GATEWAY/... registration status: 204
```

**Verify:**
- `curl -s http://localhost:8080/actuator/health` → `{"status":"UP",...}`
- `curl -s http://localhost:8080/actuator/gateway/routes | head -c 500`
  → JSON array with `user-service`, `product-service`, `order-service`
- Refresh http://localhost:8761 → `API-GATEWAY` appears under "instances currently registered"

---

## Phase 3 — Domain services

Start these in parallel — they don't depend on each other for startup.
Each in its own terminal tab.

### user-service
```bash
cd /Users/bhanupratap/My/my-microservices/user-service
./mvnw spring-boot:run
```

### product-service
```bash
cd /Users/bhanupratap/My/my-microservices/product-service
./mvnw spring-boot:run
```

### order-service
```bash
cd /Users/bhanupratap/My/my-microservices/order-service
./mvnw spring-boot:run
```

### payment-service
```bash
cd /Users/bhanupratap/My/my-microservices/paymentservice
./mvnw spring-boot:run
```

### notification
```bash
cd /Users/bhanupratap/My/my-microservices/notification
./mvnw spring-boot:run
```

**Verify all:**
- Eureka dashboard shows all 5 registered
- Each service `/actuator/health` returns UP:
  ```bash
  for p in 8081 8082 8083 8091; do
    echo -n "$p: "; curl -s http://localhost:$p/actuator/health | jq -r .status
  done
  ```

---

## Full-stack docker alternative (once you're confident)

```bash
docker compose up -d --build       # builds all Dockerfiles, then runs
docker compose ps                  # watch health
docker compose logs -f api-gateway # tail one service
```

---

## Shutdown

```bash
docker compose down                # stop containers, keep volumes
docker compose down -v             # nuke everything including DB data
```

---

## Common startup gotchas

Fill in as you hit them. Move stubborn ones to [troubleshooting.md](troubleshooting.md).

- **`Port 3306 already in use`** — you have a local MySQL running. Stop it or change compose ports.
- **`ddl-auto=validate` fails on first boot of auth-server** — see 2b above.
- **user-service startup fails: `LazyInitializationException`** — devtools + JPA quirk; disable devtools if it persists.
- **Kafka listener not consuming** — check `spring.cloud.function.definition` includes your bean name.
