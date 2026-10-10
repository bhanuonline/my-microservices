# API Gateway — Bulkhead per Instance

Concurrent-call limiting for each downstream service. Complements the
CircuitBreaker: CB trips on failure ratio, Bulkhead trips on **in-flight count**.

Name comes from ships — watertight compartments so one leak doesn't sink the
vessel. Same for services: one slow downstream shouldn't consume all gateway
threads.

Companion to [circuit-breaker.md](circuit-breaker.md).

---

## 1. Bulkhead vs Circuit Breaker (interview question)

```
┌────────────────────────────────────────────────────────────────────────┐
│  Circuit Breaker                    Bulkhead                           │
│  ────────────────                   ────────                           │
│  Failure ratio in sliding window    Concurrent count in-flight         │
│  Trips when > 50% failed            Rejects when count > N             │
│  Slow-fails (via fallback)          Rejects fast (BulkheadFullException)│
│  Fixes: "downstream is broken"      Fixes: "downstream is overwhelmed" │
│  Detection: after damage done       Detection: before damage done      │
└────────────────────────────────────────────────────────────────────────┘
```

**Real scenario**:

```
Downstream: user-service, normal p99 = 50ms
Regression: p99 → 2s (DB lock, GC, whatever)

WITHOUT bulkhead:
  100 req/s × 2s = 200 concurrent gateway connections held open
  Netty workers exhausted
  → healthy routes (product, order) can't get through either
  → CASCADING FAILURE

WITH bulkhead (userCB max=15):
  First 15 requests forwarded, held on downstream
  Request 16+ → rejected in <1ms with BulkheadFullException
  → CB filter catches, routes to /fallback/users → 503 fast
  Netty workers stay available for other routes
  → CONTAINED FAILURE
```

**Interview soundbite**: *"CB protects the downstream from itself. Bulkhead
protects the gateway from a slow downstream."*

---

## 2. Semaphore vs ThreadPool bulkhead

Resilience4j has two implementations:

| Type | Fit | Cost |
|---|---|---|
| **SemaphoreBulkhead** ✓ | Reactive gateway (Netty) | In-process counter, zero threads |
| ThreadPoolBulkhead | Wrapping blocking code | Dedicated pool — wrong for Netty |

Reactive gateway must use Semaphore. ThreadPoolBulkhead would introduce a
blocking pool that fights Netty's non-blocking model.

We use `SemaphoreBulkhead` via Resilience4j's `BulkheadOperator` reactive adapter.

---

## 3. Architecture

```
                    ┌──────────────────────────────────────────────────────────────────┐
                    │           API Gateway :8080                                      │
                    │                                                                  │
Client ─────▶       │  Route filter chain:                                             │
                    │                                                                  │
                    │  RequestRateLimiter                                              │
                    │        │                                                         │
                    │  Bulkhead=userCB       ← acquires semaphore permit               │
                    │        │                  full → BulkheadFullException           │
                    │        │                  else → hold permit, invoke chain       │
                    │        ▼                                                         │
                    │  CircuitBreaker (name=userCB, fallbackUri=forward:/fallback/users)│
                    │        │                                                         │
                    │        │ if OPEN → fallbackUri                                   │
                    │        │ if BulkheadFullException → fallbackUri (CB catches)     │
                    │        │ else → downstream                                       │
                    │        ▼                                                         │
                    │  Retry / TokenRelay / downstream call                            │
                    │                                                                  │
                    │  FallbackController /fallback/{service}:                         │
                    │    exchange attr gateway.bulkhead.full == true → bulkhead-full   │
                    │    exception on exchange = CallNotPermittedException → cb-open   │
                    │    exception = TimeoutException → timeout                        │
                    │    ...                                                           │
                    │    sets X-Fallback-Reason header + JSON body.reason field        │
                    └──────────────────────────────────────────────────────────────────┘
```

**Order note**: `Bulkhead` filter must be listed BEFORE `CircuitBreaker` in
`filters:`. Spring Cloud Gateway applies filters in YAML order, so this makes
bulkhead the outer wrapper — a saturated bulkhead never lets the request reach
the CB.

---

## 4. Design decisions

### 4a. max-wait-duration = 0 (fast-fail) vs > 0 (brief queue)

```
max-wait-duration = 0ms:
  Bulkhead full → INSTANT BulkheadFullException
  Client sees 503 in ~1ms
  Best UX for user-facing APIs (fail fast, client retries)

max-wait-duration = 100ms:
  Bulkhead full → wait up to 100ms for a permit
  Slot frees in time → proceed
  Else → BulkheadFullException
  Best for bursty m2m traffic where slots often free quickly
```

