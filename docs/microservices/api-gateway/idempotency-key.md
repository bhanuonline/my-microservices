# API Gateway — Idempotency-Key Filter

Redis-backed dedup for POST/PATCH requests. Client (or gateway) supplies an
`Idempotency-Key`; the gateway caches the response for that key for N hours.
Retries with the same key return the cached response byte-for-byte without
calling downstream — makes non-idempotent methods safe to retry.

This is the **Stripe pattern**, mostly cited whenever "idempotency + gateway +
Redis" comes up in system design.

---

## 1. The problem

```
Client                Gateway              Order Service
  │                     │                        │
  │─── POST /orders ────▶                        │
  │                     │─── forward ───────────▶│
  │                     │                        │─── charge card, order 42
  │       × network dies ×                       │
  │       (client sees no response)              │
  │                                              │
  │─── POST /orders (retry) ─▶                   │
  │                     │─── forward ───────────▶│
  │                     │                        │─── charge AGAIN, order 43
  │                     │                        │   ⚠ DUPLICATE
```

Fix: client sends the same `Idempotency-Key: 8f9a-...` on both attempts.
Gateway remembers the response after the first success. On the retry, it
returns the cached response without ever calling the backend.

---

## 2. Semantic states

Every request with an idempotency key falls into one of four:

