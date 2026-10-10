# API Gateway — Cached Fallback (Stale-While-CB-Open)

When the circuit breaker opens, instead of returning a static 503, replay the
**last successful response** cached in Redis. Read traffic survives downstream
outages; users see slightly stale data instead of an error page.

Cloudflare calls this pattern **"Always Online"**. RFC 5861 calls it
**stale-while-revalidate**.

Companion to [circuit-breaker.md](circuit-breaker.md) — this is the "cached
fallback" extension previously parked.

---

## 1. Before / after

```
BEFORE (static fallback):

  Client → GET /api/v1/products/42
              │
              ▼
         CB OPEN
              │
              ▼
       fallback returns:
       503 {"error":"service_unavailable"}     ← ugly, unhelpful
       Product page broken.

AFTER (cached fallback):

  Client → GET /api/v1/products/42
              │
              ├── CB CLOSED (normal) ──▶ downstream ──▶ 200 {product data}
              │                             │
              │                    ┌────────┴─────────────┐
              │                    │  ResponseCache        │
              │                    │  GlobalFilter         │
              │                    │  captures 2xx GET     │
              │                    │  stores in Redis 1h   │
              │                    └───────────────────────┘
              │
              └── CB OPEN ──▶ FallbackController
                                 │
                                 ▼
                          Redis lookup by URL hash
                                 │
                          hit ──▶ 200 {product data}     ← stale but usable
                                    + Warning: 110       ← RFC 7234
                                    + X-Cache: STALE
                                    + X-Cache-Age: 42
                          miss ─▶ 503 (only when no cache)
```

---

## 2. Trade-offs (interview meat)

| Pro | Con |
|---|---|
| Read traffic survives outages | Cache grows large — needs TTL bound + LRU eviction |
| UX degrades gracefully | Stale data may confuse users → `Warning: 110` |
| Reduces load on recovering downstream | Doesn't help writes (POST/PATCH/DELETE) |
| Reuses existing Redis + ServerHttpResponseDecorator | Only cacheable responses (2xx GET/HEAD) |
| Fully backwards-compatible (falls back to static 503 on miss) | Cache invalidation is hard (deliberately not addressed here) |

**Golden rule**: only cache **idempotent + safe** responses (`GET`, `HEAD`) with
2xx status. Never cache POST — would replay creation.

---

## 3. Architecture

```
                    ┌──────────────────────────────────────────────────────────────────┐
                    │           API Gateway :8080                                      │
                    │                                                                  │
Client ──GET──▶     │  BodyLoggingGlobalFilter    order = -20                          │
                    │                                                                  │
                    │  ResponseCacheGlobalFilter  order = -10  ← NEW                   │
                    │      wraps response.writeWith() via decorator                    │
                    │      on 2xx GET/HEAD + text content: capture + save to Redis     │
                    │                                                                  │
                    │  route filters:                                                  │
                    │    RateLimiter → CircuitBreaker → Retry → downstream             │
                    │                     │                                            │
                    │                     │ CLOSED (normal path)                       │
                    │                     ▼                                            │
                    │              downstream returns 200                              │
                    │              ResponseCacheGlobalFilter snapshot to Redis         │
                    │              client sees fresh response                          │
                    │                                                                  │
                    │                     │ OPEN (circuit tripped)                     │
                    │                     ▼                                            │
                    │           fallbackUri: forward:/fallback/{service}               │
                    │              │                                                   │
                    │              ▼                                                   │
                    │     FallbackController                                           │
                    │              │                                                   │
                    │       GET/HEAD?     ── no ──▶ static 503 (writes always fail)    │
                    │              │                                                   │
                    │              │ yes                                               │
                    │              ▼                                                   │
                    │     ResponseCacheStore.lookup(routeId, method, path, query)      │
                    │              │                                                   │
                    │       hit ───┴─── miss                                           │
                    │       │           │                                              │
                    │       ▼           ▼                                              │
                    │  replayStale()  static 503                                       │
                    │  + Warning:110                                                   │
                    │  + X-Cache:STALE                                                 │
                    │  + X-Cache-Age                                                   │
                    └──────────────────────────────────────────────────────────────────┘
                                          │
                                          ▼
                    ┌──────────────────────────────────────────────────────────────────┐
                    │        Redis :6379                                               │
                    │                                                                  │
                    │  respcache:<sha256(routeId|method|path|query)> = {               │
                    │      status: "200",                                              │
                    │      body: "<base64>",                                           │
                    │      headers: "Content-Type=application/json|...",               │
                    │      cachedAt: "2026-09-29T10:00:00Z",                           │
                    │      originalUrl: "/api/v1/products/42"                          │
                    │  }                                                               │
                    │  TTL: 1h (or per-route override)                                 │
                    └──────────────────────────────────────────────────────────────────┘
```