Current defaults:
- `userCB` — 50ms wait (users tolerate small delay, save on retries)
- `productCB` — 0ms (high volume, fast-fail)
- `orderCB` — 100ms (writes are expensive, brief queue OK)

### 4b. BulkheadFullException should NOT open the CB

Bulkhead rejection = gateway self-protection. Downstream is fine (we didn't
even try to call it). Counting it as a CB failure would cascade:

```
Traffic spike → bulkhead saturates → BulkheadFullException emitted
   → CB counts as failure → failure rate crosses threshold
   → CB opens → subsequent traffic hits fallback
   → cascading open even though downstream was healthy
```

Fix in `CircuitBreakerCustomization`:
```java
.ignoreExceptions(BulkheadFullException.class)
```

Interview point: *"Cascading circuit breaks are hard to reason about. Bulkhead
should be a distinct signal, not folded into CB stats."*

### 4c. Naming convention: bulkhead name == CB name

The `Bulkhead=userCB` filter uses the same instance name as the CircuitBreaker.
Reasons:
- One conceptual "protected resource" per name
- Metrics group naturally (`resilience4j_bulkhead_calls{name="userCB"}` +
  `resilience4j_circuitbreaker_calls{name="userCB"}`)
- One YAML config block covers both

### 4d. Client sees 503 — but why?

Three possible reasons a client hits `/fallback/*`:
- Bulkhead full (traffic burst)
- CB open (downstream failing)
- Timeout (downstream slow)

`FallbackController` now inspects the exchange for the triggering cause and adds:
- `X-Fallback-Reason: bulkhead-full` header
- `"reason": "bulkhead-full"` in JSON body

Extension: dashboards can alert on ratio of each reason.

### 4e. Sizing the bulkhead

Little's Law: `concurrency = throughput × latency`

Example:
```
Expected traffic:  100 req/s to user-service
p99 latency:       200ms
Concurrency:       100 × 0.200 = 20 in-flight at p99

Set bulkhead:      20 × 1.5 (safety) = 30
```

Watch `resilience4j_bulkhead_available_concurrent_calls` in Grafana; tune based
on observed rejection rate. Reject > 0.1% consistently → raise the limit.

### 4f. Per-JVM state

Bulkhead permits live in-process (semaphore in the JVM). No Redis coordination.

Consequence: 3 gateway replicas × bulkhead=10 = 30 total concurrent downstream
calls. Usually fine — the downstream sees the aggregate, and each gateway pod
protects its own worker pool.

For strict global limits, need distributed semaphore (Redis Lua). Rarely worth
the complexity — parked as extension.

---

## 5. Config

```yaml
gateway:
  circuitbreaker:
    enabled: true
    defaults:
      # ... existing CB knobs
      bulkhead:
        max-concurrent-calls: 10
        max-wait-duration: 0ms
    instances:
      userCB:
        failure-rate-threshold: 40
        wait-duration-in-open-state: 15s
        bulkhead:
          max-concurrent-calls: 15
          max-wait-duration: 50ms
      productCB:
        slow-call-duration-threshold: 1s
        bulkhead:
          max-concurrent-calls: 20
          max-wait-duration: 0ms
      orderCB:
        failure-rate-threshold: 70
        bulkhead:
          max-concurrent-calls: 5
          max-wait-duration: 100ms
```

Per-route filter:
```yaml
filters:
  - Bulkhead=userCB
  - name: CircuitBreaker
    args:
      name: userCB
      fallbackUri: forward:/fallback/users
```

---

## 6. Files added / changed

```
api-gateway/
└── src/main/
    ├── java/com/example/apigateway/
    │   ├── bulkhead/                                       (NEW package)
    │   │   └── BulkheadGatewayFilterFactory.java           (NEW — wraps with BulkheadOperator)
    │   ├── config/
    │   │   ├── CircuitBreakerProperties.java               (+ Bulkhead nested class)
    │   │   └── CircuitBreakerCustomization.java            (BulkheadRegistry bean + ignoreExceptions)
    │   └── controller/
    │       └── FallbackController.java                     (X-Fallback-Reason header)
    └── resources/
        └── application.yml                                 (bulkhead: blocks + Bulkhead= filters)

docs/microservices/api-gateway/
└── bulkhead.md                                             (this file)
```