```
┌──────────────────────────────────────────────────────────────────────┐
│  1. FIRST SEE (cache miss)                                           │
│     → set Redis lock (NX EX 30s), forward downstream,                │
│       cache 2xx response, release lock                               │
│                                                                      │
│  2. IN FLIGHT (lock exists, cache empty)                             │
│     → 409 Conflict "another request in progress"                     │
│                                                                      │
│  3. COMPLETED (cache hit)                                            │
│     → replay cached status + body + headers                          │
│       add X-Idempotent-Replay: true                                  │
│                                                                      │
│  4. MISMATCH (cache hit, different fingerprint)                      │
│     → 422 Unprocessable Entity                                       │
│     → guards against clients reusing keys carelessly                 │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 3. Architecture

```
        Client
          │  POST /api/v1/orders
          │  Idempotency-Key: 8f9a-...
          │  Body: {...}
          ▼
        ┌────────────────────────────────────────────────────────────────┐
        │  API Gateway :8080                                             │
        │                                                                │
        │  RequestRateLimiter                                            │
        │        │                                                       │
        │  CircuitBreaker (orderCB)                                      │
        │        │                                                       │
        │  RequestFingerprint  ─── sets X-Request-Fingerprint            │
        │        │                                                       │
        │  IdempotencyKey    ← THIS FILTER                               │
        │        │                                                       │
        │        │ 1. lookup idem:{route}:{key}                          │
        │        │                                                       │
        │        ├─ HIT (completed) ──▶ replay cached response ──────────┤
        │        │                     (no downstream call)              │
        │        │                                                       │
        │        ├─ HIT + fingerprint mismatch ──▶ 422 Unprocessable ────┤
        │        │                                                       │
        │        ├─ lock exists ──▶ 409 Conflict ─────────────────────── ┤
        │        │                                                       │
        │        └─ MISS ─▶ SET NX EX lock                               │
        │                │                                               │
        │                ▼                                               │
        │        chain.filter(response-decorated)                        │
        │                │                                               │
        │                ▼                                               │
        │           downstream service (POST /orders)                    │
        │                │                                               │
        │        response body captured via ServerHttpResponseDecorator  │
        │                │                                               │
        │                ▼                                               │
        │        2xx → save cached response, release lock                │
        │        non-2xx → release lock only (don't cache failures)      │
        │                                                                │
        │        ┌───────────────────────────────────────────────────┐   │
        │        │  Redis :6379                                      │   │
        │        │   idem:order-service:8f9a-... = {                 │   │
        │        │     status: "201",                                │   │
        │        │     fingerprint: "3f2a...",                       │   │
        │        │     body: "<base64>",                             │   │
        │        │     headers: "content-type=application/json|..."  │   │
        │        │   }                                               │   │
        │        │   TTL: 24h                                        │   │
        │        │                                                   │   │
        │        │   idem:order-service:8f9a-...:lock = requestId    │   │
        │        │   TTL: 30s (auto-expires if gateway crashes)      │   │
        │        └───────────────────────────────────────────────────┘   │
        └────────────────────────────────────────────────────────────────┘
```

---

## 4. Design decisions

### Where does idempotency live?

Two schools:

| Approach | Pros | Cons |
|---|---|---|
| At the gateway (what we built) | One implementation for all services; downstream doesn't know | Gateway becomes stateful; if bypassed, no protection |
| Inside each backend service | Service owns its own correctness; direct-DB retry semantics | Every service reimplements; duplicate infra |

Production answer is often "**both**" — gateway for defense in depth, service for correctness. Here we're doing gateway-only for learning.

### Client-supplied vs derived key

- **Client-supplied** — the canonical Stripe pattern. Client generates a UUID per logical operation, keeps it stable across retries. Configured via `Idempotency-Key` header.
- **Derived** — if header is missing, fall back to `X-Request-Fingerprint` (from the previous filter). This is a *safety net*, not a substitute — different clients making semantically-identical requests would collide.

Toggle: `gateway.idempotency.derive-from-fingerprint`.

### Which methods

Only `POST` and `PATCH`. GET/PUT/DELETE are idempotent by HTTP semantics — wrapping them wastes Redis calls.

### Cache success only

We only cache 2xx responses. Failures are released:

- **Cache failures** would make a genuine recovery impossible — client retry gets the same 500 back forever (until TTL).
- **Not caching** means retry can hit downstream again → risk of duplicate side effects but also the ability to recover from transient failures.

Stripe caches success only. We follow.

### The compare-and-delete Lua

Lock release uses a Lua script:

```lua
if redis.call('get', KEYS[1]) == ARGV[1] then
  return redis.call('del', KEYS[1])
else return 0 end
```

**Why**: without this, we could release a lock that expired and was reacquired by someone else. The compare (`get == requestId`) ensures we only delete our own lock. Interviewers love this — talk about it explicitly.

### `SET NX EX` vs `SETNX + EXPIRE`

We use `redis.opsForValue().setIfAbsent(key, value, ttl)` — this maps to Redis `SET key value NX EX ttl`, a single atomic command.

The alternative (`SETNX` then separately `EXPIRE`) is broken: if the process dies between commands, the lock lives forever.

### Response header allowlist (strip sensitive)

Cached response headers are replayed **byte-for-byte** to any client hitting the same key. That's a leak vector for auth cookies:

```
User A → POST /orders with Idempotency-Key: xyz
  ← 201 Set-Cookie: session=A_token
       Authorization: Bearer A_bearer

Cached → status=201, headers="Set-Cookie=session=A_token|Authorization=..."

User B → POST /orders with Idempotency-Key: xyz (same key, different auth)
  ← 201 (replayed cache)
       Set-Cookie: session=A_token       ← LEAK — B now has A's session
       Authorization: Bearer A_bearer
```

Fix: `stripHeaders` config removes sensitive headers **before** caching. Default: `Set-Cookie`, `Authorization`, `Date`.

```yaml
gateway.idempotency.strip-headers:
  - Set-Cookie
  - Authorization
  - Date
```

Case-insensitive match. Add any header your APIs might leak (e.g. `X-Refresh-Token`, `X-Session-Id`).

### Streaming / SSE detection (skip cache)

`DataBufferUtils.join()` reads the ENTIRE response body into memory before caching. For a normal JSON response, fine. For a `text/event-stream` or a chunked download, catastrophic — unbounded buffering, OOM.

Fix: inspect the response `Content-Type` before buffering. If it's in `streamingContentTypes`, skip cache write and pass the stream through unmodified:

```yaml
gateway.idempotency.skip-streaming-content: true
gateway.idempotency.streaming-content-types:
  - text/event-stream          # Server-Sent Events
  - application/octet-stream   # opaque binary / downloads
  - application/x-ndjson       # newline-delimited JSON streams
```

Impact: streaming responses always call downstream (no idempotency benefit), but don't blow up the gateway. Correct trade-off — streams are usually GETs anyway (which are already outside the methods list).

### Fail-open on Redis outage

Default: Redis down → filter returns 503. Correct but availability-costly during Redis incidents.

Alternate: `failOpenOnStoreError: true` — Redis errors bypass the filter entirely. Request proceeds as if idempotency was disabled. Client may double-post if it retries during the outage, but service stays available.

```yaml
gateway.idempotency.fail-open-on-store-error: false   # strict correctness
# or
gateway.idempotency.fail-open-on-store-error: true    # availability > correctness
```

When bypassed, adds `X-Idempotency-Bypassed: lookup-error` (or `lock-error`) response header for observability. Grafana can alert on this header's rate.

Interview take: *"pick correctness by default; toggle to fail-open only if your Redis SLA is worse than your idempotency-vs-double-write trade allows."*

### Runtime refresh via `@RefreshScope`

`IdempotencyProperties` is annotated `@RefreshScope`, so config changes take effect without a restart:

```
1. Edit application.yml (or Config Server pushes update)
2. POST /actuator/refresh
   → Environment re-reads property sources
   → IdempotencyProperties bean destroyed + recreated with new values
   → Next request sees fresh config
```

Refreshable at runtime:
- `keyTtl`, `lockTtl` (applied to NEW writes; existing Redis entries keep original TTL)
- `stripHeaders`, `skipStreamingContent`, `streamingContentTypes`
- `failOpenOnStoreError`, `requireHeader`, `verifyFingerprint`
- `headerName`, `fingerprintHeader`
- `enabled` (runtime kill switch — see below)

**Runtime kill switch**: the filter body checks `props.isEnabled()` on every request. Flip `gateway.idempotency.enabled: false` + refresh → filter no-ops. This works even though `@ConditionalOnProperty` is boot-only, because the filter bean already exists; it just becomes pass-through.

**What DOESN'T refresh**: `@ConditionalOnProperty` gates *bean creation* at boot. If you start with `enabled=false`, the filter bean doesn't exist; refresh won't resurrect it. Toggle only works one way at runtime (already-created bean → no-op).

**Existing Redis entries**: keep the TTL they were written with. Changing `keyTtl` from 24h → 1h affects only *new* entries. Redis TTL is set on write, not queried on read.

**Multi-replica** (not built): `/actuator/refresh` hits ONE pod. For all-pods refresh, either:
- Spring Cloud Bus (`/actuator/busrefresh` broadcasts via Redis/Kafka)
- Roll-your-own: reuse the route-refresh pub/sub pattern for `IdempotencyRefreshMessage`

Interview take: *"`@RefreshScope` is a Spring proxy — every getter call resolves to the currently-active bean. The filter doesn't know config changed; it just sees new values on next access."*

---

## 5. Redis schema

```
idem:{route-id}:{idempotency-key}         → hash: status, fingerprint, body(b64), headers
idem:{route-id}:{idempotency-key}:lock    → value: requestId, TTL: lock-ttl
```

**Why scope by route-id**: two services could theoretically accept the same idempotency key format. Route-scoped keys prevent collision.

**Why a hash (not JSON)**: allows atomic HGET/HSET per field. Also cheaper to update just one field (though we don't do partial updates today).

**Why base64-encode the body**: response bodies can contain any bytes (binary, non-UTF-8). Redis strings need clean encoding.

---

## 6. Config schema

```yaml
gateway:
  idempotency:
    enabled: true
    header-name: Idempotency-Key
    methods: [POST, PATCH]
    key-ttl: 24h                    # cached response lifetime
    lock-ttl: 30s                   # in-flight lock (must exceed slowest downstream call)
    require-header: false           # true = reject POST/PATCH without header (415)
    strip-headers:                  # never cache these (auth leak prevention)
      - Set-Cookie
      - Authorization
      - Date
    skip-streaming-content: true    # don't buffer SSE / binary streams
    streaming-content-types:
      - text/event-stream
      - application/octet-stream
      - application/x-ndjson
    fail-open-on-store-error: false # true = Redis down → bypass filter (availability > correctness)
    derive-from-fingerprint: true   # fallback if header missing
    verify-fingerprint: true        # 422 on same key + different body
    fingerprint-header: X-Request-Fingerprint
```

`@ConditionalOnProperty(enabled=true)` gates both `IdempotencyStore` and `IdempotencyKeyGatewayFilterFactory` — disabling drops both beans entirely.

---

## 7. Files added / changed

```
api-gateway/
└── src/main/
    ├── java/com/example/apigateway/
    │   ├── config/
    │   │   └── IdempotencyProperties.java                     (NEW)
    │   ├── idempotency/
    │   │   └── IdempotencyStore.java                          (NEW — Redis wrapper + Lua)
    │   └── filter/
    │       └── IdempotencyKeyGatewayFilterFactory.java        (NEW — the filter)
    └── resources/
        ├── application.yml                                    (idempotency block + filter on order route)
        └── application-noidem.yml                             (NEW — disable profile)
```

No new dependencies — `spring-boot-starter-data-redis-reactive` is already in from the rate limiter.

---

## 8. Filter ordering (interview-critical)

```
Route: order-service
  filters:
    - CircuitBreaker                    ← guards downstream
    - AddTenantHeader                   ← injects tenant from JWT
    - RequestFingerprint (order -1)     ← MUST run first — sets X-Request-Fingerprint
    - IdempotencyKey (order 0)          ← reads fingerprint for mismatch check
```

If `IdempotencyKey` ran before `RequestFingerprint`:
- No fingerprint → can't detect body mismatches → clients could reuse keys with different bodies undetected

---

## 9. Verification

Prereq: Redis running (`docker run redis:7-alpine` — already up from rate limiter build).

```bash
# 1. Boot everything
mvn -pl infra/api-gateway clean spring-boot:run
# (with user-service, order-service, eureka, auth-server also running)

# 2. Get a JWT
TOKEN=$(curl -s -u admin:admin123 \
  -d "grant_type=client_credentials&scope=read" \
  http://localhost:9010/oauth2/token | jq -r .access_token)
```

### 9.1 First request — creates order + caches

```bash
curl -i -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: order-12345" \
  -d '{"item":"widget","qty":1}' \
  http://localhost:8080/api/v1/orders
# → 201 Created, order id 42
```

Look inside Redis:
```bash
docker exec -it gateway-redis redis-cli KEYS "idem:*"
# idem:order-service:order-12345

docker exec -it gateway-redis redis-cli HGETALL "idem:order-service:order-12345"
# status: 201
# fingerprint: 3f2a9c8b7d...
# body: eyJvcmRlcklkIjo0MiwuLi59
# headers: content-type=application/json|...
```

### 9.2 Retry with SAME key + SAME body → cached replay

```bash
curl -i -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: order-12345" \
  -d '{"item":"widget","qty":1}' \
  http://localhost:8080/api/v1/orders
# → 201, order id 42 (SAME)
# → Response header: X-Idempotent-Replay: true
# → order-service logs should NOT show a second call
```

### 9.3 SAME key + DIFFERENT body → 422

```bash
curl -i -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: order-12345" \
  -d '{"item":"gadget","qty":9}' \
  http://localhost:8080/api/v1/orders
# → 422 Unprocessable Entity
# → {"error":"idempotency_key_mismatch","message":"..."}
```

### 9.4 New key + new body → new order

```bash
curl -i -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: order-67890" \
  -d '{"item":"gadget","qty":2}' \
  http://localhost:8080/api/v1/orders
# → 201, order id 43
```

### 9.5 Race test — two identical POSTs simultaneously

```bash
(curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: order-race" \
  -d '{"item":"race"}' \
  http://localhost:8080/api/v1/orders &) ; \
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: order-race" \
  -d '{"item":"race"}' \
  http://localhost:8080/api/v1/orders
# → One returns 201, the other returns 409 Conflict "in-flight"
```

### 9.6 No header + POST → passes through (require-header: false)

```bash
curl -i -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"item":"anonymous"}' \
  http://localhost:8080/api/v1/orders
# → 201 (with derive-from-fingerprint: true, gateway uses X-Request-Fingerprint as key)
```

Set `require-header: true` to reject instead:
```bash
# After changing config + restart:
curl -i -X POST ... (no Idempotency-Key)
# → 415 Unsupported Media Type
```

### 9.7 GET → filter passes through (methods filter)

```bash
curl -i -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/orders/42
# → normal 200, no idempotency logic runs
```

### 9.8 Disable via profile

```bash
mvn -pl infra/api-gateway spring-boot:run -Dspring-boot.run.profiles=noidem

# Now retry with same key + different body still succeeds (creates duplicate)
```

---

## 10. Failure modes & mitigations

| Scenario | Behavior | Mitigation |
|---|---|---|
| Gateway crashes mid-request | Lock has 30s TTL — auto-expires; next attempt can proceed | `lock-ttl` must exceed slowest downstream call |
| Wrong owner releases lock (expired + reacquired) | Compare-and-delete Lua prevents wrong release | Present in `IdempotencyStore` |
| Client reuses key with different body | 422 Unprocessable Entity | `verify-fingerprint: true` |
| Cache poisoning with 5xx | Only 2xx cached; failures release lock | `writeWith` checks `is2xxSuccessful()` |
| SSE / chunked response | Skipped via Content-Type check — response passes through unbuffered | `skip-streaming-content: true` + `streaming-content-types` list |
| Body > memory limit | `DataBufferLimitException` | `spring.codec.max-in-memory-size: 1MB` |
| Redis down (strict) | 503 with `idempotency_store_unavailable` | Default. Ensures no double-write. |
| Redis down (fail-open) | Filter bypassed, request proceeds, `X-Idempotency-Bypassed` header set | `fail-open-on-store-error: true` — availability over correctness |
| Auth cookies replayed to wrong client | `Set-Cookie` / `Authorization` stripped before cache | `strip-headers` config — default: Set-Cookie, Authorization, Date |
| Two gateway replicas race | Redis lock serializes — first wins, second 409 | Present in design |
| Header case | Header lookup uses standard HTTP case-insensitive rules | Handled by `HttpHeaders.getFirst()` |

---

## 11. Interview cheat-sheet

| Question | Answer |
|---|---|
| Why idempotency keys? | Retries on non-idempotent methods (POST/PATCH) cause duplicate side effects (double-charge, duplicate orders) |
| Client-supplied or gateway-derived? | Canonical: client supplies (Stripe pattern). Derived from fingerprint is defense-in-depth. |
| Why cache the RESPONSE, not just detect duplicate? | Client on retry expects an *identical* response — same status, body, headers. Otherwise retry breaks client logic. |
| Why both lock (SETNX) and response cache? | Lock handles the race (concurrent duplicates). Cache handles the sequential retry (after first completed). |
| `SET NX EX` vs `SETNX + EXPIRE`? | Atomic — if process dies between SETNX and EXPIRE, lock leaks. `SET NX EX` is one command, safe. |
| Why the compare-and-delete Lua? | Prevents releasing someone else's lock (if ours expired and someone else acquired). Only delete if value matches our requestId. |
| Cache failures too? | No (Stripe convention). Client retry hits downstream again; genuine recovery possible. Trade-off: possible retry storms. |
| What TTL for cache? | Stripe: 24h. Balance client retry window vs Redis memory footprint. |
| Streaming/SSE responses? | Skip — buffering unbounded response is unsafe. `skip-streaming-content: true` + Content-Type match. |
| Auth cookies in cached response — leak risk? | Yes. `strip-headers` config removes Set-Cookie / Authorization / Date before caching. |
| Redis down — 503 or bypass? | `fail-open-on-store-error` toggle. `false` = 503 (strict). `true` = bypass filter, add `X-Idempotency-Bypassed` header for observability. |
| How do you change TTLs without a restart? | `IdempotencyProperties` is `@RefreshScope`. Edit yaml + `POST /actuator/refresh`. Proxy resolves fresh values on next getter call. |
| Do existing Redis entries get the new TTL? | No — Redis TTL is set at write time. New entries get new TTL; existing keep original. |
| Runtime kill switch? | Yes — filter body checks `props.isEnabled()` per request. Flip flag + refresh = pass-through. |
| Multi-pod refresh? | Not built. Options: Spring Cloud Bus (`/actuator/busrefresh`) or reuse the route-refresh pub/sub pattern with a new channel. |
| How does this interact with Circuit Breaker? | Runs BEFORE the CB. Cache hit bypasses CB entirely (cheaper). Cache miss → normal CB flow. |
| Two gateway replicas race — what happens? | Redis lock serializes: first replica wins, second gets 409 (or waits for cache via polling). |
| Body mismatch — return 422 or 200 with original? | 422. Signals client bug. Returning 200 hides misuse. |
| What if idempotency-key spans across gateways/regions? | Redis needs to be shared cross-region OR gateway is single-region + region-affinity routing. Multi-region idempotency = distributed problem, way beyond this filter. |
| Alternative: use the fingerprint AS the key? | Works when clients don't cooperate. Weakness: identical requests from different clients collide. |

---

## 12. Extensions (parked)

- **Wait-for-lock** — instead of 409, poll the cache for N seconds hoping the in-flight request completes. Better client UX, more Redis pressure.
- **Metric hooks** — Micrometer counter for `idempotency.hit`, `idempotency.miss`, `idempotency.conflict`, `idempotency.mismatch`, `idempotency.bypassed`. Great for Grafana / observability item L.
- **Fingerprint scope** — currently `method | path | user | body`. May want to include Authorization to prevent cross-user cache hits (currently prevented by user in fingerprint).
- **Compression** — response bodies over N KB should be gzip'd before base64 encoding.
- **Spring Cloud Bus** — `POST /actuator/busrefresh` broadcasts refresh to all pods via Redis/Kafka. One dep + one endpoint.
- **Spring Cloud Config server** — externalize yaml to git. Auditable, versioned, multi-service.
- **DIY multi-pod refresh** — new pub/sub channel `idempotency.refresh.reload` using the same pattern as `route-refresh`. Zero new infra.
- **Refresh audit log** — listen for `RefreshScopeRefreshedEvent`, log changed keys + timestamp. Compliance.

### Verification of the newly-shipped features

Header stripping — make sure Set-Cookie / Authorization never survive the cache:
```bash
TOKEN=$(curl -s -u admin:admin123 \
  -d "grant_type=client_credentials&scope=read" \
  http://localhost:9010/oauth2/token | jq -r .access_token)

# Trigger caching (assumes order-service echoes a Set-Cookie header)
curl -i -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: leak-test-1" \
  -d '{"item":"widget"}' \
  http://localhost:8080/api/v1/orders

# Inspect the cache — headers field must NOT contain Set-Cookie or Authorization
docker exec -it gateway-redis redis-cli HGET "idem:order-service:leak-test-1" headers
# Expected: only benign headers, no Set-Cookie / Authorization / Date

# Retry — response should NOT include the original Set-Cookie either
curl -i -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: leak-test-1" \
  -d '{"item":"widget"}' \
  -X POST http://localhost:8080/api/v1/orders
```

Fail-open — kill Redis, then check `X-Idempotency-Bypassed`:
```bash
# Enable fail-open first
mvn -pl infra/api-gateway spring-boot:run \
  -Dspring-boot.run.arguments="--gateway.idempotency.fail-open-on-store-error=true"

docker stop gateway-redis

curl -i -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: outage-1" \
  -d '{"item":"widget"}' \
  http://localhost:8080/api/v1/orders
# Response has: X-Idempotency-Bypassed: lookup-error
# Body: normal order-service response (idempotency skipped)

docker start gateway-redis
```

Runtime refresh — flip config without restart:
```bash
# 1. Confirm refresh endpoint present
curl -s http://localhost:8080/actuator | jq '._links | keys[]'
# → look for "refresh"

# 2. See current TTL
curl -s http://localhost:8080/actuator/env/gateway.idempotency.key-ttl | jq '.property.value'
# → "PT24H"

# 3. Edit application.yml: change key-ttl: 24h → key-ttl: 1h
# Save the file.

# 4. Trigger refresh
curl -X POST http://localhost:8080/actuator/refresh
# → ["gateway.idempotency.key-ttl"]     ← list of changed keys

# 5. Confirm new value active
curl -s http://localhost:8080/actuator/env/gateway.idempotency.key-ttl | jq '.property.value'
# → "PT1H"

# 6. New cache entries use 1h TTL; existing 24h entries keep their TTL
docker exec -it gateway-redis redis-cli TTL "idem:order-service:<some-existing-key>"
# → still ~86400s (old entry, original 24h)

# Add a new entry, check its TTL:
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: post-refresh-ttl-1" \
  -d '{"item":"x"}' \
  http://localhost:8080/api/v1/orders
docker exec -it gateway-redis redis-cli TTL "idem:order-service:post-refresh-ttl-1"
# → ~3600s (new entry, refreshed 1h TTL)

# 7. Runtime kill switch — disable idempotency without restart
# Edit application.yml: gateway.idempotency.enabled: false
curl -X POST http://localhost:8080/actuator/refresh
# → ["gateway.idempotency.enabled"]

# Confirm: two POSTs with same key both create orders (idempotency bypassed)
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: killswitch-1" \
  -d '{"item":"x"}' \
  http://localhost:8080/api/v1/orders
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: killswitch-1" \
  -d '{"item":"x"}' \
  http://localhost:8080/api/v1/orders
# Both hit downstream — no dedup

# 8. Re-enable
# Edit application.yml: gateway.idempotency.enabled: true
curl -X POST http://localhost:8080/actuator/refresh
# Idempotency resumes for future keys
```

Streaming — SSE response should NOT be buffered:
```bash
# Assumes an SSE endpoint at /api/v1/orders/stream returning text/event-stream
curl -N -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: stream-test-1" \
  -X POST http://localhost:8080/api/v1/orders/stream

# Should stream events live (not accumulate then flush). Check Redis:
docker exec -it gateway-redis redis-cli KEYS "idem:*stream-test-1*"
# Expected: only the lock key, no cache entry (streaming skipped after body inspection)
```
