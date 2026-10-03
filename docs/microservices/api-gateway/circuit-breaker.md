# API Gateway — Circuit Breaker & Fallback

Config-driven, per-route circuit breaker on outbound calls using **Resilience4j** via
`spring-cloud-starter-circuitbreaker-reactor-resilience4j`. Combined with the rate
limiter, the gateway now protects **both ends**: rate limiter guards ingress traffic,
circuit breaker guards egress calls to downstream services.

---

## 1. Why a circuit breaker at the gateway?

Downstream services fail — sometimes fast (connection refused), sometimes slow
(timeout, thread starvation). If the gateway keeps hammering a failing service:

- Client-facing latency balloons (waiting on doomed calls)
- The downstream service can't recover (constant retry pressure)
- Gateway threads/connections get consumed → cascading outage

A circuit breaker *stops* calling for a short window when failure is detected, giving
the downstream time to recover and freeing gateway resources.

```
                      Rate limiter                Circuit breaker
                     (client-side guard)         (downstream guard)
                            │                            │
Client ──abuse──▶      429 REJECT             Gateway ──▶  DOWN service ──▶ 5xx
                                                 │             │
                                                 └────CB opens─┘
                                                 (fast-fail 503 fallback)
```

---

## 2. State machine

```
                              failureRate > threshold  OR
                              slowCallRate > threshold
                              (over sliding window)
                         ┌───────────────────────────────────┐
                         ▼                                   │
                    ┌─────────┐                        ┌───────────┐
     Normal ───────▶│ CLOSED  │──────── fails ───────▶│   OPEN    │──┐
     requests       └─────────┘                        └───────────┘  │
     pass through        ▲                                   │        │ waitDurationInOpenState
                         │                                   ▼        │ (e.g. 10s)
                         │ successes ≥ threshold        ┌───────────┐ │
                         └──────────────────  ◀─────────│ HALF_OPEN │◀┘
                                                        └───────────┘
                                              permits N probe calls
```

Three signals can open the breaker:

| Signal | Config key | Meaning |
|---|---|---|
| Failure rate | `failure-rate-threshold` (%) | Fraction of failed calls in window |
| Slow call rate | `slow-call-rate-threshold` (%) | Fraction of calls exceeding `slow-call-duration-threshold` |
| Minimum calls | `minimum-number-of-calls` | Window must be filled before evaluation |

**Common gotcha:** if `minimum-number-of-calls=5` and only 4 requests fail, the CB
stays CLOSED. Interviewers love this: *"why didn't my CB open after 3 failures?"*

---

## 3. Architecture

```
                    ┌──────────────────────────────────────────────────────────────┐
                    │                 API Gateway :8080                            │
Client ──▶          │                                                              │
                    │  CorrelationIdWebFilter                                      │
                    │                                                              │
                    │  ┌────────────────────────────────────────────────────────┐  │
                    │  │  Route: user-service                                   │  │
                    │  │   filters:                                             │  │
                    │  │    1. RequestRateLimiter    ← guards INBOUND traffic   │  │
                    │  │    2. CircuitBreaker        ← guards OUTBOUND calls    │  │
                    │  │         name = userCB                                  │  │
                    │  │         fallbackUri = forward:/fallback/users          │  │
                    │  │    3. TokenRelay                                       │  │
                    │  └────────────────────────────────────────────────────────┘  │
                    │                                                              │
                    │              open ▼                       closed ▼           │
                    │                                                              │
                    │    ┌──────────────────────┐    ┌────────────────────────┐    │
                    │    │  FallbackController  │    │  Actual service call   │    │
                    │    │  /fallback/{svc}     │    │  lb://user-service     │    │
                    │    │  → 503 + JSON body   │    └────────────────────────┘    │
                    │    └──────────────────────┘                                  │
                    └──────────────────────────────────────────────────────────────┘
```

**Filter ordering rationale**:
1. `RequestRateLimiter` first — reject over-limit traffic before it counts against the CB.
2. `CircuitBreaker` second — if downstream is down, short-circuit to fallback.
3. `TokenRelay` last — forward JWT only on calls that will actually reach downstream.

