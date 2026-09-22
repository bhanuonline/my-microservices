# Debug walkthrough — Order → Payment Saga (the one flow that touches everything)

**Why this flow?** It exercises 8+ services, 10+ microservices patterns, and 3 different failure modes. If you can debug this one flow end-to-end, you can debug any microservice.

---

## Table of contents

1. [What this flow is (business scenario)](#1-what-this-flow-is-business-scenario)
2. [Architecture — every system involved](#2-architecture--every-system-involved)
3. [Pre-flight checklist](#3-pre-flight-checklist)
4. [Happy path — step-by-step trace](#4-happy-path--step-by-step-trace)
5. [Patterns exercised — where each one lives in this flow](#5-patterns-exercised)
6. [Failure scenarios](#6-failure-scenarios)
7. [Interview questions this flow answers](#7-interview-questions-this-flow-answers)
8. [Debugging cheat sheet](#8-debugging-cheat-sheet)

---

## 1. What this flow is (business scenario)

A customer places an order for a product. The system must:

1. **Check** the product is available (via product-service)
2. **Save** the order in "CREATED" state
3. **Charge** payment
4. **Notify** the customer via email
5. Update order state to "PAID"

If payment fails → order goes to "CANCELLED".
If notification fails after payment succeeded → **REFUND the payment** (compensation) and cancel the order.

Simple business rules, but every step crosses a service boundary — which is exactly why microservices patterns matter.

---

## 2. Architecture — every system involved

```
                           ┌────────────────┐
                           │    Client      │
                           │  (Postman/curl)│
                           └────────┬───────┘
                                    │ POST /api/v1/orders
                                    │ {productId, quantity}
                                    ▼
                        ┌───────────────────────┐
                        │   api-gateway :8080   │
                        │   (reactive, Netty)   │
                        │   — routes /api/v1/*  │
                        │   — TokenRelay filter │
                        └───────────┬───────────┘
                                    │ lb://order-service
                                    │ (resolved via Eureka)
                                    ▼
                        ┌───────────────────────┐
                        │  order-service :8083  │
                        │                       │
                        │  OrderController      │
                        │       ↓               │
                        │  ProductService       │◄─────┐
                        │  (@CB @Retry @BH)     │      │
                        │       ↓               │      │ Feign call
                        │  ProductClient        │──────┘ (Eureka discovery
                        │  (Feign @lb://)       │       + LoadBalancer)
                        │       ↓               │
                        │  OrderService         │◄─── H2 DB (in-memory)
                        │   @Transactional      │     "orders" table
                        │       ↓               │
                        │  OrderSagaOrchestrator│──── "order_sagas" table
                        └────────┬──────────────┘     "processed_events" table
                                 │
              ┌──────────────────┼──────────────────┐
              │ payment.commands │ notification.    │  refund
              │                  │   commands       │
              ▼                  ▼                  ▼
         Kafka topics                          (published only on compensation)
              │                  │
     ┌────────┴────────┐    ┌────┴──────────────┐
     │ payment-service │    │ notification :8090│
     │   :8091         │    │                   │
     │                 │    │ NotifyUserCommand │
     │ PaymentCommand  │    │  Handler          │
     │  Handler        │    │                   │
     │                 │    │  (logs, TODO: send)│
     │  amount<$50→OK  │    │                   │
     │  amount≥$50→FAIL│    │  fail-me→FAIL     │
     └────────┬────────┘    └────┬──────────────┘
              │ payment.replies  │ notification.replies
              ▼                  ▼
                Kafka topics
                     │
                     ▼
         ┌─────────────────────────┐
         │   order-service         │
         │   SagaReplyHandlers     │
         │                         │
         │   onPaymentReply()      │
         │   onNotifyReply()       │
         │                         │
         │   → transition saga     │
         │   → update Order status │
         └─────────────────────────┘

  Meanwhile ALL of this is traced end-to-end in:
  ┌────────────────────┐         ┌────────────────────┐
  │   Zipkin :9411     │         │   Eureka :8761     │
  │  (trace timeline)  │         │ (service registry) │
  └────────────────────┘         └────────────────────┘
```

**Total: 8 services + 5 infra components involved in ONE HTTP request.**

---

## 3. Pre-flight checklist

Before you can test this flow, confirm:

```bash
./status.sh
```

Must see:

- ✅ All 5 Docker containers HEALTHY (mysql-user, mysql-product, mysql-auth, kafka, zipkin)
- ✅ eureka-server (8761) UP
- ✅ api-gateway (8080) UP
- ✅ order-service (8083) UP
- ✅ product-service (8082) UP
- ✅ payment-service (8091) UP
- ✅ notification (8090) UP
- ✅ Eureka lists all 5 services registered

auth-server is OK to be 302 (JWKS still serves). resource-server can be DOWN (unused in this flow).

---

## 4. Happy path — step-by-step trace

### 4a. Fire the request

```bash
curl -X POST http://localhost:8083/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: demo-happy-001" \
  -d '{"productId":1,"quantity":2}'
```

Expected response:
```json
{
  "orderId": "<uuid>",
  "productId": 1,
  "quantity": 2,
  "status": "CREATED",
  "createdAt": "2026-09-20T..."
}
```

**Save the orderId — you'll need it to poll status.**

Why direct to order-service (:8083) not gateway (:8080)? Skipping JWT complexity for now. Same result once you set up a token. When you're comfortable, retry via gateway.

### 4b. Watch the trace propagate — 10 hops

Look at each service's terminal. You should see the same `traceId` in log lines across all of them (same `[order-service,abc123def456,spanN,demo-happy-001]` prefix travels through the flow).

| # | Service | What you'll see | Why |
|---|---|---|---|
| 1 | order-service | `POST /api/v1/orders` | Request hits controller |
| 2 | order-service | `Publishing OrderCreatedEvent` (choreo — old flow still runs) | Legacy pub/sub |
| 3 | order-service | `Saga <id> STARTED for orderId=<uuid>` | Orchestrator creates saga row |
| 4 | order-service | `Publishing to paymentCommand-out-0` | Sends PaymentCommand |
| 5 | payment-service | `Processing PaymentCommand sagaId=... amount=19.98` | Consumes command |
| 6 | payment-service | `Publishing PaymentReply` (success=true) | Publishes reply |
| 7 | order-service | `Saga <id> → PAID, sending notify command` | Orchestrator handles reply |
| 8 | notification | `Sending notification sagaId=...` | Consumes NotifyCommand |
| 9 | notification | `Publishing NotifyUserReply` | Publishes reply |
| 10 | order-service | `Saga <id> COMPLETED` | Orchestrator marks saga done, order → PAID |

Also happens in parallel via choreography:
- payment-service consumes `order.created` (from step 2), publishes `payment.completed`
- order-service consumes `payment.completed` via `PaymentEventProcessor` → also marks PAID (idempotent, dedup catches it)

### 4c. Confirm the final state

```bash
# Order status should now be PAID
curl -s http://localhost:8083/api/v1/orders/<orderId> | jq
```

Expected:
```json
{
  "orderId": "<uuid>",
  "status": "PAID",
  ...
}
```

Check the saga table:
```sql
-- from a mysql shell (though order-svc uses H2 in-memory — no direct SQL access)
-- alternative: hit an actuator endpoint or check log lines
```

Since order-service uses H2 in-memory, you can enable the H2 console (add `spring.h2.console.enabled=true` to yml) to browse `orders`, `order_sagas`, `processed_events` tables via http://localhost:8083/h2-console.

### 4d. Confirm the Kafka topics have activity

```bash
# Every topic touched by this flow
for t in order.created payment.commands payment.replies payment.completed notification.commands notification.replies; do
  echo "── $t ──"
  docker exec kafka kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 \
    --topic "$t" --from-beginning --max-messages 2 --timeout-ms 2000 2>/dev/null | head -3
done
```

You'll see the JSON payloads flowing through.

### 4e. See the full trace in Zipkin

Open http://localhost:9411 → Run Query. Click the most recent trace (it'll span 500ms+ across multiple services). You should see a timeline diagram:

```
   order-service   POST /api/v1/orders          |████████████████████| 320ms
   order-service   → GET /products/1/available  |██    |             15ms
   product-service   GET /products/1/available  |█    |              8ms
   order-service   → publish paymentCommand     |█|                  3ms
   payment-service   consume paymentCommand     |    ████            25ms
   payment-service   → publish paymentReply     |        █|          3ms
   order-service   consume paymentReply         |           ████     30ms
   order-service   → publish notifyCommand      |               █|   3ms
   notification    consume notifyCommand        |                ██  15ms
   ...
```

**This is the ONE screenshot that proves you understand microservices to any interviewer.**

---

## 5. Patterns exercised

Where each named pattern shows up during ONE order request:

### 5.1 Service Discovery (Eureka + `lb://`)
- **File:** `order-service/.../client/ProductClient.java`
- **Code:** `@FeignClient(name = "product-service")`
- **When it fires:** step 4b #1 — Feign asks Eureka "who is product-service?", gets an IP, calls it.
- **Interview beat:** "We don't hardcode URLs. Services register themselves; callers discover them. Enables horizontal scaling with zero config."

### 5.2 API Gateway (reactive, Netty)
- **File:** `api-gateway/application.yml`
- **Code:** `routes: - id: order-service, uri: lb://order-service, predicates: - Path=/api/v1/orders/**`
- **When it fires:** if you hit :8080 instead of :8083 directly.
- **Interview beat:** "One entry point for clients. Handles auth, rate limiting, routing. Reactive because it's I/O-bound — Netty handles many concurrent connections cheaply."

### 5.3 Circuit breaker + Retry + Bulkhead (Resilience4j)
- **File:** `order-service/.../service/ProductService.java`
- **Code:**
  ```java
  @Retry(name = "productClient", fallbackMethod = "checkFallback")
  @CircuitBreaker(name = "productClient", fallbackMethod = "checkFallback")
  @Bulkhead(name = "productClient", fallbackMethod = "checkFallback")
  public String checkAvailability(Long productId) { ... }
  ```
- **When it fires:** step 4b #1 (order-service → product-service call).
- **Composition:** Retry OUTERMOST → CircuitBreaker → Bulkhead INNERMOST → actual call.
- **Interview beat:** "One dead downstream must not take down its caller. Retry handles transient blips; CB stops calling once N% of recent calls fail; bulkhead caps concurrency so slow downstream doesn't hog all threads."

### 5.4 Saga orchestration (with compensation)
- **File:** `order-service/.../saga/OrderSagaOrchestrator.java`
- **Code:** state machine transitions in `@Transactional` methods.
- **When it fires:** step 4b #3 onwards.
- **State machine:** `STARTED → PAID → NOTIFIED` (happy) OR `STARTED → COMPENSATING → FAILED`.
- **Interview beat:** "Distributed transactions don't exist. Instead: forward steps + compensating actions. Saga state is persisted so it survives crashes. Central orchestrator makes the flow readable."

### 5.5 Idempotent consumer
- **File:** `order-service/.../consumer/PaymentEventProcessor.java`
- **Code:**
  ```java
  private boolean claimEvent(UUID eventId) {
      try {
          processedRepo.saveAndFlush(new ProcessedEvent(eventId, CONSUMER_NAME));
          return true;
      } catch (DataIntegrityViolationException dup) {
          return false;  // already processed
      }
  }
  ```
- **When it fires:** step 4b #7 and 10 — same message may arrive twice, orchestrator only acts once.
- **Interview beat:** "Kafka gives at-least-once — same message may be delivered twice. Idempotent consumers dedupe on eventId. Together, effectively-exactly-once."

### 5.6 Transactional outbox
- **NOT in this flow** — but present in user-service.
- **Why relevant:** proves you understand the "dual-write problem." Order-service still has the dual-write bug (publish outside tx). Mention this as a known limitation you'd fix with outbox.

### 5.7 Event-driven choreography
- **Files:** `order-service/OrderEventPublisher.java`, `payment-service/OrderCreatedHandler.java`.
- **What runs in parallel to the saga:** old choreography flow — order-service publishes `OrderCreatedEvent`, payment-service consumes and publishes `PaymentCompletedEvent`, order-service consumes and marks PAID.
- **Why both are running:** we built choreography first (Problems #19, #21) then orchestration on top (Problem #24). In real life you'd pick one and delete the other.
- **Interview beat:** "Choreography = each service reacts to events. Orchestration = central coordinator. Choreography scales simpler; orchestration is easier to reason about for complex flows. We built both for teaching purposes."

### 5.8 Distributed tracing (Micrometer + Zipkin)
- **How:** `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` in every service pom. `management.tracing.sampling.probability: 1.0` in every yml.
- **When it fires:** every log line, every HTTP call, every Kafka message.
- **Interview beat:** "One request touches 8 services. Grep across logs is impossible. Trace ID propagates via W3C `traceparent` header — HTTP + Kafka headers. Zipkin shows the timeline."

### 5.9 Correlation IDs (MDC)
- **File:** `common-lib/.../CorrelationIdFilter.java`
- **When it fires:** on every servlet request. Header is `X-Correlation-Id`.
- **Interview beat:** "Trace IDs are opaque. Client-provided correlation IDs let support say 'reference this ID' and grep across all service logs."

### 5.10 DLQ (Dead Letter Queue)
- **File:** `application.yml` on order-service + payment-service.
- **Config:** `enableDlq: true`, `max-attempts: 3`, `back-off-initial-interval: 500`.
- **When it fires:** poison message on any consumed topic — after 3 retries, message goes to `<topic>.DLT`.
- **Observer:** `order-service/.../consumer/DlqObserver.java`
- **Interview beat:** "One malformed message doesn't block a partition forever. Retries handle transient failures; DLQ handles poison pills."

### 5.11 Load balancing (client-side)
- **File:** Feign uses `spring-cloud-starter-loadbalancer` (in order-service pom).
- **How:** `lb://product-service` → LoadBalancer asks Eureka → gets N instances → picks one (round-robin default).
- **Interview beat:** "Client-side LB — order-service picks which product-svc instance to call. No external LB needed. Scales to horizontal replicas."

---

## 6. Failure scenarios

### 6.1 Force payment to fail (amount ≥ $50)

**Trigger:**
```bash
curl -X POST http://localhost:8083/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"quantity":6}'   # 6 * $9.99 = $59.94 > $50
```

**Expected:**
- Saga transitions: `STARTED → FAILED`
- Order: `CREATED → CANCELLED` with reason `amount_over_limit`
- **No compensation needed** — payment was the first step, nothing had succeeded yet.

**Verify:**
```bash
sleep 2
curl -s http://localhost:8083/api/v1/orders/<orderId> | jq '.status'
# → "CANCELLED"
```

### 6.2 Force notify to fail (saga triggers COMPENSATION → refund)

**How:** temporarily modify `OrderSagaOrchestrator.onPaymentReply` to include `"fail-me"` in the notification message. This makes notification-service's `NotifyUserCommandHandler` return failure.

**Trigger:**
```bash
curl -X POST http://localhost:8083/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"quantity":2}'
```

**Expected timeline:**
1. `STARTED` → payment OK → `PAID`
2. Notify command sent with `"fail-me"` → notification returns failure
3. Orchestrator sees notify failed → transitions to `COMPENSATING`
4. Publishes `RefundCommand` to `payment.commands.refund` topic
5. payment-service logs `Executing REFUND sagaId=...`
6. Saga → `FAILED`
7. Order → `CANCELLED`

**Interview beat:** "This is why sagas exist. Money was charged; can't just rollback. Business-logic inverse: issue refund. Every forward step needs a compensating action defined."

### 6.3 Kill product-service (circuit breaker demo)

**Trigger:** Ctrl+C in product-service terminal.

**Then hammer order-service:**
```bash
for i in {1..8}; do
  time curl -s -X POST http://localhost:8083/api/v1/orders \
    -H "Content-Type: application/json" \
    -d '{"productId":1,"quantity":2}' -w " [%{http_code}]\n"
  echo
done
```

**Expected:**
- First 3-5 calls: **slow** (each takes ~5s — Retry does 3 attempts with backoff before returning fallback)
- After breaker trips (~5th call): **instant** 503 with body `Product service is down. Order not created; please retry later.`
- Breaker state:
  ```bash
  curl -s http://localhost:8083/actuator/health | jq '.components.circuitBreakers'
  # → state: "OPEN"
  ```

**Interview beat:** "Without CB, order-service's threads would all block on the dead product-service. Cascading failure. With CB, once we detect the failure pattern, we skip the call entirely — fallback returns instantly. Product-svc gets time to recover; order-svc stays healthy."

### 6.4 Restart product-service (breaker recovery)

**After 10 seconds** (the `wait-duration-in-open-state`), the breaker goes to `HALF_OPEN`.

Send one call:
```bash
curl -X POST http://localhost:8083/api/v1/orders -H "Content-Type: application/json" -d '{"productId":1,"quantity":2}'
```

Breaker sends this ONE call as a probe. If it succeeds → `CLOSED` (recovered). If it fails → back to `OPEN` for another 10s.

### 6.5 Poison message → DLQ

**Publish garbage directly to a consumed topic:**
```bash
docker exec -it kafka kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic payment.replies
# type: this-is-not-a-valid-reply
# Ctrl+D
```

**Watch:**
1. order-service log: 3 attempts to deserialize, each fails
2. Cloud Stream routes to `payment.replies.DLT` topic
3. `DlqObserver` in order-service logs the DLT arrival
4. Main topic offset advances → normal messages resume

**Confirm:**
```bash
docker exec kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic payment.replies.DLT --from-beginning --max-messages 1
```

---

## 7. Interview questions this flow answers

### Q1: "Walk me through a distributed transaction in your system."
**A:** Point to this flow. Emphasize:
- No 2PC — impossible across DB + Kafka.
- Saga pattern with persisted state.
- Every forward step has a compensating action (payment success → refund).
- State machine ensures no illegal transitions.

### Q2: "How do you handle a downstream service that's slow or down?"
**A:** Show `ProductService.java` — 3 Resilience4j patterns stacked:
- **Retry** for transient failures (with exponential backoff)
- **Circuit breaker** for sustained failures (stops calling for 10s)
- **Bulkhead** caps concurrency so slow downstream can't drain thread pool
- **Fallback** returns degraded response instead of failing

### Q3: "How do you correlate logs across services for one request?"
**A:** Two mechanisms:
- **Distributed tracing** — `traceId` auto-propagated via HTTP + Kafka headers, all logs prefixed with it, Zipkin visualizes the timeline.
- **Correlation ID** — client can provide `X-Correlation-Id`, propagated via MDC to every log line, surfaced in error responses for customer support.

### Q4: "Exactly-once delivery — is it possible?"
**A:** No — but effectively exactly-once IS. Kafka gives at-least-once. Consumers must be idempotent. Point to `PaymentEventProcessor.claimEvent()` — uses unique constraint on `processed_events.eventId` to dedupe.

### Q5: "What if payment succeeds but you can't persist the state locally?"
**A:** This is the dual-write problem. Two solutions:
- **Transactional outbox** — write event to same DB in same transaction, background poller publishes to Kafka. (Shown in user-service.)
- **Saga replay** — persisted saga state lets orchestrator resume after crash.

### Q6: "How do you deploy this — kubernetes? blue-green?"
**A:** Not implemented, but talk about:
- Services are stateless (except MySQL) — horizontally scalable.
- Eureka handles auto-registration.
- Circuit breakers protect against rolling deploy gaps.
- Kafka consumer groups mean multiple instances share load automatically.

### Q7: "What would you fix in this codebase given more time?"
**A:** Great question. Honest answers show maturity:
- Outbox pattern on order + payment services (currently dual-write risk)
- Real refund confirmation (currently fire-and-forget)
- Config-server for centralized config
- Proper auth-server SecurityConfig cleanup
- OpenAPI/Swagger docs auto-generated from controllers
- Contract tests (Spring Cloud Contract) between services

### Q8: "How would you handle 10x traffic?"
**A:**
- Scale user/product/order services horizontally (Eureka handles registration).
- Kafka partition count = max parallelism per consumer group. Increase partitions before consumer instances.
- Bulkheads size up.
- Move outbox poller to Debezium (CDC) — no polling overhead.
- Add cache in front of product-service reads (Redis).
- Split write DB from read replicas.

---

## 8. Debugging cheat sheet

### Symptom → where to look

| Symptom | Likely cause | Where to look |
|---|---|---|
| `POST /orders` returns 500 with `Product unavailable` | product-service DB is empty or check-availability logic false | product-service logs; `docker exec mysql-product mysql -uroot -ppass1234 productdb -e "SELECT * FROM products;"` |
| `POST /orders` returns instant fallback ("Product service is down") | Circuit breaker is OPEN | `curl http://localhost:8083/actuator/health \| jq '.components.circuitBreakers'` |
| Order stays in `CREATED` forever | Payment reply never arrived — payment-service dead or Kafka broken | Check payment-service is up, check `payment.commands` topic has message, check `payment.replies` topic has reply |
| Saga row exists but Order status unchanged | Reply consumer wired wrong, or dedup ate a valid message | Check order-service log for "Skipping duplicate", check `processed_events` table |
| No trace in Zipkin | Sampling too low, or Zipkin endpoint wrong | Check `management.tracing.sampling.probability: 1.0`, check `management.zipkin.tracing.endpoint` |
| Trace ID doesn't propagate to Kafka consumer | `micrometer-tracing-bridge-brave` missing on consumer side | Check consumer service's pom |
| `UnknownHostException: kafka` from a Java service | Java service running on Mac tried to use container-network hostname | Ensure yml default profile uses `localhost:9092`, not `kafka:9092` |

### Peek at the flow state (mid-run)

```bash
# 1. Is the order there?
curl -s http://localhost:8083/api/v1/orders/<orderId> | jq

# 2. What did each Kafka topic see?
for t in order.created payment.commands payment.replies notification.commands notification.replies; do
  echo "── $t ──"
  docker exec kafka kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 --topic "$t" \
    --from-beginning --max-messages 3 --timeout-ms 1500 2>/dev/null | head -3
done

# 3. Any DLT messages? (poison pills)
docker exec kafka kafka-topics.sh --bootstrap-server localhost:9092 --list | grep DLT

# 4. Consumer lag per group
docker exec kafka kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list
docker exec kafka kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group order-service-saga --describe

# 5. Circuit breaker events
curl -s http://localhost:8083/actuator/circuitbreakerevents/productClient | jq

# 6. Retry events
curl -s http://localhost:8083/actuator/retryevents/productClient | jq

# 7. In Zipkin — find your trace
open 'http://localhost:9411/zipkin/?serviceName=order-service&lookback=1h'
```

### Which log means what

When you see these lines, this is what happened:

```
"Saga <id> STARTED for orderId=..."          Orchestrator created saga row
"Publishing PaymentCommand ..."               → Kafka payment.commands topic
"Processing PaymentCommand sagaId=..."        payment-service consumed it
"Publishing PaymentReply ..."                 → Kafka payment.replies
"Saga <id> → PAID, sending notify command"   Orchestrator advanced state, next step
"Sending notification sagaId=..."             notification-service consumed
"Publishing NotifyUserReply ..."              → Kafka notification.replies
"Saga <id> COMPLETED"                         Order marked PAID, done
```

Failure branch:
```
"Saga <id> FAILED at payment step"            Payment declined
"Saga <id> → COMPENSATING, sending refund"   Notify failed → issue refund
"Executing REFUND sagaId=..."                 payment-service processed refund
```

Duplicate detection:
```
"Skipping duplicate PaymentCompleted eventId=..."  Idempotent consumer caught replay
```

---

## Practice routine — 20 minutes to interview-ready

1. Run `./status.sh` — confirm all green (2 min)
2. Fire happy path — trace it in Zipkin (5 min)
3. Fire fail-payment path — confirm CANCELLED (2 min)
4. Fire compensation path — see REFUND in payment logs (3 min)
5. Kill product-service, hammer order-service, watch breaker trip (5 min)
6. Restart product-service, wait 10s, watch breaker close (3 min)

Do this 3 times over 3 different days. On day 3 you'll be able to explain the whole thing without notes. That's interview-ready.