No new dependencies — Resilience4j Bulkhead + BulkheadOperator ship with the
existing `resilience4j-spring-boot3` + Reactor bindings.

---

## 7. Verification

### 7.1 Confirm bulkhead beans present at boot

```bash
mvn -pl infra/api-gateway clean spring-boot:run

# On boot, look for:
# Bulkhead 'userCB' initialized: maxConcurrent=15 maxWait=PT0.05S
# Bulkhead 'productCB' initialized: maxConcurrent=20 maxWait=PT0S
# Bulkhead 'orderCB' initialized: maxConcurrent=5 maxWait=PT0.1S
```

### 7.2 Check bulkhead metric via actuator

```bash
curl -s "http://localhost:8080/actuator/metrics/resilience4j.bulkhead.available.concurrent.calls?tag=name:userCB" | jq
# {
#   "name": "resilience4j.bulkhead.available.concurrent.calls",
#   "measurements": [ { "statistic": "VALUE", "value": 15.0 } ],
#   "availableTags": [{"tag":"name","values":["userCB","productCB","orderCB"]}]
# }
```

### 7.3 Trigger saturation

Slow the downstream (add `Thread.sleep(2000)` to a test endpoint in
user-service), then blast concurrent requests:

```bash
TOKEN=$(curl -s -u admin:admin123 \
  -d "grant_type=client_credentials&scope=read" \
  http://localhost:9010/oauth2/token | jq -r .access_token)

# 30 concurrent requests; userCB bulkhead is 15
seq 1 30 | xargs -n1 -P30 -I{} sh -c "
  curl -s -w '%{http_code} %{time_total}s reason=%header{X-Fallback-Reason}\n' \
    -o /dev/null -H 'Authorization: Bearer $TOKEN' \
    http://localhost:8080/api/v1/users/me
"
# Expected mix:
#   200 2.001s reason=
#   200 2.003s reason=
#   ...  (up to 15)
#   503 0.003s reason=bulkhead-full        ← fast rejection
#   503 0.002s reason=bulkhead-full
```

### 7.4 Watch bulkhead metrics live

```bash
watch -n 0.5 'curl -s "http://localhost:8080/actuator/metrics/resilience4j.bulkhead.available.concurrent.calls?tag=name:userCB" | jq .measurements[0].value'
# Shows 15 → 14 → ... → 0 as slots fill up, then rebounds when calls complete
```

### 7.5 Distinguish fallback reasons

```bash
# 1. Bulkhead saturation → X-Fallback-Reason: bulkhead-full
# (as above)

# 2. CB open (stop user-service, hit repeatedly until CB opens):
curl -i -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/users/me
# X-Fallback-Reason: circuit-breaker-open

# 3. Timeout (slow endpoint > slowCallDurationThreshold=2s):
curl -i -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/users/slow-3s
# X-Fallback-Reason: timeout
```

### 7.6 Confirm BulkheadFullException doesn't count against CB

Blast enough bulkhead rejections to normally trip the CB, then check:

```bash
curl -s http://localhost:8080/actuator/circuitbreakers | jq '.circuitBreakers.userCB'
# state: "CLOSED"  ← would be OPEN if we hadn't .ignoreExceptions()
# bufferedCalls / failedCalls: unchanged despite bulkhead rejections
```

### 7.7 Disable bulkhead per route

Remove the `Bulkhead=userCB` line from that route's filters. Restart. That
route now has unlimited concurrency to downstream (bounded only by Netty workers
+ CB).

---

## 8. Failure modes & mitigations

| Scenario | Behavior | Mitigation |
|---|---|---|
| Bulkhead too tight → false rejections during bursts | 503s under normal traffic | Watch metrics; raise `max-concurrent-calls`; use `max-wait-duration` |
| Bulkhead too loose → gateway overwhelm | Netty workers exhaust | Lower `max-concurrent-calls`; combine with JVM tuning |
| BulkheadFullException incorrectly counting against CB | CB opens on bulkhead saturation | `.ignoreExceptions(BulkheadFullException.class)` — present |
| max-wait-duration too long | Client latency degrades | 0ms for user APIs, small for m2m |
| Bulkhead state per JVM (not global) | 3 pods × 10 = 30 concurrent total | Distributed bulkhead (Redis Lua) — extension |
| Fallback URI has same bulkhead → recursion | Rejection loop possible | `/fallback/**` is a plain controller, no CB/Bulkhead filter — safe |
| Slow downstream + retry inside CB inside Bulkhead | Retries also count as new bulkhead calls | Currently correct — retry doesn't hold the permit across attempts |