---

## 4. Design decisions

### 4a. GlobalFilter vs per-route

Chose **`GlobalFilter`** for cache writes. Reasons:
- Auditing-style behavior — applies everywhere by default
- Excluded-paths handle noisy exceptions (`/actuator`, `/fallback`, `/admin`)
- Symmetric with `BodyLoggingGlobalFilter`

### 4b. Cache write criteria (all must hold)

```
✓ HTTP method is GET or HEAD
✓ Response status is 2xx
✓ Response has a body (non-zero)
✓ Cache-Control header does NOT contain no-store or private
✓ Content-Type is text (application/json, application/xml, text/*)
✓ Response size < max-cached-bytes (default 512 KB)
```

Any miss → skip write silently. Response goes to client unchanged.

### 4c. Cache read criteria (fallback controller)

```
✓ Request hit /fallback/{service} (CB triggered forward)
✓ Original method is GET or HEAD
✓ Redis entry exists for the URL
```

Miss → static 503 (identical to previous behavior). Fully backwards compatible.

### 4d. Key structure

```
respcache:<sha256(routeId + "|" + method + "|" + path + "|" + query)>
```

Why include each field:
- `routeId` — two routes may proxy to different backends via same path
- `method` — GET vs HEAD have different responses (HEAD lacks body)
- `path` — obvious
- `query` — `/products?limit=10` ≠ `/products?limit=50`

Why hash — long URLs, fixed-size keys, hides sensitive query params in Redis
key-space logging.

### 4e. Headers to strip

```yaml
strip-headers:
  - Set-Cookie      # never replay auth cookies to other users
  - Authorization   # defense in depth
  - Date            # clients might calc age from it; would be wrong on replay
```

### 4f. Signaling stale

RFC 7234 defines:
```
Warning: 110 - "Response is stale"
```

Plus custom debug headers:
```
X-Cache: STALE
X-Cache-Age: 42                       (seconds since cachedAt)
X-Cache-Original-Url: /api/v1/products/42
```

Grafana can alert on high `X-Cache: STALE` frequency.

### 4g. Invalidation strategy

**Not addressed in this build.** TTL is the sole freshness mechanism.

Real solutions (parked as extensions):
- Explicit `DELETE /admin/response-cache` endpoint
- Pub/sub `cache.invalidate` events when downstream mutates data (reuses your route-refresh pattern)

Cache invalidation being hard is a **feature** of the discussion — mention Phil
Karlton's famous quote: *"There are only two hard things in Computer Science:
cache invalidation and naming things."*

### 4h. Filter ordering

```
BodyLoggingGlobalFilter       order = -20      (audit, no side effects)
ResponseCacheGlobalFilter     order = -10      (writes cache on 2xx GET)
CircuitBreaker (route filter) built-in         (may trigger fallback)
downstream call
```

`ResponseCacheGlobalFilter` must run AFTER downstream returns but BEFORE the
response is written to the client — the `ServerHttpResponseDecorator` handles
this automatically via `writeWith()` interception.

---

## 5. Config

```yaml
gateway:
  response-cache:
    enabled: true
    default-ttl: 1h
    max-cached-bytes: 524288          # 512 KB
    cacheable-methods: [GET, HEAD]
    cacheable-content-types:
      - application/json
      - application/xml
      - text/plain
      - text/html
    excluded-paths:
      - /actuator/**
      - /fallback/**
      - /admin/**
    strip-headers:
      - Set-Cookie
      - Authorization
      - Date
    stale-warning: true
    routes:                            # per-route TTL overrides
      user-service:  { ttl: 30m }
      product-service: { ttl: 2h }
      order-service: { ttl: 5m }
```

Master switch via `@ConditionalOnProperty` on:
- `ResponseCacheStore` (Redis wrapper bean)
- `ResponseCacheGlobalFilter` (the filter)

`FallbackController` uses `ObjectProvider<...>` so it still works when cache is
disabled (behaves like the old static-only fallback).

---

## 6. Files added / changed

```
api-gateway/
└── src/main/
    ├── java/com/example/apigateway/
    │   ├── responsecache/                                  (NEW package)
    │   │   ├── ResponseCacheProperties.java                (NEW)
    │   │   ├── ResponseCacheStore.java                     (NEW — Redis wrapper)
    │   │   └── ResponseCacheGlobalFilter.java              (NEW — captures 2xx GET)
    │   └── controller/
    │       └── FallbackController.java                     (extended — tries cache first)
    └── resources/
        └── application.yml                                 (gateway.response-cache block)

docs/microservices/api-gateway/
└── cached-fallback.md                                      (this file)
```

