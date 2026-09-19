# Flow — User registration

## The request

```bash
curl -X POST http://localhost:8081/api/v1/users \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: demo-user-reg-001" \
  -d '{"name":"Alice","email":"alice@example.com","password":"secret123"}'
```

## Expected outcome

- 201 Created response with `{id, name, email}` (password NOT in response)
- User row in `mysql-user.userdb.user`
- Outbox row in `mysql-user.userdb.outbox_events` — briefly PENDING then SENT
- Kafka message on `user.registered` topic
- Log line in notification-service showing the event was consumed
- Full trace visible in Zipkin

## Step-by-step trace

<!-- Fill in with actual log lines as you run it -->

### 1. Gateway (skipping — direct hit to 8081 for now)

_TODO: retry via gateway on port 8080 with a JWT once auth flow is fully wired._

### 2. UserController receives the request

Expected log (user-service):
```
INFO  [user-service,<traceId>,<spanId>,demo-user-reg-001] c.e.u.controller.UserController : ...
```

### 3. UserRegistrationService (transactional boundary begins)

Expected: single DB transaction wraps both INSERTs.

### 4. Outbox row inserted (PENDING)

Query immediately:
```bash
docker exec mysql-user mysql -uroot -ppass1234 userdb -e \
  "SELECT id, status, created_at FROM outbox_events ORDER BY created_at DESC LIMIT 1;"
```

### 5. Response sent (201)

### 6. OutboxRelay drains (up to 500ms later)

Expected log:
```
DEBUG ... OutboxRelay : Draining 1 outbox events
```

### 7. Kafka message published

```bash
docker exec kafka kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic user.registered --from-beginning --max-messages 1
```

### 8. notification-service consumes

Expected log (notification):
```
INFO  [notification,<sameTraceId>,<newSpanId>,demo-user-reg-001] ... UserRegisteredHandler : Received UserRegisteredEvent
```

## Zipkin trace

Open http://localhost:9411 → find trace `<traceId>` → screenshot / describe timeline here.

## What could go wrong

- **Outbox row stuck PENDING** — poller not running (`@EnableScheduling` missing?), or Kafka down.
- **notification doesn't consume** — `spring.cloud.function.definition` in notification/application.yml doesn't include `userRegistered`.
- **Trace ID doesn't propagate to notification** — Micrometer Kafka instrumentation missing.
- **Correlation ID missing from notification logs** — MDC propagation across Kafka not automatic (feature gap; document it).