---

## 4. Config schema

```yaml
gateway:
  circuitbreaker:
    enabled: true                          # master switch (@ConditionalOnProperty)
    defaults:
      sliding-window-size: 10
      minimum-number-of-calls: 5
      failure-rate-threshold: 50           # %
      slow-call-rate-threshold: 50         # %
      slow-call-duration-threshold: 2s
      wait-duration-in-open-state: 10s
      permitted-calls-in-half-open-state: 3
    instances:                             # per-CB overrides (merged with defaults)
      userCB:
        failure-rate-threshold: 40         # tighter
        wait-duration-in-open-state: 15s
      productCB:
        slow-call-duration-threshold: 1s
      orderCB:
        failure-rate-threshold: 70         # more lenient — critical writes
```

Each instance name (`userCB`, `productCB`, `orderCB`) must match the `name:` on the
`CircuitBreaker` filter in the route definition. That's the join key between
`gateway.circuitbreaker.instances.*` and `spring.cloud.gateway.routes[].filters[].args.name`.

---

## 5. Files added / changed

```
api-gateway/
├── pom.xml                                                  (+ cb-reactor-resilience4j)
└── src/main/
    ├── java/com/example/apigateway/
    │   ├── config/
    │   │   ├── CircuitBreakerProperties.java                (NEW)
    │   │   ├── CircuitBreakerCustomization.java             (NEW — Customizer bean)
    │   │   └── GatewaySecurityConfig.java                   (added /fallback/** permit)
    │   └── controller/
    │       └── FallbackController.java                       (NEW)
    └── resources/
        ├── application.yml                                   (CB filters + config block)
        └── application-nocb.yml                              (NEW — disable profile)
```

Key design decisions:

- **`CircuitBreakerCustomization`** uses `Customizer<ReactiveResilience4JCircuitBreakerFactory>`.
  This is the *idiomatic* Spring Cloud CircuitBreaker way (register configs at
  bootstrap; filters reference them by name). Alternative — `@Bean CircuitBreakerRegistry` —
  fights the abstraction and won't apply to the Gateway filter.
- **`FallbackController`** uses one endpoint for all services via `{service}` path
  variable. Extension point: read `X-Original-Path` or query the CB state to return
  cached data for GETs.
- **`GatewaySecurityConfig`** permits `/fallback/**` — otherwise the fallback route
  would require JWT auth, defeating the purpose (auth-server itself might be down).

---

## 6. Running it

### 6.1 Prereqs
```bash
docker run -d --name gateway-redis -p 6379:6379 redis:7-alpine   # for rate limiter
mvn -pl eureka-server   spring-boot:run
mvn -pl auth-server     spring-boot:run
mvn -pl user-service    spring-boot:run
mvn -pl api-gateway     spring-boot:run
```

### 6.2 Happy path
```bash
TOKEN=$(curl -s -u admin:admin123 \
  -d "grant_type=client_credentials&scope=read" \
  http://localhost:9010/oauth2/token | jq -r .access_token)

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/users/me
# 200 OK
```

### 6.3 Trigger the circuit breaker

Stop `user-service` (Ctrl-C its process), then:

```bash
for i in $(seq 1 10); do
  curl -s -o /dev/null -w "%{http_code} " \
    -H "Authorization: Bearer $TOKEN" \
    http://localhost:8080/api/v1/users/me
done
echo
# Expected: first ~5 requests are 503 (real failures during window-fill),
# then CB opens and remaining calls are fast-503s from FallbackController.
```

### 6.4 Inspect state

```bash
# CB state (uses management.health.circuitbreakers)
curl -s http://localhost:8080/actuator/health | jq
# {
#   "status": "UP",
#   "components": {
#     "circuitBreakers": {
#       "status": "UP",
#       "details": {
#         "userCB": { "status": "OPEN", ... }
#       }
#     }
#   }
# }

# Or the dedicated actuator endpoints:
curl -s http://localhost:8080/actuator/circuitbreakers | jq
curl -s http://localhost:8080/actuator/circuitbreakerevents/userCB | jq
```

