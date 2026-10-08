# Debugging flows with Postman

Three ordered sequences of requests that walk a single business event through
the whole system: HTTP request → gateway → service → outbox → Kafka → saga
reply → state transition → metrics. When something breaks in production, the
flows show you exactly which hop broke.

Import files:
- `postman/microservices.postman_collection.json` — collection
- `postman/microservices.postman_environment.json` — environment

Three new folders sit near the top:

```
F1. Flow — Happy order (gateway → saga → Kafka)
F2. Flow — Payment fails (saga FAILED)
F3. Flow — Notification fails → compensation (RefundCommand)
```

## Prereqs

```bash
docker compose up -d
# Wait for everything to be healthy. Minimum needed:
#   api-gateway auth-server order-service product-service
#   payment-service notification kafka kafka-ui prometheus grafana mailhog
```

Then import the collection + environment into Postman, select the
`my-microservices (local)` environment, open a flow folder, and click
**Run folder** OR just hit the requests top-to-bottom.

---

## Flow 1 — Happy order

```
┌─ Request path ────────────────────────────────────────────────────────┐
│                                                                       │
│  POST /oauth2/token           (auth-server :8095)                     │
│       │                                                               │
│       ▼                                                               │
│  POST /api/v1/orders          (gateway :8080 → order-service :8083)   │
│       │                                                               │
│       │  order-service:                                               │
│       │    OrderService.create()  → persist Order + outbox            │
│       │    OrderSagaOrchestrator.start() → PaymentCommand (Kafka)     │
│       ▼                                                               │
│  kafka-ui — peek payment.commands                                     │
│       │                                                               │
│       │  payment-service consumes → charges → publishes PaymentReply  │
│       ▼                                                               │
│  GET /admin/sagas/{sagaId} after ~5s                                  │
│       │  (saga = PAID)                                                │
│       │  orchestrator sends NotifyUserCommand                         │
│       ▼                                                               │
│  kafka-ui — peek notification.commands                                │
│       │                                                               │
│       │  notification consumes → publishes NotifyUserReply(success)   │
│       ▼                                                               │
│  Final saga = NOTIFIED, order = PAID                                  │
│       │                                                               │
│       ▼                                                               │
│  Grafana order-saga dashboard shows terminal{completed}++             │
└───────────────────────────────────────────────────────────────────────┘
```

### "If step N fails, look at..."

| Step | Fails →                               | Where to look                             |
|------|---------------------------------------|-------------------------------------------|
| 1    | 401                                   | auth-server up? `docker logs auth-server` |
| 1    | connection refused                    | compose stack not ready                   |
| 2    | 401                                   | token expired → re-run step 1             |
| 2    | 500                                   | `docker logs order-service` — likely Feign to product-service failed |
| 3    | kafka-ui unreachable                  | `docker compose logs kafka-ui`            |
| 3    | 0 messages                            | orchestrator never fired → see 2 500 above |
| 4    | state still STARTED                   | payment-service is down OR reply hasn't landed yet (wait longer) |
| 4    | 404 on `/admin/sagas/{id}`            | sagaId wasn't captured → check step 3 test output |
| 5    | 0 messages                            | payment reply came back FAIL → go to Flow 2 |
| 6    | state=FAILED                          | notification failed → go to Flow 3        |
| 7    | status still CREATED                  | reply handler didn't run `order.markPaid()` |

---

## Flow 2 — Payment fails

Two ways to trigger it:

```
Option A — Simulate payment-service outage
──────────────────────────────────────────
  docker compose stop paymentservice
  # Run Flow 2.
  # order-service's PaymentCommand consumer retries 3 times (per
  # application.yml default back-off), then routes to payment.commands.DLT.
  # Saga never gets a PaymentReply → times out indirectly as "stuck."
  # (This triggers SagaInFlightStuck after 15m — the alert you built.)

Option B — Force a success=false reply
──────────────────────────────────────
  Open http://localhost:8090 → topic payment.replies → Produce Message
  Key: (empty)
  Value: {"sagaId":"<grabbed from kafka-ui>","orderId":"<same>",
          "success":false,"failureReason":"insufficient_funds","paymentId":null}
```

Option B is the realistic path (payment-service is up, just rejects).
Option A simulates a hard outage and exercises the DLT.

### Walk

```
POST /orders → saga STARTED
       │
       │  (if Option A: payment-service never consumes)
       │  (if Option B: you manually produce the fail reply)
       ▼
Peek payment.commands.DLT (Option A only; empty in Option B)
       │
       ▼
Poll /admin/sagas/{id} for ~15s
       │
       ▼
state = FAILED, order = CANCELLED
       │
       ▼
Prometheus query confirms orders_saga_terminal_total{outcome="failed"}++
```

---

## Flow 3 — Notification fails → compensation

```
POST /orders → saga STARTED
       │
       │  payment-service replies success
       ▼
saga = PAID → NotifyUserCommand sent
       │
       │  Option A: docker compose stop notification
       │  Option B: produce NotifyUserReply(success=false) via kafka-ui
       ▼
orchestrator catches the fail → beginCompensation() → RefundCommand
       │
       ▼
Peek payment.commands.refund — RefundCommand with the saga's paymentId
       │
       ▼
state = FAILED (compensated), order = CANCELLED
       │
       ▼
Prometheus query confirms orders_saga_terminal_total{outcome="compensated"}++
```

The compensated outcome is importantly different from `failed`:
- `failed` = payment never happened; nothing to undo.
- `compensated` = payment DID happen; refund was fired. Customer's money moves.

The dashboard splits them for exactly this reason.

---

## kafka-ui tips

- UI: http://localhost:8090
- Topics are listed under cluster "local".
- Click a topic → **Messages** tab → scroll back to see what was produced.
- **Produce Message** tab lets you manually inject replies to test failure paths.
- Consumer groups tab shows per-group lag (also surfaced on the Kafka dashboard).

## Alternative to kafka-ui — raw CLI

```bash
# Peek payment.commands from the broker
docker exec kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic payment.commands \
  --from-beginning --max-messages 10 --property print.headers=true

# Produce a reply manually
echo '{"sagaId":"...","orderId":"...","success":false,"failureReason":"...","paymentId":null}' | \
  docker exec -i kafka kafka-console-producer.sh \
    --bootstrap-server localhost:9092 \
    --topic payment.replies
```

## Related runbooks

- `troubleshooting.md#alert--order-saga` — when the SagaInFlightStuck alert
  fires, this doc tells you what to inspect.
- `troubleshooting.md#alert--dlt-messages` — Option A in Flow 2 produces
  DLT activity, which these alerts catch.
