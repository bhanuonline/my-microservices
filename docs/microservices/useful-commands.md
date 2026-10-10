# Useful Commands — Everyday Runbook

Living reference. Grep for the thing you want. Every command here was built
alongside Tier 1–4 and is kept in sync as new features ship.

Companion Postman collection: `postman/microservices.postman_collection.json`
+ `postman/microservices.postman_environment.json`.

- [1. Stack lifecycle](#1-stack-lifecycle)
- [2. Health and discovery](#2-health-and-discovery)
- [3. Auth (basic + OAuth2 JWT)](#3-auth-basic--oauth2-jwt)
- [4. Orders (write side)](#4-orders)
- [5. Order search (CQRS read side)](#5-order-search-cqrs-read-side)
- [6. Products](#6-products)
- [7. Users](#7-users)
- [8. Tier 1 — idempotency, resilience](#8-tier-1--idempotency-resilience)
- [9. Tier 2 — DLQ admin, Debezium CDC](#9-tier-2--dlq-admin-debezium-cdc)
- [10. Tier 4 — saga admin, feature flags, SSE, GraphQL, gRPC](#10-tier-4--saga-admin-feature-flags-sse-graphql-grpc)
- [11. k6 load testing](#11-k6-load-testing)
- [12. Observability (Zipkin, Prometheus, Grafana, Loki)](#12-observability)
- [13. Kafka](#13-kafka)
- [14. Database probes](#14-database-probes)
- [15. Troubleshooting one-liners](#15-troubleshooting)

Conventions:
- Gateway at `http://localhost:8080`. Direct service ports listed in each section.
- Admin basic auth: `admin:admin123`. OAuth2 JWT examples assume `/oauth2/token` client credentials.

---

## 1. Stack lifecycle

```bash
# JDK 17 is required (project source=17)
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.0.2.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH

# Build everything (skip tests for speed)
mvn -DskipTests clean package

# Observability + messaging infra only
docker compose up -d zipkin prometheus loki promtail grafana \
                     schema-registry redis kafka

# App plane
docker compose up -d eureka-server auth-server api-gateway \
                     user-service product-service order-service \
                     payment-service notification

# CQRS read side (new in Tier 2 — separate Spring Boot)
cd order-query && mvn spring-boot:run &

# GraphQL BFF (new in Tier 4)
cd graphql-bff && mvn spring-boot:run &

# Stop everything
docker compose down

# Nuke volumes too (loses DB data)
docker compose down -v
```

---

## 2. Health and discovery

```bash
# Gateway health
curl http://localhost:8080/actuator/health

# Any service — same shape
curl http://localhost:8083/actuator/health   # order-service
curl http://localhost:8082/actuator/health   # product-service
curl http://localhost:8081/actuator/health   # user-service

# Eureka registry (JSON)
curl -H 'Accept: application/json' http://localhost:8761/eureka/apps

# Eureka UI
open http://localhost:8761
```

---

## 3. Auth (basic + OAuth2 JWT)

```bash
# Basic auth — admin / admin123. Works on /admin/** and gateway-proxied APIs.
curl -u admin:admin123 http://localhost:8080/actuator/info

# OAuth2 client-credentials — grab a JWT
TOKEN=$(curl -s -X POST http://localhost:8095/oauth2/token \
         -u my-client:secret \
         -d 'grant_type=client_credentials&scope=orders.read' | jq -r .access_token)
echo "$TOKEN"

# Use the token
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/orders/foo-123
```

---

## 4. Orders

```bash
# Create an order (Tier 1: idempotency-aware)
KEY=$(uuidgen)
curl -u admin:admin123 \
     -H "Content-Type: application/json" \
     -H "Idempotency-Key: $KEY" \
     -H "X-Correlation-Id: demo-$(date +%s)" \
     -X POST http://localhost:8080/api/v1/orders \
     -d '{"productId":1,"quantity":2}' -v 2>&1 | grep -i -E 'HTTP/|idempot|x-correlation'

# Replay the same request — should return same body + "Idempotency-Replay: true"
curl -u admin:admin123 \
     -H "Content-Type: application/json" \
     -H "Idempotency-Key: $KEY" \
     -X POST http://localhost:8080/api/v1/orders \
     -d '{"productId":1,"quantity":2}' -v 2>&1 | grep -i -E 'HTTP/|idempot'

# Get an order by id (replace with the id from above)
curl -u admin:admin123 http://localhost:8080/api/v1/orders/<orderId>
```

---

## 5. Order search (CQRS read side)

Direct to `order-query` on **8086** (not through gateway yet).

```bash
# All
curl http://localhost:8086/orders/search

# By status
curl 'http://localhost:8086/orders/search?status=CREATED'

# By product
curl 'http://localhost:8086/orders/search?productId=1'

# By id (returns the ES doc)
curl http://localhost:8086/orders/search/<orderId>
```

---

## 6. Products

```bash
# List
curl -u admin:admin123 http://localhost:8080/api/v1/products

# Get by id — first call hits DB, second comes from Redis cache (Tier 4)
curl -u admin:admin123 http://localhost:8080/api/v1/products/1
curl -u admin:admin123 http://localhost:8080/api/v1/products/1    # cache HIT

# Availability (used by order-service via Feign)
curl -u admin:admin123 http://localhost:8080/api/v1/products/1/availability

# Create
curl -u admin:admin123 -H "Content-Type: application/json" \
     -X POST http://localhost:8080/api/v1/products \
     -d '{"name":"Widget","description":"shiny","price":9.99,"stock":100}'
```

---

## 7. Users

```bash
# List (gateway rewrites /api/v1/users → /api/v2/users on user-service)
curl -u admin:admin123 http://localhost:8080/api/v1/users

# Me (requires a valid JWT)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/users/me

# Create — directly on user-service (public endpoint)
curl -X POST http://localhost:8081/api/v1/users/registerUser \
     -H "Content-Type: application/json" \
     -d '{"username":"alice","password":"secret","email":"a@b.c"}'
```

---

## 8. Tier 1 — idempotency, resilience

```bash
# Idempotency: see Section 4 for the full round-trip.

# Trigger the circuit breaker on user-service → product-service
docker compose stop product-service
for i in {1..20}; do
  curl -s -o /dev/null -w "%{http_code}\n" \
       -H "Authorization: Bearer $TOKEN" \
       http://localhost:8080/api/v1/users/with-products
done
# → first 2-3 fail (timeout+retry), rest 200 OK from fallback (empty list)

docker compose start product-service    # breaker returns to CLOSED after 30s cooldown

# Live resilience4j events
curl -u admin:admin123 http://localhost:8081/actuator/circuitbreakers | jq
curl -u admin:admin123 http://localhost:8081/actuator/circuitbreakerevents | jq '.circuitBreakerEvents[-5:]'
```

---

## 9. Tier 2 — DLQ admin, Debezium CDC

### DLQ

```bash
# List poison messages
curl -u admin:admin123 http://localhost:8080/admin/dlq
curl -u admin:admin123 'http://localhost:8080/admin/dlq?status=NEW'

# Inspect one
curl -u admin:admin123 http://localhost:8080/admin/dlq/1

# Replay (publishes back to original topic — consumer dedup catches dups)
curl -u admin:admin123 -X POST http://localhost:8080/admin/dlq/1/replay

# Acknowledge without replay (audit-only)
curl -u admin:admin123 -X POST http://localhost:8080/admin/dlq/1/ack
```

### Debezium CDC (switch outbox dispatcher from polling to streaming)

```bash
# Bring up CDC infra
docker compose up -d postgres-order kafka-connect

# Start order-service on the CDC profile (expects Postgres, disables OutboxRelay poller)
SPRING_PROFILES_ACTIVE=docker-cdc java -jar order-service/target/order-service-*.jar

# Register the connector
./observability/debezium/register-outbox-connector.sh

# Check status
curl -s http://localhost:8083/connectors/order-outbox-connector/status | jq
```

---

## 10. Tier 4 — saga admin, feature flags, SSE, GraphQL, gRPC

### Saga admin

```bash
# List in-flight sagas
curl -u admin:admin123 http://localhost:8080/admin/sagas
curl -u admin:admin123 'http://localhost:8080/admin/sagas?state=STARTED'

# Detail with full step history
curl -u admin:admin123 http://localhost:8080/admin/sagas/<uuid> | jq

# Force a compensation (fires a refund command, records it as COMPENSATION step)
curl -u admin:admin123 -X POST \
     'http://localhost:8080/admin/sagas/<uuid>/compensate?reason=stuck-manual'
```

### Feature flags

```bash
# List
curl -u admin:admin123 http://localhost:8080/admin/flags

# Create or update one
curl -u admin:admin123 -X PUT http://localhost:8080/admin/flags/new-payment-provider \
     -H "Content-Type: application/json" \
     -d '{
           "description":"shadow new provider",
           "enabled": true,
           "rulesJson":"{\"percentage\":25,\"tenantWhitelist\":[],\"userWhitelist\":[]}"
         }'

# Toggle on/off (keeps rules)
curl -u admin:admin123 -X POST http://localhost:8080/admin/flags/new-payment-provider/toggle
```

### SSE push

```bash
# Subscribe (keep the terminal open). Pipe to grep to visualise events as they land.
curl -N http://localhost:8099/notifications/stream/order-abc-123

# In another terminal: produce a payment.completed event
docker exec -it kafka kafka-console-producer.sh \
   --bootstrap-server localhost:9092 --topic payment.completed <<EOF
{"eventId":"$(uuidgen)","orderId":"order-abc-123","paymentId":"pmt-1","amount":9.99,"completedAt":"$(date -u +%FT%TZ)"}
EOF
```

### GraphQL BFF

```bash
# GraphiQL UI
open http://localhost:8087/graphiql

# One-liner query
curl -X POST http://localhost:8087/graphql \
     -H "Content-Type: application/json" \
     -d '{"query":"{ product(id: 1) { id name price } }"}'

# Nested — triggers DataLoader (if backend returns multiple orders)
curl -X POST http://localhost:8087/graphql \
     -H "Content-Type: application/json" \
     -d '{"query":"{ order(id: \"o-1\") { id status product { name price } } }"}'
```

### gRPC

```bash
# payment-service listens on :9091 (plaintext)
grpcurl -plaintext localhost:9091 list
grpcurl -plaintext localhost:9091 payment.PaymentService/Charge \
        -d '{"order_id":"o1","amount":"9.99","currency":"USD","idempotency_key":"k1"}'
```

---

## 11. k6 load testing

```bash
# Smoke (local k6)
k6 run load-tests/k6/orders.js

# Load, pushing to Prometheus
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
K6_PROMETHEUS_RW_TREND_AS_NATIVE_HISTOGRAM=true \
k6 run --out experimental-prometheus-rw -e SCENARIO=load load-tests/k6/orders.js

# Compose variant (no local k6 install)
docker compose --profile loadtest run --rm k6 \
   run -e SCENARIO=load --out experimental-prometheus-rw /scripts/orders.js

# Dashboard
open 'http://localhost:3000/d/k6-overview'
```

---

## 12. Observability

```bash
# Zipkin
open http://localhost:9411

# Prometheus
open http://localhost:9090
open http://localhost:9090/targets        # every service should be UP

# Grafana (admin/admin)
open http://localhost:3000

# Hot-reload Prometheus after editing observability/prometheus.yml
curl -X POST http://localhost:9090/-/reload

# Change a package log level at runtime without a restart (actuator loggers)
curl -u admin:admin123 -X POST http://localhost:8083/actuator/loggers/com.example.orderservice \
     -H "Content-Type: application/json" \
     -d '{"configuredLevel":"DEBUG"}'
```

### Useful PromQL

```promql
# RED per service
sum by (application) (rate(http_server_requests_seconds_count[1m]))
sum by (application) (rate(http_server_requests_seconds_count{status=~"5.."}[1m]))
histogram_quantile(0.95, sum by (application, le) (rate(http_server_requests_seconds_bucket[1m])))

# Resilience4j breaker state (0=CLOSED, 2=HALF_OPEN, 3=OPEN)
resilience4j_circuitbreaker_state

# JVM heap
sum by (application) (jvm_memory_used_bytes{area="heap"})

# k6 — load-test rate / tail
sum(rate(k6_http_reqs_total[30s]))
histogram_quantile(0.99, sum by (le) (rate(k6_http_req_duration_seconds_bucket[1m])))
```

### LogQL (Loki)

```logql
{svc="order-service"} | json | correlationId="<id>"
{svc="order-service", level="ERROR"}
sum by (svc) (rate({level="ERROR"}[5m]))
```

---

## 13. Kafka

```bash
# List topics
docker exec kafka kafka-topics.sh --bootstrap-server localhost:9092 --list

# Describe a topic (partitions, replicas, config)
docker exec kafka kafka-topics.sh --bootstrap-server localhost:9092 \
    --describe --topic order.created

# Tail from the beginning
docker exec kafka kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 --topic order.created --from-beginning --max-messages 10

# Produce a test event
docker exec -i kafka kafka-console-producer.sh \
    --bootstrap-server localhost:9092 --topic payment.completed <<'EOF'
{"eventId":"test-1","orderId":"o-1","paymentId":"pmt-1","amount":9.99,"completedAt":"2026-10-03T12:00:00Z"}
EOF

# Consumer group lag
docker exec kafka kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
    --describe --group order-service
```

### Schema Registry

```bash
# List subjects
curl http://localhost:8085/subjects

# Latest schema for a subject
curl http://localhost:8085/subjects/order.created-value/versions/latest | jq

# Change compatibility to BACKWARD (default)
curl -X PUT http://localhost:8085/config/order.created-value \
     -H "Content-Type: application/vnd.schemaregistry.v1+json" \
     -d '{"compatibility":"BACKWARD"}'
```

---

## 14. Database probes

```bash
# order-service — H2 console (default profile)
open 'http://localhost:8083/h2-console'
# JDBC URL: jdbc:h2:mem:orderdb;DB_CLOSE_DELAY=-1   user: sa   pass: (blank)

# MySQL — users, products, auth
docker exec -it mysql-user     mysql -uroot -ppass1234 userdb    -e "SHOW TABLES;"
docker exec -it mysql-product  mysql -uroot -ppass1234 productdb -e "SHOW TABLES;"
docker exec -it mysql-auth     mysql -uroot -ppass1234 authdb    -e "SHOW TABLES;"

# Postgres (docker-cdc profile)
docker exec -it postgres-order psql -U order -d orderdb -c "SELECT COUNT(*) FROM outbox_events;"

# Redis
docker exec -it redis redis-cli KEYS 'product-service:*'
docker exec -it redis redis-cli --scan --pattern 'rate-limit:*' | head
```

---

## 15. Troubleshooting

```bash
# Any service refuses to start / port in use
lsof -i :8080

# Follow logs for one container
docker compose logs -f order-service

# Clear all stuck Kafka consumer groups (dev only!)
docker exec kafka kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
    --list | xargs -I {} docker exec kafka kafka-consumer-groups.sh \
    --bootstrap-server localhost:9092 --delete --group {}

# Prometheus not scraping a service
curl http://localhost:9090/api/v1/targets | jq '.data.activeTargets[].labels'

# Grafana dashboard not showing — reload provisioning
docker exec grafana curl -s -X POST http://localhost:3000/api/admin/provisioning/dashboards/reload
```