---

## 9. Interview cheat-sheet

| Question | Answer |
|---|---|
| Bulkhead vs Circuit Breaker? | CB: failure ratio in window → opens on downstream failure. Bulkhead: concurrency limit → rejects when in-flight count hits max. |
| Use both? | Yes, always together. CB catches slow-fail scenarios; bulkhead prevents fast concurrency exhaustion. |
| Semaphore vs ThreadPool? | Semaphore: in-process counter, zero threads, reactive-compatible. ThreadPool: dedicated pool, wraps blocking code, wrong for Netty. |
| Why per-instance (not global)? | Isolation. Saturating user-service doesn't block product-service. Ship-compartment metaphor. |
| max-wait-duration 0 or > 0? | 0 = fast-fail (user APIs). >0 = brief queue for bursty m2m. |
| Should BulkheadFullException open CB? | No. Different signals — bulkhead is gateway self-protection, not downstream failure. Ignore via `.ignoreExceptions(...)`. |
| How to size? | Little's Law: concurrency = throughput × latency. Then × 1.5 safety. Adjust from observed rejection rate. |
| Client distinguishes bulkhead vs CB fallback? | Custom `X-Fallback-Reason` header + `reason` field in JSON body. Values: bulkhead-full, circuit-breaker-open, timeout, unknown. |
| Metrics? | Auto-registered by Resilience4j: `resilience4j_bulkhead_available_concurrent_calls`, `_max_allowed_concurrent_calls`, `_calls{kind=permitted\|rejected}`. |
| Multi-replica state? | Per-JVM. 3 replicas × 10 = 30 total. Distributed bulkhead via Redis is possible but usually not worth complexity. |
| ThreadPoolBulkhead in reactive gateway? | Anti-pattern. Blocking pool defeats Netty's non-blocking model. Always Semaphore. |
| Filter order? | Bulkhead BEFORE CircuitBreaker in YAML. Bulkhead is the outer wrapper → saturated bulkhead never reaches CB. |
| Interaction with rate limiter? | RL rejects at ingress (client identity). Bulkhead rejects at egress (downstream capacity). Both useful. |

---

## 10. Common pitfalls (interview probes)

1. **ThreadPoolBulkhead in a reactive gateway** — anti-pattern; blocks Netty.
2. **Not calling `.ignoreExceptions(BulkheadFullException.class)`** — cascading CB opens on bulkhead saturation.
3. **Bulkhead AFTER CircuitBreaker in YAML** — CB wraps bulkhead → CB never sees BulkheadFullException, fallback never fires (wrong behavior).
4. **Bulkhead too tight** — false rejections during legitimate bursts.
5. **Bulkhead too loose** — no protection; Netty workers exhaust regardless.
6. **Long `max-wait-duration` on user APIs** — request queues instead of fast-fail; latency creeps.
7. **Assuming bulkhead is distributed** — per-JVM only. Global caps need Redis + Lua.
8. **Fallback URI going through the same bulkhead** — recursive rejection. `/fallback/**` must be a plain controller.
9. **Metrics name mismatch** — `resilience4j_bulkhead_*` vs `resilience4j_circuitbreaker_*`. Separate Grafana panels needed.

---

## 11. Extensions (parked)

- **Distributed bulkhead** via Redis + atomic INCR/DECR with Lua compare-and-decr for cross-JVM cap. Adds latency + Redis dependency. Rarely worth it.
- **Adaptive concurrency** — Netflix's concurrency-limits library (based on TCP Vegas). Auto-tunes limits from observed latency.
- **Per-endpoint bulkhead** — split `userBulkhead` into `userReadBulkhead` + `userWriteBulkhead` for read/write asymmetric limits.
- **Bulkhead + `@RefreshScope`** — hot-reload limits from config server during incidents.
- **Response header `X-Bulkhead-Available`** — expose current permit count so clients can back off proactively.
- **Alert rules** — Grafana alert: `resilience4j_bulkhead_calls{kind="rejected"}` > N/min for 5m → page oncall.
- **ThreadPoolBulkhead for legacy blocking backends** — separate CB/Bulkhead config path for the one endpoint that still uses `RestTemplate`.
- **Adaptive sizing via Little's Law** — read p99 latency from Micrometer, recompute optimal concurrency, publish refresh.
