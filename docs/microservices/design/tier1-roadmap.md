# Tier 1 Observability & Resilience Roadmap

Audience: you, learning microservices for interviews. This doc covers the five
"must-have" production features every microservices interviewer asks about, in
the order you should build them in this repo.

- [1. Distributed tracing (Zipkin / OpenTelemetry)](#1-distributed-tracing)
- [2. Metrics (Prometheus) + dashboards (Grafana)](#2-metrics--dashboards)
- [3. Centralized logging (Loki + Promtail)](#3-centralized-logging)
- [4. Resilience4j across every cross-service call](#4-resilience4j)
- [5. External idempotency keys on POSTs](#5-idempotency-keys)

Each section has: **what it is**, **why interviewers ask**, **current state in this
repo**, **implementation steps**, **how to verify**, **talking points**.

---

## Target architecture after Tier 1

```
                                  ┌────────────────────────────────────────────────┐
                                  │              OBSERVABILITY PLANE               │
                                  │                                                │
                                  │   ┌─────────┐   ┌──────────┐   ┌───────────┐   │
                                  │   │ Zipkin  │   │Prometheus│   │   Loki    │   │
                                  │   │ (spans) │   │(metrics) │   │  (logs)   │   │
                                  │   └────▲────┘   └─────▲────┘   └─────▲─────┘   │
                                  │        │              │              │         │
                                  │        │          ┌───┴──────────────┴──────┐  │
                                  │        │          │        Grafana          │  │
                                  │        │          │  (dashboards + explore) │  │
                                  │        │          └─────────────────────────┘  │
                                  └────────┼──────────────┼──────────────┼─────────┘
                                           │ spans        │ scrape       │ push
                                           │              │              │
       ┌──────────┐  ┌──────────┐  ┌───────┴──────────────┴──────────────┴────────┐
       │ Browser  │─▶│  API GW  │─▶│  Each service exposes:                       │
       └──────────┘  │  (9000)  │  │   - traceId W3C header propagated (Brave)    │
                     │          │  │   - /actuator/prometheus (Micrometer)        │
                     │ ┌─────┐  │  │   - JSON logs with traceId+correlationId     │
                     │ │ R4J │  │  │   - Resilience4j on cross-service clients    │
                     │ │ RL  │  │  │   - Idempotency-Key dedup on POSTs           │
                     │ └─────┘  │  └──────────────────────────────────────────────┘
                     └────┬─────┘
                          │
       ┌──────────────────┼─────────────────┬──────────────┬───────────────┐
       ▼                  ▼                 ▼              ▼               ▼
   ┌───────┐         ┌─────────┐       ┌──────────┐   ┌───────┐      ┌──────────────┐
   │ Order │ ───────▶│ Payment │       │ Product  │   │ User  │      │ Notification │
   │       │ Kafka   │         │       │          │   │       │      │              │
   └───┬───┘  (saga) └────┬────┘       └──────────┘   └───────┘      └──────▲───────┘
       │                  │                                                 │
       └──────────────────┴──── Kafka (events, DLQ) ──────────────────────▶─┘
```

Everything in the top box is new infra you'll add. Everything in the bottom
box exists today — you'll instrument it.

---

## 1. Distributed tracing

### What it is
Every request gets a `traceId`. Every operation on that request gets a `spanId`
whose parent is the caller's span. The tree of spans — browser → gateway →
order → kafka → payment → notification — is uploaded to a trace backend
(Zipkin) that reconstructs the full timeline.

### Why interviewers ask
"When a user says 'checkout is slow,' how do you find which service is slow?"
Logs alone can't answer it; you need traces. This is the #1 observability
question for distributed systems.

### Current state in this repo
Already wired via `common-lib`:

```
common-lib/pom.xml
  micrometer-tracing-bridge-brave        W3C trace context propagation
  zipkin-reporter-brave                  spans → Zipkin HTTP exporter

docker-compose.yml
  zipkin:9411                            Zipkin UI + collector
  MANAGEMENT_ZIPKIN_TRACING_ENDPOINT     every service points here
```

What's **missing**:
- Custom business spans (`@NewSpan` on `placeOrder`, `chargeCard`).
- Verification: have you actually opened Zipkin and traced a request end to end?
- Trace context propagation through Kafka (producer side adds headers, consumer side continues the trace).

### Implementation steps

**1.1 Verify end-to-end trace works today**
```bash
# Start the stack
docker compose up -d zipkin
# Start eureka, config, api-gateway, order-service, payment-service, notification
# Hit an endpoint through the gateway with a known correlation id
curl -u admin:admin123 -H "X-Correlation-Id: trace-test-1" \
     -X POST http://localhost:9000/orders \
     -H "Content-Type: application/json" \
     -d '{"productId":"P-1","quantity":2}'
# Open http://localhost:9411 and search by traceId
```
Expected: one trace, multiple spans across order-service and payment-service.
If spans stop at gateway, see troubleshooting below.

**1.2 Add custom business spans**
Add `@NewSpan("place-order")` on `OrderService.placeOrder(…)`. Add
`@SpanTag("order.id")` on the parameter you want surfaced in Zipkin.

```java
// order-service/.../service/OrderService.java
@NewSpan("place-order")
public Order placeOrder(@SpanTag("order.product") String productId, int qty) { ... }
```

Dependency (already pulled in by `micrometer-tracing`):
```xml
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-tracing-annotations</artifactId>
</dependency>
```

**1.3 Propagate trace context through Kafka**
Brave Kafka instrumentation adds `b3`/`traceparent` headers on producer; the
consumer continues the trace. Confirm `spring-kafka` is being auto-wrapped —
if the trace breaks at the Kafka hop, add:
```xml
<dependency>
  <groupId>io.zipkin.brave</groupId>
  <artifactId>brave-instrumentation-kafka-clients</artifactId>
</dependency>
```
and register the `TracingProducerInterceptor` / `TracingConsumerInterceptor`
on the Kafka client configs.

**1.4 Sampling (production-realism)**
Default is 100% sampling (`management.tracing.sampling.probability=1.0`). In
`application.yml` of each service, drop to 10% for a prod profile:
```yaml
management:
  tracing:
    sampling:
      probability: ${TRACE_SAMPLE_RATE:1.0}
```

### Verify
- Zipkin UI shows a trace with ≥4 spans: `api-gateway` → `order-service` → `kafka-send` → `payment-service` → `notification`.
- The W3C `traceparent` header appears on inter-service HTTP calls (`curl -v`).
- Logs on every service for the same request share the same `traceId`.

### Interview talking points
- Difference between **sampling at ingest** (what % of traces you keep) vs **tail-based sampling** (keep traces that errored or were slow).
- W3C `traceparent` vs older B3 headers — Micrometer bridges to both.
- Why you'd pick OpenTelemetry over Zipkin-native: OTel is vendor-neutral; you can swap Zipkin → Tempo → Honeycomb without code changes.
- Trace context propagation across async boundaries (Kafka, @Async, thread pools) is the hard part — it's not "free."

---

## 2. Metrics + dashboards

### What it is
Each service exports counters/gauges/histograms via `/actuator/prometheus`.
Prometheus scrapes them on an interval. Grafana queries Prometheus to render
dashboards. The core "RED" metrics interviewers want to see:

- **Rate** — requests/sec per endpoint
- **Errors** — error rate per endpoint
- **Duration** — p50/p95/p99 latency per endpoint

### Why interviewers ask
"How do you know your service is healthy?" "What's your SLO and how do you
measure error budget?" You can't answer these without metrics.

### Current state in this repo
- ✅ Every service exposes `/actuator/prometheus` (via `common-lib`).
- ❌ No Prometheus server scraping them.
- ❌ No Grafana.

### Implementation steps

**2.1 Add Prometheus + Grafana to `docker-compose.yml`**

```yaml
# docker-compose.yml (add)
  prometheus:
    image: prom/prometheus:latest
    container_name: prometheus
    ports: ["9090:9090"]
    volumes:
      - ./observability/prometheus.yml:/etc/prometheus/prometheus.yml:ro
    networks: [microservices-net]

  grafana:
    image: grafana/grafana:latest
    container_name: grafana
    ports: ["3000:3000"]
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin
      GF_AUTH_ANONYMOUS_ENABLED: "true"
      GF_AUTH_ANONYMOUS_ORG_ROLE: "Viewer"
    volumes:
      - ./observability/grafana/provisioning:/etc/grafana/provisioning:ro
      - ./observability/grafana/dashboards:/var/lib/grafana/dashboards:ro
    networks: [microservices-net]
```

**2.2 Prometheus scrape config**

```yaml
# observability/prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'microservices'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets:
          - 'api-gateway:9000'
          - 'order-service:9020'
          - 'payment-service:9021'
          - 'product-service:9022'
          - 'user-service:9023'
          - 'notification:9024'
          - 'auth-server:9010'
```

Note: Prometheus scrapes service names on the Docker network, not localhost.
Make sure each service exposes its port in `docker-compose.yml` AND is on
`microservices-net`.

Alternative (more mature): **Eureka service discovery**. Prometheus has an
Eureka SD module — add `eureka_sd_configs` so you don't have to list each
target. Interview win if you do this.

**2.3 Expose the right actuator endpoints**

In each service's `application.yml` (already done via common-lib defaults —
verify):
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    tags:
      application: ${spring.application.name}   # critical: lets you filter per service
    distribution:
      percentiles-histogram:
        http.server.requests: true              # enables p95/p99
```

**2.4 Provision Grafana dashboards automatically**

```yaml
# observability/grafana/provisioning/datasources/prometheus.yml
apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    url: http://prometheus:9090
    isDefault: true
```

Import ready-made dashboards (don't build from scratch at first):
- **JVM Micrometer** — Grafana dashboard ID `4701`
- **Spring Boot 2.1+ Statistics** — ID `11378`
- **Spring Boot APM Dashboard** — ID `12900`

Add a `dashboards.yml` under `provisioning/dashboards/` and drop the JSON files from Grafana's library.

**2.5 Add business metrics (not just HTTP)**

In `OrderService`:
```java
private final Counter ordersPlaced;
private final Timer paymentLatency;

public OrderService(MeterRegistry registry) {
  this.ordersPlaced = Counter.builder("orders.placed.total")
      .description("Orders successfully placed")
      .tag("service", "order")
      .register(registry);
  this.paymentLatency = Timer.builder("payment.call.latency")
      .publishPercentiles(0.5, 0.95, 0.99)
      .register(registry);
}
```
Then increment on success. These "golden signals" + business counters are
what interviewers want to see on a dashboard.

### Verify
- `http://localhost:9090/targets` — every service shows `UP`.
- `http://localhost:3000` (admin/admin) — JVM dashboard shows heap/GC per service.
- Hit `/orders` 100 times; `orders.placed.total` counter increments; p95 shows up on the Spring Boot dashboard.

### Interview talking points
- **Pull vs push** — Prometheus pulls (scrapes). Push gateways exist for short-lived jobs. Why pull wins: easier service discovery, no "lost" metrics from a crashed pusher.
- **Cardinality explosion** — never tag metrics with `userId` or `orderId`. Each unique tag combo is a new timeseries; a million users = a million series = OOM.
- **RED vs USE** — RED (Rate/Errors/Duration) for request-driven services, USE (Utilization/Saturation/Errors) for resources like CPU/disk.
- **Histogram vs summary** — histograms let you aggregate across services; summaries don't. Always histograms for latency.

---

## 3. Centralized logging

### What it is
Every service writes JSON logs. A collector (Promtail) tails the files or
container stdout, pushes to a log store (Loki). You query logs in Grafana
alongside metrics and traces.

### Why interviewers ask
"A user complains about order 12345 — walk me through how you find what
happened." Answer: find the trace by `orderId`, pivot from trace to logs by
`traceId`, read the error. If you only have logs on 15 servers you have to
`grep` 15 places.

### Current state in this repo
- ✅ `logback-observability.xml` (via common-lib) emits JSON with `traceId`, `spanId`, `correlationId`, `service` fields.
- ❌ Logs only land on each container's stdout; no central store.

### Implementation steps

**3.1 Add Loki + Promtail to compose**

```yaml
  loki:
    image: grafana/loki:latest
    container_name: loki
    ports: ["3100:3100"]
    networks: [microservices-net]

  promtail:
    image: grafana/promtail:latest
    container_name: promtail
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
      - ./observability/promtail-config.yml:/etc/promtail/config.yml:ro
    command: -config.file=/etc/promtail/config.yml
    networks: [microservices-net]
```

**3.2 Promtail config — scrape Docker container logs**

```yaml
# observability/promtail-config.yml
server:
  http_listen_port: 9080

positions:
  filename: /tmp/positions.yaml

clients:
  - url: http://loki:3100/loki/api/v1/push

scrape_configs:
  - job_name: docker
    docker_sd_configs:
      - host: unix:///var/run/docker.sock
        refresh_interval: 10s
    relabel_configs:
      - source_labels: ['__meta_docker_container_name']
        target_label: 'container'
      - source_labels: ['__meta_docker_container_label_com_docker_compose_service']
        target_label: 'service'
    pipeline_stages:
      - json:
          expressions:
            level: level
            traceId: traceId
            correlationId: correlationId
      - labels:
          level:
```
Key choice: expose `level` as a label (low cardinality — OK), but **never**
expose `traceId` as a label (high cardinality — would blow up Loki). Keep
`traceId` as a parsed JSON field so you can still filter but it doesn't become
a label index.

**3.3 Add Loki as a Grafana datasource**

```yaml
# observability/grafana/provisioning/datasources/loki.yml
apiVersion: 1
datasources:
  - name: Loki
    type: loki
    url: http://loki:3100
    jsonData:
      derivedFields:
        - name: TraceID
          matcherRegex: '"traceId":"(\w+)"'
          url: 'http://zipkin:9411/zipkin/traces/${__value.raw}'
          datasourceUid: zipkin
```
That `derivedFields` block is the magic: in Grafana logs, every `traceId`
becomes a clickable link that opens the trace in Zipkin.

**3.4 Query examples (LogQL)**

```logql
{service="order-service"} |= "ERROR"
{service="order-service"} | json | correlationId="trace-test-1"
sum by (service) (rate({level="ERROR"}[5m]))
```

### Verify
- `http://localhost:3000` → Explore → Loki → `{service="order-service"}` shows logs.
- Click a `traceId` in a log line → opens Zipkin to that trace.
- Hit a bad request → ERROR count rises on your log-rate panel.

### Interview talking points
- **Why Loki over ELK** — Loki indexes labels only, not full text; cheaper for high log volume. ELK indexes everything; richer queries, more cost.
- **Structured vs unstructured logs** — if you log `log.info("User " + id + " failed")`, you can't query by `user_id`. JSON with MDC fields is the only way to grep across millions of lines reliably.
- **Three pillars of observability** — logs (what), metrics (how much), traces (where). You need all three; this stack gives you all three in Grafana.
- **Log level control at runtime** — Spring Boot Actuator `/actuator/loggers` lets you flip a package to DEBUG without a redeploy. Mention this.

---

## 4. Resilience4j

### What it is
A library of fault-tolerance primitives:
- **Circuit breaker** — stop calling a dead downstream after N failures; try again after a cooldown.
- **Retry** — retry transient failures with backoff + jitter.
- **Bulkhead** — cap concurrent calls to one dependency so it can't exhaust your thread pool.
- **Time limiter** — fail fast if a call exceeds a timeout.
- **Rate limiter** — client-side throttle (so you don't DOS your own downstream).

### Why interviewers ask
"What happens to your order service if payment service is down for 5 minutes?"
Right answer: circuit breaker opens, orders degrade gracefully with a
fallback; order service doesn't lock up all its threads waiting.

### Current state in this repo
- ✅ `ProductService` in order-service has `@CircuitBreaker + @Retry + @Bulkhead`.
- ✅ API gateway has `CircuitBreaker` + `RequestRateLimiter` filters on routes.
- ⚠️ `order → payment`, `order → user`, `notification → anywhere` have **no** resilience.

### Implementation steps

**4.1 Add dependency to any service missing it**
```xml
<dependency>
  <groupId>io.github.resilience4j</groupId>
  <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
```

**4.2 Config profile per downstream**

```yaml
# order-service/src/main/resources/application.yml
resilience4j:
  circuitbreaker:
    instances:
      paymentClient:
        slidingWindowSize: 20
        failureRateThreshold: 50          # open after 50% of 20 calls fail
        waitDurationInOpenState: 30s      # cool down 30s before half-open
        permittedNumberOfCallsInHalfOpenState: 3
        slowCallRateThreshold: 50
        slowCallDurationThreshold: 2s
  retry:
    instances:
      paymentClient:
        maxAttempts: 3
        waitDuration: 500ms
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - java.io.IOException
          - org.springframework.web.client.ResourceAccessException
  bulkhead:
    instances:
      paymentClient:
        maxConcurrentCalls: 10
  timelimiter:
    instances:
      paymentClient:
        timeoutDuration: 3s
```

**4.3 Annotate the client call**

```java
// order-service/.../client/PaymentClient.java
@CircuitBreaker(name = "paymentClient", fallbackMethod = "paymentFallback")
@Retry(name = "paymentClient")
@Bulkhead(name = "paymentClient")
@TimeLimiter(name = "paymentClient")
public CompletableFuture<PaymentResult> charge(ChargeRequest req) {
  return CompletableFuture.supplyAsync(() -> restClient.post().body(req).retrieve().body(PaymentResult.class));
}

private CompletableFuture<PaymentResult> paymentFallback(ChargeRequest req, Throwable t) {
  log.warn("Payment call failed, queuing for later retry: {}", t.toString());
  // enqueue to outbox for retry — do NOT fail the order silently
  outbox.enqueue(req);
  return CompletableFuture.completedFuture(PaymentResult.pending(req.orderId()));
}
```
Order of annotations matters: `TimeLimiter → CircuitBreaker → Retry → Bulkhead` from outer to inner. Spring applies them outer-first.

**4.4 Emit resilience metrics to Prometheus**
```yaml
management:
  health:
    circuitbreakers:
      enabled: true
  metrics:
    tags:
      application: ${spring.application.name}
```
Resilience4j auto-registers metrics with Micrometer:
- `resilience4j.circuitbreaker.state`
- `resilience4j.retry.calls`
- `resilience4j.bulkhead.available.concurrent.calls`

Build a Grafana panel: "payment circuit breaker state over time."

### Verify
- Stop payment-service. Hit `/orders` 20 times.
- First few: timeout + retry. After threshold: fast-fail via fallback.
- Grafana shows `circuitbreaker.state` flipping CLOSED → OPEN.
- Start payment-service back up. After `waitDurationInOpenState`, state goes HALF_OPEN → CLOSED.

### Interview talking points
- **Circuit breaker states** — CLOSED (normal), OPEN (fast-fail), HALF_OPEN (trial calls). Draw this on a whiteboard.
- **Retry only idempotent operations** — never retry a `POST /charge` without an idempotency key or you'll double-charge. Ties into Section 5.
- **Thundering herd** — if 1000 clients all retry at the same time after a downstream recovers, they re-crash it. Fix: exponential backoff **with jitter**.
- **Bulkhead vs rate limiter** — bulkhead caps concurrency (threads in flight); rate limiter caps throughput (requests per second). Different failure modes.
- **Resilience4j vs Hystrix** — Hystrix is dead. Resilience4j is lightweight (no thread pool per command), Java 8+, Spring-first.

---

## 5. Idempotency keys

### What it is
Client generates a UUID per logical operation and sends it as
`Idempotency-Key: <uuid>` on `POST /orders`. Server stores
`(key → response hash + status)` in Redis/DB with a TTL (24h). If the same
key arrives again, server returns the stored response instead of re-executing.

### Why interviewers ask
"User clicks 'Buy' twice because the network stalled. How do you prevent two
orders?" Also: "Your payment retry logic fired twice because of a transient
error. How do you prevent two charges?"

### Current state in this repo
- ✅ `IdempotencyGuard` exists in `common-lib`, used by `PaymentCommandProcessor` (saga, keyed on `sagaId`) and `PaymentEventProcessor` (consumer dedup).
- ❌ No HTTP-layer `Idempotency-Key` header handling on `POST /orders` or `POST /payments` from external clients.

### Implementation steps

**5.1 Define the storage schema**

```sql
CREATE TABLE idempotency_record (
  key          VARCHAR(64)  PRIMARY KEY,     -- client-provided key, scoped per endpoint
  endpoint     VARCHAR(255) NOT NULL,         -- e.g. "POST /orders"
  user_id      VARCHAR(64)  NOT NULL,         -- scope the key per user
  request_hash VARCHAR(64)  NOT NULL,         -- SHA-256 of request body
  status_code  INT          NOT NULL,
  response     TEXT         NOT NULL,         -- stored JSON body
  created_at   TIMESTAMP    NOT NULL,
  expires_at   TIMESTAMP    NOT NULL          -- TTL cleanup
);
CREATE INDEX idx_idem_expires ON idempotency_record(expires_at);
```

Scope the key by `user_id + endpoint` so key `abc` from user A doesn't clash
with key `abc` from user B.

**5.2 Build a Servlet filter (or interceptor)**

```java
// common-lib/.../idempotency/IdempotencyFilter.java
public class IdempotencyFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
    String key = req.getHeader("Idempotency-Key");
    if (key == null || !isWriteMethod(req)) { chain.doFilter(req, res); return; }

    String userId = currentUserId(req);
    String bodyHash = sha256(cachedBody(req));

    Optional<IdempotencyRecord> existing = store.find(key, req.getRequestURI(), userId);
    if (existing.isPresent()) {
      IdempotencyRecord rec = existing.get();
      if (!rec.requestHash().equals(bodyHash)) {
        res.setStatus(422);
        res.getWriter().write("""{"error":"Idempotency-Key reused with different body"}""");
        return;
      }
      // Replay stored response
      res.setStatus(rec.statusCode());
      res.getWriter().write(rec.response());
      return;
    }

    // Wrap response to capture body, then store on success
    ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(res);
    chain.doFilter(req, wrapped);
    if (wrapped.getStatus() < 500) {
      store.save(new IdempotencyRecord(key, req.getRequestURI(), userId, bodyHash,
          wrapped.getStatus(), new String(wrapped.getContentAsByteArray()),
          Instant.now(), Instant.now().plus(24, HOURS)));
    }
    wrapped.copyBodyToResponse();
  }
}
```

**5.3 Register per service — opt in**

Not every endpoint should be idempotency-guarded. Add an annotation
`@Idempotent` and only run the filter if the handler is annotated. Or
whitelist endpoints via config:
```yaml
idempotency:
  endpoints:
    - POST /orders
    - POST /payments/charge
    - POST /payments/refund
```

**5.4 Return the key in response headers**
```
HTTP/1.1 201 Created
Idempotency-Key: 5e9f...
```
So clients know their key was honored.

**5.5 Clean up expired keys**
Scheduled job every hour: `DELETE FROM idempotency_record WHERE expires_at < NOW()`.

### Verify
```bash
KEY=$(uuidgen)
# First call — creates order
curl -u admin:admin123 -H "Idempotency-Key: $KEY" \
     -X POST http://localhost:9000/orders \
     -H "Content-Type: application/json" \
     -d '{"productId":"P-1","quantity":2}'
# → 201 Created, orderId=o-1

# Replay same key — returns same response, no new order
curl -u admin:admin123 -H "Idempotency-Key: $KEY" \
     -X POST http://localhost:9000/orders \
     -H "Content-Type: application/json" \
     -d '{"productId":"P-1","quantity":2}'
# → 201 Created, orderId=o-1 (SAME id, not o-2)

# Same key, different body — rejected
curl -u admin:admin123 -H "Idempotency-Key: $KEY" \
     -X POST http://localhost:9000/orders \
     -H "Content-Type: application/json" \
     -d '{"productId":"P-99","quantity":1}'
# → 422 Unprocessable
```

### Interview talking points
- **Idempotency vs deduplication** — deduplication throws away a dupe silently; idempotency returns the *same stored result*. Clients need the result, not just safety.
- **Why hash the request body** — stops a client from reusing a key for a different payload (common bug / abuse vector).
- **Scoping** — key must be scoped per `(user, endpoint)` — otherwise user A's key could collide with user B's.
- **How Stripe does it** — read their [idempotent requests docs](https://stripe.com/docs/api/idempotent_requests). Standard reference.
- **Where to store** — Redis is faster but ephemeral; Postgres is durable. For payments: Postgres. For analytics writes: Redis is fine.
- **Interaction with retry (Section 4)** — Resilience4j retry **must** send the same `Idempotency-Key` on every attempt or you'll duplicate work.

---

## Suggested build order (2 weekends)

**Weekend 1 — observability plane**
1. (30 min) Verify tracing already works — hit something, open Zipkin.
2. (2 hr) Add Prometheus + Grafana to compose, provision JVM dashboard, verify all targets UP.
3. (1 hr) Add Loki + Promtail, wire `traceId` click-through from Loki to Zipkin.
4. (1 hr) Add a business counter (`orders.placed.total`) and build one custom Grafana panel.

**Weekend 2 — resilience plane**
5. (2 hr) Add Resilience4j to `order → payment` call with CB + Retry + TimeLimiter + fallback to outbox.
6. (2 hr) Build the `IdempotencyFilter` in common-lib, apply to `POST /orders` and `POST /payments`.
7. (1 hr) Chaos test: `docker stop payment-service`, run 50 orders, show breaker opens + requests fallback cleanly, logs/traces/metrics all visible in Grafana.

That final chaos test is your interview demo. Record a screen capture.

---

## Interview-grade one-liner summary

> *"We use Micrometer with the Brave bridge to export W3C trace context to
> Zipkin; Prometheus scrapes `/actuator/prometheus` on 15s intervals and
> Grafana renders JVM + RED dashboards; Loki ingests JSON logs via Promtail
> with a derived-field link from `traceId` to the Zipkin UI. Every cross-service
> client is wrapped in Resilience4j — circuit breaker, retry with jitter,
> bulkhead, time limiter, with fallbacks to an outbox so no work is dropped.
> External POSTs honor `Idempotency-Key` with request-body hashing to prevent
> duplicate submits and safe client retries."*

If you can say that fluently and point to the running stack, you've aced the
observability + resilience section of any microservices interview.

---

## Related docs in this repo

- [observability-multi-service.md](../features/observability-multi-service.md) — how JSON logs + tracing were rolled out
- [concepts/circuit-breaker.md](concepts/circuit-breaker.md)
- [concepts/retry-and-bulkhead.md](concepts/retry-and-bulkhead.md)
- [concepts/distributed-tracing.md](concepts/distributed-tracing.md)
- [concepts/correlation-ids.md](concepts/correlation-ids.md)
- [concepts/outbox-pattern.md](concepts/outbox-pattern.md) — fallback destination for Section 4