No new dependencies — Redis reactive already in from rate limiter.

---

## 7. Verification

```bash
mvn -pl infra/api-gateway clean spring-boot:run

# Prereqs: Redis + Eureka + auth-server + product-service all running

TOKEN=$(curl -s -u admin:admin123 \
  -d "grant_type=client_credentials&scope=read" \
  http://localhost:9010/oauth2/token | jq -r .access_token)
```

### 7.1 Normal GET — response cached in background

```bash
curl -i -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/products/all
# → 200 OK, product list

# Check Redis
docker exec -it gateway-redis redis-cli KEYS "respcache:*"
# → respcache:<sha256_hex>

docker exec -it gateway-redis redis-cli HGETALL "respcache:<hash>"
# status: 200
# body: <base64>
# headers: Content-Type=application/json|...
# cachedAt: 2026-09-29T...
# originalUrl: http://localhost:8080/api/v1/products/all

docker exec -it gateway-redis redis-cli TTL "respcache:<hash>"
# → 7200 (2h — product-service override)
```

### 7.2 Simulate outage — stop product-service

```bash
# Ctrl-C the product-service process
```

### 7.3 Trigger CB → fallback serves cache

```bash
# Hit repeatedly to open the CB (first few will be genuine 5xx, then fallback kicks in)
for i in $(seq 1 10); do
  curl -s -o /dev/null -w "%{http_code} " \
    -H "Authorization: Bearer $TOKEN" \
    http://localhost:8080/api/v1/products/all
done
echo
# Expected: 503 503 200 200 200 ... (once CB opens, cached 200 served)
```

### 7.4 Verify STALE headers

```bash
curl -i -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/products/all
# HTTP/1.1 200 OK
# Content-Type: application/json
# Warning: 110 - "Response is stale"
# X-Cache: STALE
# X-Cache-Age: 42
# X-Cache-Original-Url: http://localhost:8080/api/v1/products/all
#
# [cached product list]
```

### 7.5 URL never cached → genuine 503

```bash
curl -i -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/products/never-fetched-before
# → 503 {"error":"service_unavailable","service":"products",...}
```

### 7.6 POST during outage → always 503 (writes never cached)

```bash
curl -i -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"item":"x"}' \
  http://localhost:8080/api/v1/orders
# → 503 (POST never uses cache — safety)
```

### 7.7 Restart downstream, wait for CB to close

```bash
mvn -pl services/product-service spring-boot:run
sleep 20

curl -i -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/products/all
# → 200 OK, fresh from downstream
# NO X-Cache header (not a fallback response)
# Cache is UPDATED with the fresh response
```

### 7.8 Invalidate via Redis directly

```bash
docker exec -it gateway-redis redis-cli DEL "respcache:<hash>"
# Next fallback for that URL → 503 (cache miss)
```

### 7.9 Disable the whole feature

```bash
mvn -pl infra/api-gateway spring-boot:run \
  -Dspring-boot.run.arguments="--gateway.response-cache.enabled=false"

# FallbackController still works — pure static 503 behavior (backwards compat)
```

---

## 8. Failure modes & mitigations

| Scenario | Behavior | Mitigation |
|---|---|---|
| Downstream returns 500 the first time (no prior success) | Cache empty → static 503 | Same as pre-cached behavior. No regression. |
| Cached response contains expired auth cookies | Would serve to next user | `strip-headers: [Set-Cookie]` — present |
| Cache poisoning via untrusted downstream | Attacker responses cached; served to users | Trust boundary — same as regular downstream trust |
| Very large response (10 MB) | `max-cached-bytes` skips it, logs debug | Log skip; extension: gzip before storing |
| TTL too long | Users see very stale data | Per-route TTL config; extension: adaptive TTL |
| Redis down at request time | Cache lookup errors → static 503 (`.onErrorResume`) | Present in FallbackController |
| Streaming response (SSE) | Content-Type filter skips | `text/event-stream` not in cacheable-content-types |
| POST accidentally cached | Would replay creation | Explicit method allowlist blocks non-GET/HEAD |
| Cache growth unbounded | Redis fills up | TTL bounds; consider `maxmemory-policy allkeys-lru` |
| Cached response with `Cache-Control: no-store` | Not cached (RFC compliance) | Filter checks Cache-Control header |
| Two requests race for same URL cache write | Redis HSET is atomic; last-write-wins | Acceptable — both responses are current |

---

## 9. Interview cheat-sheet