### 6.5 Recovery — HALF_OPEN probe

```bash
# Restart user-service, then wait for waitDurationInOpenState (15s for userCB)
sleep 16

# First calls after the wait are HALF_OPEN probes (permittedCallsInHalfOpenState=3)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/users/me
# 200 → success recorded in HALF_OPEN

# After enough successes, CB → CLOSED
curl -s http://localhost:8080/actuator/circuitbreakers | jq '.circuitBreakers.userCB.state'
# "CLOSED"
```

### 6.6 Direct fallback test
```bash
curl -i http://localhost:8080/fallback/users
# HTTP/1.1 503 Service Unavailable
# {"error":"service_unavailable","service":"users",...}
```

### 6.7 Disable everything
```bash
mvn -pl api-gateway spring-boot:run -Dspring-boot.run.profiles=nocb

# Kill user-service, hit the route → get raw 5xx (no CB, no fallback)
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/users/me
```

---

## 7. Failure modes & mitigations

| Scenario | Behavior | Mitigation |
|---|---|---|
| Downstream slow (2s+) | Counts as slow call → `slow-call-rate-threshold` may open CB | Tune per instance; add TimeLimiter |
| Downstream returning 4xx (not 5xx) | Not counted as failure by default | Add custom `recordExceptions` if 4xx should count |
| Fallback controller itself down | 500 from gateway (nothing catches it) | Keep fallback code trivial + covered by health check |
| CB stays CLOSED despite obvious failures | `minimum-number-of-calls` not met | Lower threshold or trigger more traffic |
| Multiple gateway replicas | Each has its OWN CB state (per-JVM) | Acceptable — no shared coordination needed, avoids thundering herd on recovery |
| Fallback loop (CB on `/fallback/**`) | Infinite recursion | Fallback path isn't in a route with CB filter — kept as controller ✓ |

---

## 8. Interview cheat-sheet

| Question | Answer |
|---|---|
| Rate limiter vs circuit breaker? | RL: reject client abuse (ingress). CB: stop calling failing downstream (egress). |
| Why sliding window over fixed count? | Fixed count is stale — a burst of successes hides ongoing failure pattern |
| Count-based vs time-based sliding window? | Count = simpler math, uneven wall-clock. Time = better for variable RPS. |
| Why `slowCallRateThreshold` matters | Slow failing is worse than fast failing — thread pool exhaustion, cascading queue growth |
| Bulkhead vs CB? | Bulkhead limits concurrent calls (isolation). CB stops calls entirely (fast-fail). Often combined. |
| Retry + CB interaction? | Retry INSIDE CB — each retry counts as one call in the CB window. Wrong order = CB never trips. |
| Why fallback route needs a controller, not a route? | If fallback URI was another gateway route, the CircuitBreaker filter would recursively apply |
| CB state per JVM — problem? | Different replicas may have different views. Trade-off: no shared state = no thundering-herd recovery. Fine for stateless resilience. |
| How to change thresholds without restart? | `@RefreshScope` on `CircuitBreakerCustomization` + `/actuator/refresh`. Or use Spring Cloud Config. |
| How does TimeLimiter differ from `slowCallDurationThreshold`? | `slowCallDurationThreshold` = *records* the call as slow (for CB decision). TimeLimiter = *cancels* the call after N seconds. Set both. |

---

## 9. Extensions (parked)

- **Custom `recordExceptions`** — decide which errors count (e.g. include `WebClientResponseException.NotFound` if 404 is business-fatal).
- **Bulkhead per instance** — limit concurrent calls per CB to prevent thread starvation.
- **Retry filter** — wrap the CB with exponential backoff for transient failures.
- **Micrometer + Prometheus** — expose `resilience4j_circuitbreaker_state` metric,
  alert when in OPEN > 5min.
- **Adaptive thresholds** — read failure thresholds from a config server, tune per-tenant.
- **Cached fallback** — instead of 503, return the last successful response from Redis
  (stale-while-revalidate pattern).
