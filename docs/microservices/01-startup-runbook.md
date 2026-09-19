# 01 — Startup runbook

Order matters. Each phase depends on the previous being healthy.

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

```bash
docker compose up -d mysql-user mysql-product mysql-auth kafka zipkin
docker compose ps
```

**Wait until:** all show `(healthy)` — ~15s for MySQL, ~10s for Kafka.

**Verify each:**

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

---

## Phase 2 — Support services

Start in this order. Wait for each to be UP before starting the next.

### 2a — Eureka

```bash
cd eureka-server
../api-gateway/mvnw spring-boot:run
```

Verify:
- Console: `Started EurekaServerApplication`
- Browser: http://localhost:8761 → Eureka dashboard, "no instances" is normal

### 2b — Auth-server

**FIRST BOOT ONLY** — flip `auth-server/src/main/resources/application.properties`:
```
spring.jpa.hibernate.ddl-auto=update
```
After it boots once (creates tables), flip back to `validate`.

```bash
cd auth-server
../api-gateway/mvnw spring-boot:run
```

Verify:
- Console: `Started AuthServerApplication`
- `curl http://localhost:8095/.well-known/openid-configuration | jq .issuer`
  → returns `"http://localhost:8095"`

### 2c — API Gateway

```bash
cd api-gateway
./mvnw spring-boot:run
```

Verify:
- Console: `Netty started on port 8080`
- Eureka dashboard: `API-GATEWAY` appears
- `curl http://localhost:8080/actuator/gateway/routes | jq '.[] | .route_id'`
  → shows `user-service`, `product-service`, `order-service`

---

## Phase 3 — Domain services

Start these in parallel — they don't depend on each other.

### user-service
```bash
cd user-service && ../api-gateway/mvnw spring-boot:run
```

### product-service
```bash
cd product-service && ../api-gateway/mvnw spring-boot:run
```

### order-service
```bash
cd order-service && ../api-gateway/mvnw spring-boot:run
```

### payment-service
```bash
cd paymentservice && ../api-gateway/mvnw spring-boot:run
```

### notification
```bash
cd notification && ../api-gateway/mvnw spring-boot:run
```

**Verify all:**
- Eureka dashboard shows all 5
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
