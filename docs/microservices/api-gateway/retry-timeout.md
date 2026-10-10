# API Gateway — Retry + Timeout

Handles **transient** downstream failures via automatic retry with exponential backoff,
bounded by connect + response timeouts. Layered inside the CircuitBreaker so retries
still count toward the CB's failure window.

---

## 1. Where this fits

```
Client                                                 downstream service
  │                                                              ▲
  ▼                                                              │
┌───────────────────────────────────────────────────────────────┐│
│  Gateway                                                      ││
│                                                               ││
│  RateLimiter    →  CircuitBreaker  →  Retry  →  HTTP call ────┘│
│  (ingress abuse)   (fail fast)        (transient)              │
│                                                                │
│  429               503 fallback       retry N times            │
└────────────────────────────────────────────────────────────────┘
```

**Order matters** — Retry is inside CB. Each retry attempt is a call from the CB's
point of view, so:

- 3 retries × failing service = 3 failures recorded → CB opens at threshold ✓
- Reversed (Retry outside CB) = CB sees 1 attempt total → never trips → clients
  hammer a dying service forever

In Spring Cloud Gateway YAML, filter order in `filters:` = execution order (outer to
inner). We list `CircuitBreaker` before `Retry`, so CB wraps Retry.

---

## 2. What retry does (and doesn't) fix

| Failure class | Retry helps? | Example |
|---|---|---|
| Transient network error | ✓ | Connection reset, DNS blip |
| Downstream restarting | ✓ | Rolling deploy, single instance bounce |
| Downstream overloaded (5xx) | ✓ (with backoff) | Give it breathing room |
| Downstream logic error (500 always) | ✗ | Bug — retry won't fix |
| Client sent bad payload (400) | ✗ | Retry can't make invalid valid |
| Auth error (401/403) | ✗ | Token isn't going to appear |
| Downstream slow (30s response) | ✗ *by itself* | Need timeout + retry together |

---

## 3. Idempotency — the interview centerpiece

```
Safe to retry:                   Unsafe to retry:
  GET  /users/123                   POST /orders    ← creates duplicate order
  PUT  /users/123 {...}             POST /payments  ← charges twice
  DELETE /users/123                 POST /emails    ← sends twice
  HEAD /users/123
  OPTIONS /users
```

**Rule of thumb**: only auto-retry idempotent methods (GET, PUT, DELETE, HEAD,
OPTIONS). POST/PATCH require an **Idempotency-Key** header so the server can dedupe:

```
POST /orders
Idempotency-Key: 8f9a2c4d-...
```

Server persists the key + response for N minutes. Second call with same key returns
the cached response instead of processing again. Stripe's approach.

**In this build**: our `order-service` route has NO Retry filter at all — safest
default. Add idempotency-key filter later as an extension.

---

## 4. Exponential backoff + jitter

```
attempt 1: fail
attempt 2: wait 100ms  (firstBackoff)
attempt 3: wait 200ms  (100 * factor=2)
attempt 4: wait 400ms  (200 * factor=2)
                        ...capped at maxBackoff=2s
```

**Why backoff**: hammering a failing service every 10ms just makes it fail harder.
Exponential gives it time to recover.

**Why jitter**: without it, 10,000 clients all retrying at exactly `t+100ms` → thundering
herd → service dies again on the recovery burst.

Spring Cloud Gateway's Retry filter has `basedOnPreviousValue`:

- `true`  → strict exponential (no jitter, deterministic)
- `false` → uses `firstBackoff * (factor ^ attempt)` — closer to real jitter behavior

For true random jitter you'd need a custom `Retry` bean using Reactor's
`Retry.backoff().jitter(0.5)`. Not built here — mention it in interviews.

---

## 5. Two levels of timeout

