# 02 — Verification checklist

Copy-paste commands to check every component is truly working, not just "running".

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
