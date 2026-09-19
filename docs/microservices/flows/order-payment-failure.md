# Flow — Order → Payment (failure paths)

## Scenario A — Payment declined (amount >= $50)

```bash
# quantity=6 → amount=$59.94 → payment-service replies failure
curl -X POST http://localhost:8083/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"quantity":6}'
```

Expected:
- Saga: STARTED → FAILED (no compensation — payment was the first step)
- Order: CREATED → CANCELLED with reason "amount_over_limit"

## Scenario B — product-service down (circuit breaker)

Stop product-service in your IDE.

```bash
# Hammer 10 times — first few slow, then instant fallback via CB
for i in $(seq 10); do
  curl -s -X POST http://localhost:8083/api/v1/orders \
    -H "Content-Type: application/json" \
    -d '{"productId":1,"quantity":2}' -w " %{http_code}\n"
done
```

Expected:
- First ~5 requests: slow (Retry does 3 attempts × exponential backoff before returning UNAVAILABLE fallback)
- After breaker trips: instant 503 with `PRODUCT_UNAVAILABLE` error code
- `curl http://localhost:8083/actuator/health | jq '.components.circuitBreakers'` → state `OPEN`

## Trace observations

<!-- Fill in -->

### Retry events
```bash
curl -s http://localhost:8083/actuator/retryevents/productClient | jq
```
Expect: RETRY, RETRY, ERROR events per failed call.

### Circuit breaker events
```bash
curl -s http://localhost:8083/actuator/circuitbreakerevents/productClient | jq
```
Expect: ERROR events, then STATE_TRANSITION from CLOSED → OPEN.

## Scenario C — Restart product-service (breaker recovery)

Restart it. Wait 10s (the `wait-duration-in-open-state`), then:

```bash
curl -X POST http://localhost:8083/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"quantity":2}'
```

Expected:
- First call: breaker in HALF_OPEN, probes downstream, succeeds → back to CLOSED.
- Subsequent calls normal.
- Actuator health: `state: CLOSED`.

## What this teaches

- Retry is INNER to breaker in terms of behavior when breaker is OPEN: the whole method (breaker+retry) returns fallback instantly, not retry-3-times-fail.
- Fallback path bypasses network entirely — critical property.