```
                     ┌── per-attempt (response-timeout)
                     │   cancels ONE hung call
                     ▼
Request ──┐          ┌────────────────┐          ┌─────────────┐
          │  connect │   HTTP request │   read   │  response   │
          │  timeout │   sent         │          │   ...       │
          └─▶────────┴────────────────┴──────────┴─────────────┘
             ▲       ▲                                         ▲
             │       │                                         │
             │       └── connect-timeout (TCP handshake)       │
             │                                                 │
             └── global-response-timeout ──────────────────────┘
                 (bounds TOTAL wall-clock incl. all retries)
```

| Timeout | Purpose | Config |
|---|---|---|
| Connect | TCP handshake | `spring.cloud.gateway.httpclient.connect-timeout` (ms) |
| Response | Wait for headers/body from downstream, per attempt | `spring.cloud.gateway.httpclient.response-timeout` |

**Interview point:** without a per-attempt response-timeout, retry can't recover from a
hung call — Reactor Netty will wait forever. Set both connect + response timeouts, always.

---

## 6. Config

```yaml
spring:
  cloud:
    gateway:
      httpclient:
        connect-timeout: 1000            # ms
        response-timeout: 5s             # per attempt
      routes:
        - id: user-service
          filters:
            - name: CircuitBreaker
              args: { name: userCB, fallbackUri: forward:/fallback/users }
            - name: Retry
              args:
                retries: 3
                methods: GET,PUT,DELETE
                statuses: BAD_GATEWAY,SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT
                backoff:
                  firstBackoff: 100ms
                  maxBackoff: 2s
                  factor: 2
                  basedOnPreviousValue: false

gateway:
  retry:
    enabled: true                        # mirror config for future admin/refresh
    defaults: { retries: 3, methods: [GET, PUT, DELETE], ... }
    instances:
      user-service: { retries: 3 }
      order-service: { retries: 0 }      # never
  timeout:
    enabled: true
    global-response-timeout: 5s
    connect-timeout: 1s
```

**Why two sources of truth?** Spring Cloud Gateway parses filter args at route-build
time — it doesn't read `gateway.retry.*`. That block mirrors the real settings so a
future admin endpoint / `@RefreshScope` extension can display and (eventually) rewrite
route definitions from properties. Same pattern as `gateway.ratelimit.routes.*`.

---

## 7. Files added / changed

```
api-gateway/
├── pom.xml                                              (no changes — built-in filter)
└── src/main/
    ├── java/com/example/apigateway/config/
    │   ├── RetryProperties.java                         (NEW)
    │   ├── TimeoutProperties.java                       (NEW)
    │   └── TimeoutConfig.java                           (NEW — HttpClientCustomizer)
    └── resources/
        ├── application.yml                              (Retry filter + httpclient timeouts + gateway.retry/timeout blocks)
        └── application-noretry.yml                      (NEW — disable profile)
```

---

## 8. Running it

### 8.1 See retries in action

Enable retry debug logging (uncomment in `application.yml`):
```yaml
logging:
  level:
    org.springframework.cloud.gateway.filter.factory.RetryGatewayFilterFactory: TRACE
    reactor.netty.http.client: DEBUG
```

Then stop `user-service` and hit the route:
```bash
TOKEN=$(curl -s -u admin:admin123 \
  -d "grant_type=client_credentials&scope=read" \
  http://localhost:9010/oauth2/token | jq -r .access_token)

time curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/users/me
```

Expected log sequence:
```
retry: attempt #0 failed
retry: attempt #1 will run after 100ms
retry: attempt #1 failed
retry: attempt #2 will run after 200ms
retry: attempt #2 failed
retry: attempt #3 will run after 400ms
retry: attempt #3 failed
→ CB records 3+ failures → opens → 503 fallback
```

Expected `time` output: ~700ms (100 + 200 + 400 backoff + connect timeouts).

### 8.2 Prove POST is NOT retried

```bash
# Stop order-service
time curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"item":"x"}' \
  http://localhost:8080/api/v1/orders

# Expected: 503 immediately (~10ms). No backoff, no retry lines in log.
```