| Question | Answer |
|---|---|
| Why cache the fallback response? | Read traffic survives downstream outages. UX degrades to "stale data" instead of "error page." |
| What's stale-while-revalidate? | HTTP-caching pattern (RFC 5861): serve stale content while updating in background. We serve stale during CB open. |
| Why only cache GET/HEAD? | Idempotency + safety. POST caching = replay creation = duplicate orders. |
| TTL selection? | Balances staleness vs coverage. Product data: 5-15 min. Config: hours. Live prices: seconds. Per-route override. |
| Why hash the URL for the cache key? | Fixed-size Redis key; hides sensitive query params from Redis-key logs; allows arbitrarily long URLs. |
| How signal stale to client? | RFC 7234 `Warning: 110`. Plus `X-Cache: STALE`, `X-Cache-Age` for monitoring/debugging. |
| Which headers preserve on replay? | Content-Type (parsing), custom X-* headers. Strip Set-Cookie (auth leak), Authorization, Date. |
| Invalidation strategy? | TTL only in this build. Extensions: explicit invalidate endpoint + pub/sub on mutations. Cache invalidation is hard. |
| POST during outage? | Never cached. Fallback returns 503. Writes require downstream. |
| Interaction with CircuitBreaker? | ResponseCache captures 2xx while CB CLOSED. FallbackController reads cache when CB triggers fallback URI. Symbiotic. |
| Interaction with IdempotencyKey? | Different scopes. Idempotency dedups client retries; response cache serves during downstream outages. Both use ServerHttpResponseDecorator (same pattern). |
| How bound cache size? | TTL first; Redis `maxmemory-policy allkeys-lru` for hard cap; per-response `max-cached-bytes` limit. |
| Cached authenticated GETs? | Currently shared cache — response for admin's /users/me shown to guest is possible. Fix: include auth context in cache key OR don't cache user-specific endpoints. |
| Why order -10 on the cache filter? | After BodyLoggingGlobalFilter (-20). Captures downstream response BEFORE client sees it. Must run before response returns. |
| Cache HIT vs stale-while-revalidate? | HIT = downstream healthy, response served without going to downstream. Stale-during-CB = downstream DOWN, cache is last resort. |
| Redis dies at request time? | Cache lookup errors → controller catches with `.onErrorResume` → static 503. Fully degraded but functional. |

---

## 10. Common pitfalls (interview probes)

1. **Caching POST responses** — would replay creation. Explicit allowlist required.
2. **Caching 5xx** — transient 500 becomes permanent for TTL. Status filter (2xx only).
3. **Set-Cookie in cached response** — auth cookie leak. Strip explicitly.
4. **Caching authenticated GETs** — user A's `/me` served to user B. Cache key needs auth context OR don't cache.
5. **Cache key collisions across routes** — same path, different backends. Include `routeId` in key.
6. **Not stripping Date header** — clients calc age from Date get wrong answer. Strip.
7. **Streaming responses cached** — breaks SSE/downloads. Content-Type filter saves you.
8. **Cache warming during slow start** — first requests bypass cache, downstream sees thundering herd. Extension: pre-warm from access logs.
9. **`forward:/fallback` losing original URL** — Spring Cloud Gateway stashes original URL in `GATEWAY_ORIGINAL_REQUEST_URL_ATTR`. Read it, not the forwarded path.
10. **`ObjectProvider` for optional cache** — FallbackController works with or without cache bean via `getIfAvailable()`. Prevents startup failure when disabled.

---

## 11. Extensions (parked)

- **Explicit invalidation endpoint** — `DELETE /admin/response-cache?routeId=...&path=...` for manual bust.
- **Pub/sub invalidation** — downstream services publish `cache.invalidate` events; gateway subscribes and drops keys. Reuse pattern from route-refresh pub/sub.
- **User-specific caching** — include auth context in key hash (safer but larger).
- **ETag / If-None-Match** — RFC 7232 support: cache Etag, return 304 if client already has it.
- **Cache warming** — script that pre-populates from access logs on startup.
- **Adaptive TTL** — extend for popular URLs, shrink for rarely-accessed.
- **Gzip compression** — compress large responses before base64 to save Redis memory.
- **Micrometer metrics** — counters `cache.hit`, `cache.miss`, `cache.write`, `cache.stale_served`; timer for cache-lookup latency.
- **Multi-tier cache** — local Caffeine (per-JVM) → Redis (shared) → downstream. Latency win, complexity cost.
- **Full stale-while-revalidate** — serve stale immediately AND fetch fresh in background (RFC 5861 real semantics; requires a background fetch mechanism).
- **Content negotiation** — cache per Accept-Language / Accept-Encoding to avoid serving wrong-locale responses.
