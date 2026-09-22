# Circuit breaker — Resilience4j

## The problem it solves

- Downstream service dies → callers block on TCP timeout (~30s) → their thread pool fills → they die too.
- **Cascading failure** — one dead service takes down its callers.

## How it works — state machine

```
   CLOSED (normal) ──N failures──▶ OPEN (fail fast, fallback)
       ▲                              │
       │                          wait period
       │                              │
       └── probe succeeds ── HALF-OPEN ◄──┘
                             (allow 1 test call)
```

## How it's wired in THIS project

- **Where:** `order-service/src/main/java/com/example/orderservice/service/ProductService.java`
- **Annotation:** `@CircuitBreaker(name = "productClient", fallbackMethod = "checkFallback")`
- **Fallback signature:** same return type + same params + `Throwable` at end
- **Config:** `order-service/application.yml` → `resilience4j.circuitbreaker.instances.productClient`

Key params:
| Param | Value | Meaning |
|---|---|---|
| `sliding-window-size` | 10 | Look at last 10 calls |
| `minimum-number-of-calls` | 5 | Wait for 5 calls before deciding |
| `failure-rate-threshold` | 50 | ≥50% failed → OPEN |
| `wait-duration-in-open-state` | 10s | Stay OPEN this long |
| `permitted-number-of-calls-in-half-open-state` | 3 | Probe count |

## How to observe it running

```bash
# Breaker state
curl -s http://localhost:8083/actuator/health | jq '.components.circuitBreakers'

# Event log
curl -s http://localhost:8083/actuator/circuitbreakerevents/productClient | jq
```

## Common failure modes

- No AOP starter → annotations do nothing (silent). Fix: `spring-boot-starter-aop` in pom.
- Fallback signature wrong → runtime error when fallback should fire.
- Breaker never trips → check `minimum-number-of-calls` — must have enough samples.

## Interview talking points

- Circuit breaker vs retry vs bulkhead — different failure modes.
- Composition order matters: retry OUTSIDE breaker OUTSIDE bulkhead.
- Netflix Hystrix (deprecated) → Resilience4j (current).
- Metrics exposure — every state change is a metric event, integrate with Prometheus.