### 8.3 Prove timeout fires

If you have an artificially slow endpoint (add `Thread.sleep(10_000)` to a test
controller in user-service):
```bash
time curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/users/slow
# Expected: ~5s wall-clock, then 504 Gateway Timeout → retry kicks in → CB opens
```

### 8.4 Disable retry only
```bash
mvn -pl infra/api-gateway spring-boot:run -Dspring-boot.run.profiles=noretry

# Kill user-service, hit route
time curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/users/me
# Expected: 503 fallback in ~10ms (no retry delay)
```

### 8.5 See CB state after retry storm
```bash
curl -s http://localhost:8080/actuator/circuitbreakers | jq
# userCB should show bufferedCalls counting each retry attempt
```

---

## 9. Failure modes & mitigations

| Scenario | Behavior | Mitigation |
|---|---|---|
| POST retried (would create dupes) | Not retried — GET/PUT/DELETE only in config | Add Idempotency-Key filter for opt-in POST retry |
| Retry storm on recovery | All clients retry at same instant → service dies again | True jitter (custom Retry bean with `.jitter(0.5)`) |
| CB never opens despite failures | Retry outside CB (wrong order) | Ensure filter order: CircuitBreaker BEFORE Retry in YAML |
| Client waits forever | No response-timeout set | Both connect + response timeouts configured ✓ |
| Retries burn CB's minimum-calls quota fast | CB opens too quickly | Tune `minimum-number-of-calls` higher OR reduce retries per-route |
| Retry on 503 fallback URI | Would retry the fallback controller (usually harmless but pointless) | Statuses list is `BAD_GATEWAY, SERVICE_UNAVAILABLE, GATEWAY_TIMEOUT` from upstream; fallback runs inside gateway so it won't hit this filter |

---

## 10. Interview cheat-sheet

| Question | Answer |
|---|---|
| Which HTTP methods to auto-retry? | GET, PUT, DELETE, HEAD, OPTIONS. Never POST without idempotency key. |
| Why exponential backoff? | Linear retry hammers a dying service; exponential gives recovery time |
| Why jitter? | Prevents thundering-herd retry synchronization |
| Retry INSIDE or OUTSIDE the CB? | INSIDE — otherwise retries never count toward CB's failure window |
| What's an idempotency key? | Client UUID; server dedupes and returns cached response for duplicates (Stripe pattern) |
| Retry policy for 4xx? | Don't. Client bug — retry can't fix it. |
| Timeout without retry — enough? | No. Timeout stops one hung call; can't recover from immediate 503 |
| Global vs per-attempt timeout | Per-attempt: cancel one hung call. Global: bound total wait across retries. Set both. |
| Retry storm — what & how to prevent? | Synchronized retries on recovery kill service again; solution = jitter + backoff + CB |
| POST retried twice — what breaks? | Duplicate order/charge/email. Reason POST needs idempotency key. |
| Difference: response-timeout vs slowCallDurationThreshold (CB) | response-timeout = cancel the call. slowCallDurationThreshold = record it as slow for CB stats. |
| Reactor Retry vs Gateway Retry filter | Gateway Retry filter operates on HTTP status/methods. Reactor `Retry.backoff()` operates on Mono/Flux errors. Filter is the right level for gateway. |

---

## 11. Extensions (parked)

- **Idempotency-Key filter** — custom `GlobalFilter`: on POST/PATCH, look up key in Redis; return cached response if seen, else record after successful response.
- **True jitter** — custom `RetryGatewayFilterFactory` using Reactor's `Retry.backoff(...).jitter(0.5)`.
- **Bulkhead per route** — limit concurrent in-flight calls per downstream.
- **Adaptive timeouts** — read p99 latency from Micrometer; dynamically adjust `response-timeout`.
- **Retry budget** — cap total retries across a time window (e.g. no more than 10% of traffic should be retries) to prevent retry storms globally. See Google SRE book.
