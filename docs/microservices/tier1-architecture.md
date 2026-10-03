# Tier 1 Architecture — Observability + Resilience

Companion to [tier1-roadmap.md](tier1-roadmap.md). The roadmap tells you
*what to build*; this doc explains *how it fits together*, with diagrams of
every data path and failure mode.

Audience: you, building this to learn and to demo in interviews.

- [1. The complete stack at a glance](#1-the-complete-stack-at-a-glance)
- [2. Lifecycle of one request through everything](#2-lifecycle-of-one-request)
- [3. Distributed tracing — Zipkin](#3-distributed-tracing)
- [4. Metrics — Prometheus + Grafana](#4-metrics)
- [5. Centralized logging — Loki + Promtail](#5-centralized-logging)
- [6. Resilience4j](#6-resilience4j)
- [7. HTTP idempotency](#7-http-idempotency)
- [8. Operational cheat sheet](#8-operational-cheat-sheet)
- [9. Interview script](#9-interview-script)

---

## 1. The complete stack at a glance

```
┌───────────────────────────────────────────────────────────────────────────┐
│                             OBSERVABILITY PLANE                           │
│                                                                           │
│   ┌─────────┐   ┌────────────┐   ┌──────────┐   ┌───────────────────┐    │
│   │ Zipkin  │   │ Prometheus │   │   Loki   │   │      Grafana      │    │
│   │ :9411   │   │   :9090    │   │  :3100   │   │       :3000       │    │
│   │ (traces)│   │ (metrics)  │   │  (logs)  │   │ dashboards + xplr │    │
│   └────▲────┘   └─────▲──────┘   └────▲─────┘   └───────────────────┘    │
│        │              │               │          data sources ↑          │
│        │              │               │          (traces, metrics, logs) │
│        │ POST spans   │ GET /scrape   │ POST logs                        │
│        │              │               │                                  │
│  ┌─────┴──────────────┴───────┐  ┌────┴────┐                             │
│  │  every service exports:    │  │Promtail │  tails docker container     │
│  │                            │  │  (sidecar) stdout → ships JSON lines  │
│  │  /actuator/prometheus      │  └────▲────┘                             │
│  │  POST /api/v2/spans        │       │                                  │
│  │  stdout JSON logs ─────────┼───────┘                                  │
│  └────────────┬───────────────┘                                          │
└───────────────┼──────────────────────────────────────────────────────────┘
                │
┌───────────────┼──────────────────────────────────────────────────────────┐
│               ▼                   APPLICATION PLANE                       │
│                                                                           │
│   ┌───────────┐   ┌───────────┐   ┌──────────────┐                        │
│   │  Browser  │──▶│  API GW   │──▶│ Auth Server  │                        │
│   │           │   │  :8080    │   │   :8095      │                        │
│   └───────────┘   │           │   └──────────────┘                        │
│                   │ Resil4J   │                                           │
│                   │ RateLim   │                                           │
│                   │ CircuitBr │                                           │
│                   └─────┬─────┘                                           │
│                         │                                                 │
│      ┌──────────────────┼──────────────────┬──────────────┐              │
│      ▼                  ▼                  ▼              ▼              │
│  ┌───────┐         ┌─────────┐         ┌─────────┐   ┌───────┐           │
│  │ Order │◀─saga──▶│ Payment │         │ Product │◀──│ User  │           │
│  │ :8083 │  Kafka  │         │         │ :8082   │ R4J :8081             │
│  │       │         │         │         │         │   │       │           │
│  │ ▪idem.│         │         │         │         │   │       │           │
│  │ ▪@NewS│         │         │         │         │   │       │           │
│  └───┬───┘         └────┬────┘         └─────────┘   └───────┘           │
│      │                  │                                                 │
│      └──────Kafka :9092 ┴──── (events / saga replies / DLQ) ─────────┐   │
│                                                                       │   │
│      ┌─────────────────┐                                              │   │
│      │  Notification   │◀─────────────────────────────────────────────┘   │
│      └─────────────────┘                                                  │
│                                                                           │
└───────────────────────────────────────────────────────────────────────────┘

                        ─── infra ───
       MySQL × 3   Vault (dev)   H2 (in-mem, order-service)
```

**Legend:**
- `▪idem.` — HTTP `Idempotency-Key` honoured on `POST /api/v1/orders`.
- `▪@NewS` — business span `order.create` emitted via `@NewSpan`.
- `R4J` — Resilience4j guards (circuit breaker + retry + bulkhead + timeouts).

---

## 2. Lifecycle of one request

Walk through what happens when a browser POSTs `/api/v1/orders` with
`Idempotency-Key: K`. Every Tier 1 feature engages at least once.

```
Browser                 API GW              Order-service          Observability
   │                      │                      │                        │
   │ POST /orders         │                      │                        │
   │ Idempotency-Key:K    │                      │                        │
   │ X-Correlation-Id:C   │                      │                        │
   ├─────────────────────▶│                      │                        │
   │                      │ CorrelationIdFilter  │                        │
   │                      │   MDC += {corrId=C}  │                        │
   │                      │ ○ new trace T, span S1                        │
   │                      │   traceparent="00-T-S1-01"                    │
   │                      │ CircuitBreaker       │                        │
   │                      │   cb[order-route]    │                        │
   │                      │   allow? → CLOSED ✓  │                        │
   │                      │ RequestRateLimiter   │                        │
   │                      │   tokens left? ✓     │                        │
   │                      │                      │                        │
   │                      │ GET /orders          │                        │
   │                      │ traceparent="00-T-S1-01"                      │
   │                      │ X-Correlation-Id:C   │                        │
   │                      │ Idempotency-Key:K    │                        │
   │                      ├─────────────────────▶│ CorrelationIdFilter    │
   │                      │                      │   MDC += {corrId=C}    │
   │                      │                      │ ○ continue trace T,    │
   │                      │                      │   span S2 parent=S1    │
   │                      │                      │                        │
   │                      │                      │ IdempotencyFilter      │
   │                      │                      │   lookup (K, "POST /orders")                │
   │                      │                      │   miss → cache body    │
   │                      │                      │   let through …        │
   │                      │                      │                        │
   │                      │                      │ OrderController        │
   │                      │                      │   @PostMapping         │
   │                      │                      │ OrderService.create    │
   │                      │                      │   ○ @NewSpan("order.create") span S3        │
   │                      │                      │   ┌─ INSERT order      │
   │                      │                      │   │  INSERT outbox row ← same tx           │
   │                      │                      │   └─ commit            │
   │                      │                      │   saga.start()         │
   │                      │                      │                        │
   │                      │                      │ OutboxRelay (poll)     │
   │                      │                      │   send → Kafka         │
   │                      │                      │   span S4 parent=S3    │
   │                      │                      │   (b3 headers on msg)  │
   │                      │                      │                        │
   │                      │                      │ (handler returns)      │
   │                      │                      │ IdempotencyFilter      │
   │                      │                      │   wraps response       │
   │                      │                      │   save (K, hash, body) │
   │                      │                      │                        │
   │                      │ 201 Created {order}  │                        │
   │                      │◀─────────────────────│                        │
   │                      │                      │                        │
   │                      │                      │ async out of band ────▶│
   │                      │                      │  spans S1..S4 → Zipkin │
   │                      │                      │  JSON logs → stdout    │
   │                      │                      │                        │   ┌──Promtail
   │                      │                      │                        │◀──┤  tails ↑
   │                      │                      │                        │   │  pushes
   │                      │                      │                        │   ▼  → Loki
   │                      │                      │                        │   Prometheus scrapes
   │ 201 Created          │                      │                        │   /actuator/prometheus
   │◀─────────────────────│                      │                        │   every 15s
```

If the client retries with the **same `Idempotency-Key`**, the second request
short-circuits inside `IdempotencyFilter` and never reaches the controller —
you get back the stored 201 and `Idempotency-Replay: true` header.

If `payment-service` is down, Resilience4j in `user-service → product-service`
(or in gateway routes) opens the breaker; subsequent calls fail fast via the
fallback. All three observability signals (trace, metric, log) show the fault.

---

## 3. Distributed tracing

### Concept

```
      traceId T  ───────────────────────────────────────────────────▶
      │
      ├─ span S1  api-gateway        HTTP GET /orders
      │     │
      │     ├─ span S2  order-service   servlet dispatcher
      │     │     │
      │     │     ├─ span S3  order.create     ← @NewSpan annotation
      │     │     │     │
      │     │     │     ├─ span S3a  SELECT product
      │     │     │     ├─ span S3b  INSERT orders
      │     │     │     ├─ span S3c  INSERT outbox_event
      │     │     │     └─ span S3d  saga.start
      │     │     │
      │     │     └─ span S4  kafka send → order.created
      │     │           │
      │     │           └─ (consumer side continues same trace in product-svc)
      │
      └─ wall-clock: ms from first span start to last span end
```

**Why one tree:** every service shares the same `traceId` for one logical
request. Clicking a trace in Zipkin shows timing of each span — easy to see
"the payment call ate 2.3 s out of a 2.5 s request."

### How propagation works

```
        HTTP                          HTTP                       Kafka
Browser ─────▶ GW ────────────────▶ Order ──────────────────▶ (topic)
         (no header)             traceparent=T-S1-01            headers:
         GW injects header       Order continues trace          traceparent=T-S3-01
                                 Spring-wrapped RestTemplate       b3=T-S3-1
                                 adds outbound traceparent         →  consumed by
                                                                     Product-svc
                                                                     which continues
                                                                     the trace
```

- **HTTP:** `Micrometer Tracing + Brave bridge` instruments inbound dispatcher
  (`FilterRegistration` auto-added) and outbound `RestTemplate`/`RestClient`/
  `WebClient`/Feign.
- **Kafka:** Spring Boot's `KafkaTracingAutoConfiguration` wraps the producer
  factory with a `TracingProducerFactoryCustomizer` and the consumer factory
  with the corresponding customizer; both read/write W3C `traceparent` on
  message headers.
- **MDC:** `micrometer-tracing-bridge-brave` populates `traceId` and `spanId`
  into logback MDC. Your `logback-observability.xml` already surfaces these as
  JSON fields.

### Where `common-lib` wired it

```
common-lib/pom.xml
   ├─ micrometer-tracing-bridge-brave   ← wires auto-config, instrumentation,
   │                                      and MDC population
   └─ zipkin-reporter-brave             ← HTTP exporter to :9411/api/v2/spans

each service's application.yml
   management.zipkin.tracing.endpoint = http://zipkin:9411/api/v2/spans
   management.tracing.sampling.probability = 1.0   (dev; drop to 0.1 for prod)
```

### Custom business span we added

```java
// order-service/.../service/OrderService.java
@NewSpan("order.create")
@Transactional
public Order create(@SpanTag("order.productId") Long productId,
                    @SpanTag("order.quantity") Integer quantity) { ... }
```

In Zipkin's UI you'll see a span named `order.create` under the HTTP span,
tagged with `order.productId` and `order.quantity` — searchable by tag.

### Trade-offs

| Decision | Why |
|---|---|
| Brave bridge over OTel bridge | Lighter, zero extra deps, mature on Spring. Switching to OTel is a one-line swap later. |
| Sample 100% in dev | Need every trace to debug. In prod, 1–10% keeps Zipkin cheap. |
| Don't tag `userId` or `orderId` | Not high-cardinality on *spans* (good) but DO tag them on business spans. The cardinality rule applies to *metrics*, not traces. |

---

## 4. Metrics

### Pull model

```
     ┌─────────────────┐    scrape every 15s      ┌─────────────────┐
     │   Prometheus    │ ─────────────────────▶  │ order-service   │
     │                 │  GET /actuator/prometheus│  Micrometer     │
     │  TSDB  ↓        │                          │  registry       │
     │  retains 7d     │ ◀───────────────────── │ exports:        │
     └─────────────────┘  text/plain exposition  │ http_server_... │
             │                                   │ jvm_memory_...  │
             │ PromQL                            │ resilience4j_...│
             ▼                                   │ orders_placed...│
       ┌─────────┐                               └─────────────────┘
       │ Grafana │  queries Prom for panels; stacks traces & logs
       └─────────┘
```

**Why pull not push:**
- Prometheus discovers targets (static or via Eureka SD). If a service dies,
  Prometheus flips its `up` series to 0 — alertable.
- No "lost metrics" from a crashed pusher.
- Easy local dev: hit the scrape URL yourself to see raw values.

### Target set (current)

```
scrape_configs → job 'microservices' → static_configs → targets:
   api-gateway:8080       auth-server:8095       resource-server:8096
   user-service:8081      product-service:8082   order-service:8083
```

If you add a new service: give it `common-lib` (brings the Prometheus
registry), expose port, add it to `observability/prometheus.yml`, restart
Prometheus (`docker exec prometheus kill -HUP 1`).

### The three metric shapes you'll meet

```
Counter     monotonic ↑              rate() over window → req/s
                                     ex. http_server_requests_seconds_count

Gauge       up/down                  current value
                                     ex. jvm_memory_used_bytes

Histogram   count + sum + buckets    histogram_quantile() → p50/p95/p99
                                     ex. http_server_requests_seconds_bucket
```

### The RED dashboard panels

```
┌────────────────────────────────┬────────────────────────────────┐
│  Rate — req/s per service      │  Errors — 5xx rate per service │
│    sum by (application) (rate( │    sum{status=~"5.."} / sum    │
│     http_server_requests_      │     over 1m window             │
│     seconds_count[1m]))        │                                │
├────────────────────────────────┼────────────────────────────────┤
│  Duration — p95/p99 latency    │  JVM heap used                 │
│    histogram_quantile(0.95,    │    jvm_memory_used_bytes       │
│     sum by (application, le)   │     {area="heap"}              │
│     (rate(..._bucket[1m])))    │                                │
├────────────────────────────────┴────────────────────────────────┤
│  Resilience4j breaker states (CLOSED=green, OPEN=red)           │
│    resilience4j_circuitbreaker_state                            │
├─────────────────────────────────────────────────────────────────┤
│  CB call outcomes — successful / failed / not_permitted         │
│    rate(resilience4j_circuitbreaker_calls_total[1m])            │
└─────────────────────────────────────────────────────────────────┘
```

Dashboard JSON lives at `observability/grafana/dashboards/microservices-red.json`
and auto-loads via Grafana provisioning.

### Cardinality warning — read this twice

```
GOOD tag               BAD tag
─────────              ─────────
service: "order"       userId: "u-7f3a..."
endpoint: "/orders"    orderId: "o-9bc1..."
status: 200            requestId: "..."
method: "POST"         any UUID

Reason: Prometheus stores one timeseries per unique label combination.
10 services × 20 endpoints × 10 status codes = 2000 series. Fine.
10 services × 1M users = 10M series = Prometheus OOM.
```

Spring Boot's `http_server_requests_seconds_*` is already correctly tagged.
When you add business metrics, think "would I ever want to graph *per-user*?"
If no, don't tag per-user.

---

## 5. Centralized logging

### Promtail pipeline

```
Docker container stdout                                         Loki
┌───────────────────────┐                                   ┌──────────┐
│ order-service         │  ┌─────────────────────────────┐  │ labels:  │
│  { "@timestamp":"…",  │  │ Promtail                    │  │   svc    │
│    "level":"INFO",    │─▶│                             │─▶│   level  │
│    "service":"order", │  │  docker_sd_configs discover │  │   container
│    "traceId":"abc…",  │  │  pipeline_stages:           │  │          │
│    "correlationId":   │  │    - json: extract fields   │  │ parsed:  │
│    "msg":"placed…" }  │  │    - labels: level, svc     │  │   traceId│
│                       │  │    (NEVER label traceId)    │  │   corrId │
└───────────────────────┘  └─────────────────────────────┘  │          │
                                                            │ indexes: │
                                                            │ labels   │
                                                            │ only     │
                                                            └──────────┘
```

**Key design choice — labels vs parsed fields:**

```
label         → indexed; cheap to filter; keep cardinality < ~100 values
parsed field  → not indexed; costs a scan to filter but any cardinality OK

level  = INFO | WARN | ERROR | DEBUG     ← 4 values → LABEL ✓
svc    = order-service | user-service... ← ~10 values → LABEL ✓
traceId= abc123... (UUID per request)    ← millions → FIELD ✓
```

Promoting `traceId` to a label would create one Loki stream per request. That
blows up index size and makes everything slow.

### Query examples (LogQL)

```
# All errors from order-service
{svc="order-service", level="ERROR"}

# Logs for a specific correlation id
{svc="order-service"} | json | correlationId="trace-test-1"

# Error rate per service (shows up as a Grafana panel)
sum by (svc) (rate({level="ERROR"}[5m]))

# Logs matching a trace (clickable from Zipkin trace too, after derived-field setup)
{} | json | traceId="abc123..."
```

### The traceId → Zipkin click-through

```
Grafana log line in Loki Explore:
   { "msg": "payment call failed", "traceId": "abc123…" }
                                                ↑
                               derived-field regex grabs this
                               datasourceUid: zipkin
                               URL: ${__value.raw}  →  Zipkin lookup by traceId

   Clicking the TraceID badge opens Zipkin on that exact trace.
```

Configured in `observability/grafana/provisioning/datasources/datasources.yml`
under `Loki` → `jsonData.derivedFields`.

### Loki vs ELK

| | Loki | ELK |
|---|---|---|
| Indexes | labels only | full text |
| Cost per GB logs | low | higher (JVM, Elasticsearch cluster) |
| Query power | moderate (LogQL) | high (Lucene / KQL) |
| Operational weight | tiny | heavy |
| When to pick | high log volume, cost-sensitive | you need full-text / fuzzy search |

For a demo stack: Loki wins. For enterprise log analytics: often ELK.

---

## 6. Resilience4j

### State machine

```
                     failure rate ≥ threshold
                     OR slow-call rate ≥ threshold
          ┌───────────────────────────────────┐
          │                                   ▼
      ┌───────┐                           ┌───────┐
      │CLOSED │                           │ OPEN  │
      │       │                           │       │
      │ all   │                           │ reject│
      │ calls │                           │ fast  │
      │ allowed│                          │ call  │
      └───┬───┘                           │ fallbk│
          ▲                               └───┬───┘
          │ permittedCalls succeed            │
          │ in HALF_OPEN                      │ waitDurationInOpenState elapsed
          │                                   ▼
          │                               ┌─────────┐
          └───────────────────────────────│HALF_OPEN│
                                          │trial N  │
                                          │calls    │
                                          └─────────┘
```

### Decorator order matters

Spring applies the outermost annotation first; the stack we chose:

```
   CircuitBreaker    ← outermost: short-circuits before we spend any attempt budget
       │
       └─▶ Retry     ← if an attempt fails transiently, retry inside the breaker;
             │         the breaker only sees final success/failure
             │
             └─▶ Bulkhead  ← each attempt reserves a slot; prevents a retry storm
                   │          from exhausting the pool
                   │
                   └─▶ actual call (RestTemplate / Feign / WebClient)
```

Why not Retry outside CB? Retrying through an open breaker just burns time
to no effect — the breaker is already saying "stop."

Why not Bulkhead outside Retry? One caller could reserve the slot once, then
retry 3× while holding it — bulkhead's point is to cap concurrent work.

### What runs where today

```
user-service      ProductService.getAllProducts()  [NEW]
                     @CircuitBreaker(name="productClient", fallback→empty list)
                     @Retry(name="productClient")
                     @Bulkhead(name="productClient")
                  RestTemplate   connectTimeout=2s   readTimeout=3s

order-service     ProductService.check…             [existing]
                     @CircuitBreaker + @Retry + @Bulkhead on productClient

api-gateway       routes/{order,user,…}             [existing]
                     filter: CircuitBreaker (per-route breaker)
                     filter: RequestRateLimiter   (per-API-key tokens)
```

### Config shape (user-service example)

```yaml
resilience4j:
  circuitbreaker.instances.productClient:
    slidingWindowSize:   20
    minimumNumberOfCalls:10
    failureRateThreshold:50          # ≥50% of 20 → open
    slowCallRateThreshold:50
    slowCallDurationThreshold:2s     # anything > 2s counts as "slow"
    waitDurationInOpenState:30s      # cool-down before trial calls
    permittedNumberOfCallsInHalfOpenState:3
  retry.instances.productClient:
    maxAttempts:3
    waitDuration:300ms
    enableExponentialBackoff:true
    exponentialBackoffMultiplier:2   # 300ms, 600ms, 1200ms
    retryExceptions: [IOException, ResourceAccessException, TimeoutException]
  bulkhead.instances.productClient:
    maxConcurrentCalls:20
    maxWaitDuration:50ms
  timelimiter.instances.productClient:
    timeoutDuration:3s
```

### Metrics it exports (visible in Grafana panel 6 of our dashboard)

```
resilience4j_circuitbreaker_state{name="productClient", application="user-service"}
    gauge: 0=CLOSED 1=DISABLED 2=HALF_OPEN 3=OPEN 4=FORCED_OPEN

resilience4j_circuitbreaker_calls_total{name="productClient", kind="successful|failed|ignored|not_permitted"}
    counter: increments per outcome

resilience4j_retry_calls_total{name="productClient", kind="successful_without_retry|successful_with_retry|failed_with_retry|failed_without_retry"}
    counter: shows whether retries actually help

resilience4j_bulkhead_available_concurrent_calls{name="productClient"}
    gauge: how many slots free right now
```

### Chaos test playbook

```
1. Start full stack.
2. Hit /users/with-products 20× — all succeed, CB stays CLOSED.
3. docker compose stop product-service
4. Hit the same endpoint 20× — first few fail+retry+fallback,
   then CB flips OPEN. Fallback returns [] every call. No 5xx to caller.
5. Grafana panel 5 shows the breaker going red.
6. docker compose start product-service
7. Wait 30s. CB flips HALF_OPEN, trial calls succeed, back to CLOSED.
```

Record a screen capture of this. It is the single best demo for an
observability+resilience interview question.

---

## 7. HTTP idempotency

### Why: the "double-click" and "retry" problems

```
     without idempotency                       with idempotency
     ────────────────────                      ─────────────────
     client POSTs /orders                      client POSTs /orders Idempotency-Key:K
       → server creates order o-1                → server creates order o-1
     network hangs; client retries             network hangs; client retries same K
       → server creates order o-2 ❌            → server returns stored o-1 ✓
         user gets charged twice                 Idempotency-Replay: true
```

### The filter flow

```
HTTP POST /api/v1/orders
Idempotency-Key: K
X-Correlation-Id: C
Body: {productId:1, quantity:2}
         │
         ▼
┌────────────────────────┐
│ IdempotencyFilter      │
│ (OncePerRequestFilter) │
└────────────┬───────────┘
             │
             │  method ∈ {POST,PUT,PATCH,DELETE} AND
             │  Idempotency-Key present AND
             │  endpoint is in idempotency.endpoints?
             │
          ┌──┴──┐
       NO │     │ YES
          ▼     ▼
       passthrough   read body, SHA-256 → hash
                     repo.findByKeyAndEndpoint(K, "POST /api/v1/orders")
                                │
                       ┌────────┴────────┐
                       │                 │
                  FOUND                 NOT FOUND
                       │                 │
                       ▼                 ▼
                 same hash?           cache body → forward to handler
                 ┌─────┬───────┐          │
              YES│     │NO     │          │ handler runs, response wrapped
                 ▼     ▼       ▼          ▼
          replay stored  422        status<500 ?
          status+body    "key reused"       ┌──┴──┐
          header:        with different     │     │
          Idempotency-   body"             YES   NO
          Replay: true                      │     │
                                            ▼     ▼
                                   INSERT record   skip caching
                                   (K, endpoint, principal,
                                    hash, status, body,
                                    createdAt, expiresAt)
```

### Why we DO NOT cache 5xx responses

A 5xx might be a transient server failure. If we cached it, the client's
legitimate retry (with the same key) would get the cached 500 forever.
Rule: cache only when the server actually produced a definitive outcome
(2xx or 4xx).

### Database shape

```
idempotency_records
  idem_key       VARCHAR(128)  ┐
  endpoint       VARCHAR(255)  ├─ composite PK
                               ┘
  principal      VARCHAR(128)       ← scope per user (prevents cross-user key collisions)
  request_hash   VARCHAR(64)        ← SHA-256 of body — detects key reuse with different payload
  status_code    INT
  content_type   VARCHAR(128)
  response_body  LOB                ← capped at idempotency.maxBodyBytes (default 1 MiB)
  created_at     TIMESTAMP
  expires_at     TIMESTAMP          ← indexed; housekeeping job deletes expired
```

### What we built in common-lib

```
com.example.common.idempotency.http/
  IdempotencyRecord.java              @Entity, composite PK via @IdClass
  IdempotencyRecordRepository.java    Spring Data JPA repo + deleteExpired()
  IdempotencyFilter.java              OncePerRequestFilter — the heart
  IdempotencyProperties.java          @ConfigurationProperties("idempotency")
  IdempotencyAutoConfiguration.java   @ConditionalOnProperty(enabled=true)
                                      + @EntityScan + @EnableJpaRepositories

common-lib/src/main/resources/META-INF/spring/
  org.springframework.boot.autoconfigure.AutoConfiguration.imports
    → includes IdempotencyAutoConfiguration
```

Any service that imports common-lib gets the filter "for free" as soon as
it sets:

```yaml
idempotency:
  enabled: true
  ttl: 24h
  endpoints:
    - "POST /api/v1/orders"           # exact match, "METHOD /path"
    - "POST /api/v1/payments/charge"
```

Services that don't enable it pay zero cost — `@ConditionalOnProperty` skips
bean creation entirely.

### What's enabled today

```
order-service   idempotency.enabled: true
                endpoints: ["POST /api/v1/orders"]
```

### Interactions with other Tier 1 features

| Combines with | How |
|---|---|
| Resilience4j Retry | **Required pair.** Retries MUST send the same `Idempotency-Key` on every attempt, or you duplicate work on retry success. |
| Tracing | The replay path is a separate span. In Zipkin you'll see a very short span with the `Idempotency-Replay: true` outcome. |
| Metrics | Future: add a counter `idempotency_replays_total{endpoint}` — spikes mean clients are retrying a lot (symptom of upstream flakiness). |
| Correlation-id | Replay returns the stored body but the current `X-Correlation-Id` is logged — you can tell "this *replay* happened for correlation-id C2 but it originally came from C1." Add `original_correlation_id` to the record if you want to trace back. |

---

## 8. Operational cheat sheet

### Ports

```
Application plane             Observability plane
─────────────────             ────────────────────
8080  api-gateway             9411  zipkin
8081  user-service            9090  prometheus
8082  product-service         3100  loki
8083  order-service           3000  grafana   (admin/admin)
8095  auth-server             8761  eureka-server
8096  resource-server
9092  kafka
```

### First-time boot

```
# 1. Build everything
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.0.2.jdk/Contents/Home
mvn -DskipTests clean package

# 2. Start the observability plane
docker compose up -d zipkin prometheus loki promtail grafana

# 3. Start the app plane (if running via docker)
docker compose up -d eureka-server auth-server api-gateway \
                    user-service product-service order-service

# 4. Open Grafana → Dashboards → "Microservices — RED + Resilience"
open http://localhost:3000
```

### Verifying each feature

```
TRACING
  Hit a request:
    curl -u admin:admin123 -H "Content-Type: application/json" \
         -X POST http://localhost:8080/api/v1/orders \
         -d '{"productId":1,"quantity":2}'
  Then open http://localhost:9411 → "Run Query"
  Expect: one trace with spans from gateway → order → kafka (if payment-service listens)

METRICS
  http://localhost:9090/targets   → every service "UP"
  http://localhost:3000           → RED dashboard shows rate rising after you curl

LOGGING
  http://localhost:3000 → Explore → Loki
  Query: {svc="order-service"} | json
  Click any traceId → opens Zipkin on that trace

RESILIENCE4J
  docker compose stop product-service
  Hit user-service's /users-with-products a few times
  http://localhost:3000 → RED dashboard panel 5 shows breaker OPEN
  docker compose start product-service
  Wait 30 s; breaker returns to CLOSED

IDEMPOTENCY
  KEY=$(uuidgen)
  # First call
  curl -u admin:admin123 -H "Idempotency-Key: $KEY" \
       -H "Content-Type: application/json" \
       -X POST http://localhost:8080/api/v1/orders \
       -d '{"productId":1,"quantity":2}' -v 2>&1 | grep -i idempotency
  # Replay
  curl -u admin:admin123 -H "Idempotency-Key: $KEY" \
       -H "Content-Type: application/json" \
       -X POST http://localhost:8080/api/v1/orders \
       -d '{"productId":1,"quantity":2}' -v 2>&1 | grep -i idempotency
  # Expect: second call returns "Idempotency-Replay: true"
```

### Where to add a new service and get all five features

```
1. Add module to parent pom.
2. In your service pom:
     <dependency>common-lib</dependency>        ← tracing, metrics, JSON logs, idempotency
     <dependency>resilience4j-spring-boot3</dependency>  ← if calling other services
3. application.yml:
     management.endpoints.web.exposure.include: health,info,metrics,prometheus,...
     management.zipkin.tracing.endpoint: http://zipkin:9411/api/v2/spans
     (plus resilience4j and idempotency blocks as needed)
4. observability/prometheus.yml:
     - '<new-service>:<port>'   # add to scrape targets
5. Restart Prometheus: docker exec prometheus kill -HUP 1
```

---

## 9. Interview script

Memorise these — they turn each diagram into a 60-second answer.

**Q: How do you debug a slow request across 5 microservices?**
> Every request carries a W3C `traceparent` header injected at the gateway.
> Each service continues the trace via Micrometer's Brave bridge, including
> across async hops like Kafka. Spans go to Zipkin where I can see which
> service and which SQL/HTTP call ate the budget. The same `traceId` lands
> in the structured JSON logs, so I can pivot from a trace to the error log
> and back from the log to the trace via Grafana's derived-field link.

**Q: How do you know your service is healthy?**
> Every service exposes `/actuator/prometheus`. Prometheus scrapes every 15 s.
> Grafana shows the RED dashboard — rate, error rate, p95/p99 latency per
> service — plus JVM heap and Resilience4j breaker states. A breaker turning
> red is visible before users notice.

**Q: What happens if payment-service goes down for five minutes?**
> The caller is wrapped in Resilience4j. First few calls time out (3 s) and
> retry with jitter. After the breaker's failure threshold is crossed, it
> opens and we fast-fail into a fallback — in our case, an empty response
> for the product list, or an outbox enqueue for payments. The caller's
> threads don't block, Grafana shows the breaker state transition, and we
> don't 5xx the end user.

**Q: User clicks "Buy" twice. Prevent two orders?**
> Clients send `Idempotency-Key: <uuid>` on POST. A servlet filter stored in
> `common-lib` looks up `(key, endpoint)` in Postgres. First call: handler
> runs, we cache the response. Second call with the same key: filter returns
> the cached response with `Idempotency-Replay: true`. If the body hash
> differs we return 422 — stops accidental key reuse. TTL is 24 h.

**Q: How do you avoid retrying something that would duplicate?**
> We only retry on transient infra exceptions — `IOException`, `TimeoutException`,
> `ResourceAccessException`. We never retry 4xx — those are business failures.
> For writes that could duplicate, the client passes `Idempotency-Key` so a
> retry of a write that silently succeeded on the server still returns the
> same result instead of creating a second row.

**Q: What's wrong with tagging metrics by `userId`?**
> Cardinality explosion. Prometheus stores one timeseries per unique label
> set. A million users = a million series = OOM. Metrics labels stay low
> cardinality (service, endpoint, status). Per-user identifiers live in
> traces (per-request anyway) and logs (as parsed fields, not labels).

---

## Related reading

- [tier1-roadmap.md](tier1-roadmap.md) — the step-by-step build plan
- [observability-multi-service.md](observability-multi-service.md) — how JSON logs + tracing shipped originally
- [concepts/distributed-tracing.md](concepts/distributed-tracing.md)
- [concepts/circuit-breaker.md](concepts/circuit-breaker.md)
- [concepts/retry-and-bulkhead.md](concepts/retry-and-bulkhead.md)
- [concepts/correlation-ids.md](concepts/correlation-ids.md)
- [concepts/outbox-pattern.md](concepts/outbox-pattern.md)
