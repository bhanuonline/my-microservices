# Retry + Bulkhead — Resilience4j

## Retry

### The problem it solves
- Transient failures (network blip, momentary 503). Retrying often succeeds.
- Only for **idempotent** operations. Never blind-retry POST/PUT unless you have idempotency keys.

### How it's wired
- Same class as circuit breaker: `order-service/.../service/ProductService.java`
- `@Retry(name = "productClient", fallbackMethod = "checkFallback")`
- Config:
  ```
  max-attempts: 3
  wait-duration: 200ms
  enable-exponential-backoff: true
  exponential-backoff-multiplier: 2   # 200ms → 400ms → 800ms
  retry-exceptions:
    - java.io.IOException
    - java.util.concurrent.TimeoutException
    - feign.RetryableException
  ```

### Observe
```bash
curl -s http://localhost:8083/actuator/retryevents/productClient | jq
```

### Interview talking points
- Idempotency: safe to retry GET/PUT/DELETE. POST needs an idempotency key.
- Exponential backoff + **jitter** — prevents thundering-herd retry storms.
- Filter which exceptions retry (not all — business exceptions never).

---

## Bulkhead

### The problem it solves
- One slow downstream holds all your threads.
- Cap concurrent calls to it → excess requests fail fast → other endpoints keep working.

### How it's wired
- `@Bulkhead(name = "productClient", fallbackMethod = "checkFallback")`
- Config:
  ```
  max-concurrent-calls: 10
  max-wait-duration: 100ms   # excess callers wait up to 100ms then fail
  ```

### Observe
```bash
# Hammer with concurrent requests
seq 30 | xargs -I{} -P 30 curl -s -X POST http://localhost:8083/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"quantity":1}' -o /dev/null -w "%{http_code}\n"

curl -s http://localhost:8083/actuator/bulkheadevents/productClient | jq
```

### Interview talking points
- Ship compartment analogy — one flooded section doesn't sink the ship.
- Semaphore vs thread-pool bulkhead. Semaphore = same thread, capped count. Thread-pool = separate pool, harder to configure but true isolation.
- Sizing rule of thumb: `≈ target_RPS × downstream_latency_seconds`.

---

## Composition order (all 3 stacked)

```
   Client call
   ↓
   @Retry            ← outermost
       ↓
       @CircuitBreaker
           ↓
           @Bulkhead
               ↓
               actual call
```

Reasons:
- Retry outside breaker → when breaker OPEN, retries hit fallback fast (no wasted attempts).
- Breaker outside bulkhead → bulkhead rejections count toward breaker failure rate.

**Don't fight the default order — it's chosen deliberately.**
