# API Gateway — Redis-backed Rate Limiting

Config-driven, per-route rate limiting using the token-bucket algorithm, backed by Redis.
Toggle globally via `gateway.ratelimit.enabled` or swap route definitions with the `nolimit` Spring profile.

---

## 1. Why rate-limit at the gateway?

```
                       ┌───── denies at edge (cheap)
   burst / abuse ──▶ Gateway ──▶ downstream services
                       └───── protects DB, CPU, quotas
```

- **One place, one policy** — no per-service duplication.
- **Fail-closed** — Redis is authoritative and shared across gateway replicas.
- **Cheap** — 429 is returned before hitting service business logic.

Where NOT to do it: inside each microservice (per-JVM counters → useless behind a load balancer).

---

## 2. Architecture

```
                    ┌──────────────────────────────────────────────────────────┐
                    │                 API Gateway :8080                        │
Client ──JWT──▶     │                                                          │
                    │  CorrelationIdWebFilter                                  │
                    │        │                                                 │
                    │        ▼                                                 │
                    │  ┌────────────────────────────────────────────────────┐  │
                    │  │ Route: user-service                                │  │
                    │  │   filters:                                         │  │
                    │  │    1. RequestRateLimiter                           │  │
                    │  │        keyResolver  = userKeyResolver              │  │
                    │  │        rateLimiter  = redisRateLimiter (@Primary)  │  │
                    │  │    2. TokenRelay                                   │  │
                    │  └────────────────────────────────────────────────────┘  │
                    │                                                          │
                    │        (if bucket empty → HTTP 429 + Retry-After)        │
                    │                                                          │
                    │  RateLimitErrorHandler  ── wraps 429 as JSON body        │
                    └──────────────────────────────────────────────────────────┘
                                          │
                                          ▼
                    ┌───────────────────────────────────────────────┐
                    │  Redis :6379                                  │
                    │  ─ Atomic Lua script (GET+DECR+SET+EXPIRE)    │
                    │  ─ Keys:                                      │
                    │      request_rate_limiter.{user:alice}.tokens │
                    │      request_rate_limiter.{user:alice}.ts     │
                    └───────────────────────────────────────────────┘
```

---

## 3. Token bucket, in 30 seconds

```
     replenishRate = 10 tokens/sec       burstCapacity = 20
                 │
                 ▼
        ┌──────────────────────┐
        │  ▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░│   ← current tokens
        └──────────────────────┘
        Each request costs `requestedTokens` (default 1).
        If tokens >= cost:  allow, decrement.
        Else:               deny with 429.
        Refill: max(capacity, current + elapsedSec * replenishRate)
```

Why token bucket (vs leaky bucket / fixed window):
- Allows **bursts** up to `burstCapacity` — good UX for real users.
- Refills continuously (not per second boundary) → no thundering herd at `:00`.
- Fits Redis atomic ops naturally (one Lua script per request).

---

## 4. Config schema

```yaml
gateway:
  ratelimit:
    enabled: true                # master switch — false disables all limiter beans
    key-strategy: USER           # USER | IP | API_KEY  (informational; actual resolver picked in yml)
    defaults:                    # applied by RedisRateLimiter @Primary bean
      replenish-rate: 10
      burst-capacity: 20
      requested-tokens: 1
    routes:                      # per-route metadata (currently informational; route yml still authoritative)
      user-service:
        enabled: true
        limit: { replenish-rate: 10, burst-capacity: 20, requested-tokens: 1 }
```

The actual filter numbers live inside `spring.cloud.gateway.routes[].filters` because Spring Cloud Gateway reads them at route-build time. `gateway.ratelimit.routes.*` mirrors those values so admin endpoints (future work) can display and validate them.

---

## 5. Key resolvers — pick one per route

