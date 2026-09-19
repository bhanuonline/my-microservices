# Transactional Outbox Pattern

## The problem it solves

**Dual-write:** you cannot atomically write to DB AND publish to Kafka. If either fails, state diverges. This is the #1 pitfall of naive event-driven design.

## How it works

```
   ONE TX:  INSERT INTO users
            INSERT INTO outbox_events  ← same DB, same TX

   Meanwhile:
     Poller (every 500ms):
       SELECT PENDING outbox rows FOR UPDATE SKIP LOCKED
       For each: publish to Kafka → mark SENT
```

## Guarantees

- **At-least-once delivery.** Never at-most-once (message can't be lost).
- **Ordered per aggregate** (if you use the aggregate ID as Kafka message key).
- **NOT exactly-once.** Consumers must be idempotent to deduplicate.

## How it's wired in THIS project

- **Entity:** `common-lib/.../outbox/OutboxEvent.java` — reusable
- **Repo:** `common-lib/.../outbox/OutboxEventRepository.java`
- **Writer:** `user-service/.../outbox/OutboxWriter.java` — called from `@Transactional` service
- **Relay:** `user-service/.../outbox/OutboxRelay.java` — `@Scheduled(fixedDelay=500)`
- **JPA scan:** `UserServiceApplication` has `@EntityScan` + `@EnableJpaRepositories` widened to include common-lib
- **Scheduling:** `@EnableScheduling` on main class

## How to observe it running

```bash
# 1. Register a user
curl -X POST http://localhost:8081/api/v1/users -H "Content-Type: application/json" \
  -d '{"name":"Alice","email":"a@a.com","password":"secret123"}'

# 2. Immediately query the outbox — see PENDING row
docker exec mysql-user mysql -uroot -ppass1234 userdb -e \
  "SELECT id, status, attempt_count, created_at, sent_at FROM outbox_events ORDER BY created_at DESC LIMIT 5;"

# 3. Wait 1s, re-query → SENT
```

**Killer demo — proves resilience:**
```bash
docker compose stop kafka
# Register another user — succeeds (returns 201)
# Outbox row stays PENDING
docker compose start kafka
# Wait a few seconds — poller catches up, row → SENT
```

## Common failure modes

- Publish inside `@Transactional` boundary (defeats the point — you already have the dual-write).
- Publish OUTSIDE any tx (fire-and-forget), but forget the outbox → back to dual-write.
- Poller crashes between "publish OK" and "mark SENT" → duplicate delivery on next run. Consumer must dedup.
- Outbox table grows unbounded → periodic cleanup job for SENT rows.

## Interview talking points

- At-least-once → consumer idempotency is REQUIRED, not optional.
- CDC-based outbox (Debezium) is the "no polling, zero latency" upgrade.
- Idempotency key = the outbox `event.id` (UUID), used as Kafka message key.
- Trade-off: extra DB writes per event. Alternative: transactional messaging (Kafka transactions), but complex and doesn't span DB + Kafka.