| Resolver | Bean name | When to use | Key format |
|---|---|---|---|
| User (JWT sub) | `userKeyResolver` | Authenticated APIs | `user:<sub>` or `anon:<ip>` |
| IP | `ipKeyResolver` | Public / unauthenticated | `ip:<addr>` |
| API Key | `apiKeyResolver` | Partner integrations | `apiKey:<X-Api-Key>` |

Reference from `application.yml` via SpEL: `#{@userKeyResolver}`.

Interview point: *"Why three resolvers? Different traffic classes need different fairness models — a shared partner API key should aggregate all its traffic, but IP-based limiting is unfair to NAT users behind a corporate proxy."*

---

## 6. How the "enable/disable" toggle works

Three layers of control, from coarse to fine:

### Layer A — bean-level (Spring `@ConditionalOnProperty`)
`RateLimitConfig` is annotated:
```java
@ConditionalOnProperty(prefix = "gateway.ratelimit", name = "enabled", havingValue = "true")
```
When `false`, none of `redisRateLimiter`, `userKeyResolver`, `ipKeyResolver`, `apiKeyResolver` are created. **However**, if the route yml still references `#{@userKeyResolver}`, gateway startup fails with `BeanNotFoundException`. That's why we also need Layer B.

### Layer B — profile-scoped routes (`application-nolimit.yml`)
Overrides the routes without the `RequestRateLimiter` filter:
```bash
mvn spring-boot:run -pl infra/api-gateway \
  -Dspring-boot.run.profiles=nolimit
```
Now the filter chain itself doesn't include the limiter. Cleaner than "enabled=false" alone.

### Layer C — runtime toggle (future work, `@RefreshScope`)
Add `spring-cloud-starter-config` + `@RefreshScope` on `RateLimitConfig`, push new config, then:
```bash
curl -X POST http://localhost:8080/actuator/refresh
```

**Recommended matrix**:

| Environment | Setting |
|---|---|
| Local dev, exploring | `enabled: false` OR run with `-Pnolimit` |
| Local dev, testing limits | `enabled: true`, run Redis in Docker |
| Staging | `enabled: true`, generous limits |
| Production | `enabled: true`, tight limits + `@RefreshScope` for tuning |

---

## 7. Files added / changed

```
api-gateway/
├── pom.xml                                             (+ spring-boot-starter-data-redis-reactive)
└── src/main/
    ├── java/com/example/apigateway/config/
    │   ├── RateLimitProperties.java                    (NEW — typed config)
    │   ├── RateLimitConfig.java                        (NEW — beans, @ConditionalOnProperty)
    │   └── RateLimitErrorHandler.java                  (NEW — JSON 429 body)
    └── resources/
        ├── application.yml                             (routes + gateway.ratelimit block)
        └── application-nolimit.yml                     (NEW — disable profile)
```

---

## 8. Running it

### 8.1 Start Redis
```bash
docker run -d --name gateway-redis -p 6379:6379 redis:7-alpine
docker exec -it gateway-redis redis-cli PING   # PONG
```

### 8.2 Start dependencies
```bash
# In separate terminals or via your usual launcher:
mvn -pl infra/eureka-server        spring-boot:run
mvn -pl infra/auth-server          spring-boot:run
mvn -pl services/user-service         spring-boot:run
mvn -pl infra/api-gateway          spring-boot:run
```

### 8.3 Get a JWT from the auth-server (port 9010, admin:admin123)
```bash
TOKEN=$(curl -s -u admin:admin123 \
  -d "grant_type=client_credentials&scope=read" \
  http://localhost:9010/oauth2/token | jq -r .access_token)
echo "$TOKEN" | cut -c1-40
```

### 8.4 Hammer the limited route
```bash
# user-service: replenish=10/s, burst=20 → first ~20 succeed, then 429
for i in $(seq 1 30); do
  curl -s -o /dev/null -w "%{http_code} " \
    -H "Authorization: Bearer $TOKEN" \
    http://localhost:8080/api/v1/users/me
done
echo
```

### 8.5 Inspect Redis state
```bash
docker exec -it gateway-redis redis-cli KEYS "request_rate_limiter*"
# request_rate_limiter.{user:admin}.tokens
# request_rate_limiter.{user:admin}.timestamp

docker exec -it gateway-redis redis-cli GET "request_rate_limiter.{user:admin}.tokens"
```

### 8.6 Verify per-user isolation
```bash
# Different principal → different bucket
TOKEN2=$(curl -s -u client-app:secret \
  -d "grant_type=client_credentials" \
  http://localhost:9010/oauth2/token | jq -r .access_token)

curl -i -H "Authorization: Bearer $TOKEN2" \
  http://localhost:8080/api/v1/users/me
# 200 with fresh X-RateLimit-Remaining: 19
```

### 8.7 Response headers to look for
On a **200 OK**:
```
X-RateLimit-Remaining: 19
X-RateLimit-Requested-Tokens: 1
X-RateLimit-Burst-Capacity: 20
X-RateLimit-Replenish-Rate: 10
```
On a **429 Too Many Requests**:
```
Retry-After: 1
Content-Type: application/json
{"error":"rate_limited","message":"Too many requests, slow down.","correlationId":"..."}
```

### 8.8 Disable the limiter — profile mode
```bash
mvn -pl infra/api-gateway spring-boot:run \
  -Dspring-boot.run.profiles=nolimit

# Now hammer freely — all 200
for i in $(seq 1 100); do
  curl -s -o /dev/null -w "%{http_code} " \
    -H "Authorization: Bearer $TOKEN" \
    http://localhost:8080/api/v1/users/me
done
```

---

## 9. Failure modes & mitigations

| Scenario | Default behavior | Mitigation |
|---|---|---|
| Redis down | RequestRateLimiter denies (empty response → 500) | Set `spring.cloud.gateway.filter.request-rate-limiter.deny-empty-key=false` OR wrap with a CircuitBreaker fallback route |
| Anonymous request (no JWT) | `userKeyResolver` falls back to `anon:<ip>` | Consider dedicated public routes with `ipKeyResolver` |
| Missing `X-Api-Key` for apiKeyResolver | Bucket key becomes `apiKey:missing` (shared bucket for all) | Add pre-filter that rejects requests without the header |
| Two gateway replicas | Both write same Redis keys → shared bucket ✓ | Nothing — this is the design |
| JWT `sub` collision | Two orgs with the same user id → shared bucket | Compose key with `iss + sub` in a custom resolver |

---

## 10. Interview cheat-sheet

| Question | Concise answer |
|---|---|
| Why token bucket over fixed window? | Bursts allowed, no boundary spikes, atomic in Redis via Lua |
| Why Redis, not in-memory Caffeine/Guava? | Multiple gateway pods must share state; single-node limits are per-JVM |
| Why the Lua script? | Atomic read-decide-write; without it, two gateways racing the same key overspend the bucket |
| How does the `@ConditionalOnProperty` toggle work? | Bean factory skips creating limiter beans; routes must be swapped too (via `nolimit` profile) to avoid missing-bean errors |
| Different limits per user tier (free/pro)? | Custom `KeyResolver` that appends tier → different key → different bucket per tier; or a custom `RateLimiter` that reads tier→limits from a store |
| How to change limits without redeploy? | `@RefreshScope` on `RateLimitConfig` + `/actuator/refresh` after external config change |
| What's the trade-off vs sidecar (Envoy)? | Java-gateway keeps everything in one JVM (simpler ops, JWT/routing/limiter co-located); Envoy is language-agnostic but adds a hop and a config surface |

---

## 11. Extensions (parked)

- **Per-tier limits** — read `tier` claim from JWT, compose into key.
- **Dynamic config** — `@RefreshScope` + Spring Cloud Config server.
- **Admin endpoint** — `/admin/ratelimit/status` listing current buckets.
- **Composite key** — `user:<sub> + route:<id>` so limits are per user *per endpoint*.
- **Alerting** — Micrometer counter on 429s → Prometheus alert when > N/sec.
