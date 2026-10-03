# API Gateway — API Key Authentication (alongside JWT)

Two auth mechanisms coexisting: **JWT** for human users, **API key** for
partner/machine-to-machine traffic. Both handled at the gateway, both fully
integrated with Spring Security (real principal, real authorities).

Real-world pattern: Stripe, Twilio, GitHub — all offer both.

---

## 1. Why two mechanisms?

```
┌───────────────────────────────────────────────────────────────────┐
│                       Who's calling?                              │
│                                                                   │
│  Human user via app             Partner backend / integration     │
│  short-lived JWT (15 min)       long-lived API key (months)       │
│         │                                    │                    │
│         ▼                                    ▼                    │
│    Authorization: Bearer …           X-Api-Key: sk_live_…         │
│                                                                   │
│   ✓ user context (sub, email)    ✓ machine identity (ownerId)    │
│   ✓ refresh-token flow           ✓ no OAuth dance                 │
│   ✓ short expiry = fast revoke    ✓ instant revoke via Redis DEL  │
└───────────────────────────────────────────────────────────────────┘
```

**Trade-off summary:**

| Aspect | JWT | API Key |
|---|---|---|
| Storage | stateless (signature verified) | stateful (Redis lookup per call) |
| Revocation | wait for expiry (or JWKS blocklist) | instant (Redis DEL) |
| Lifetime | minutes | months / years |
| Use case | interactive users | server-to-server |
| Cost per request | RSA sig verify + issuer fetch (~1ms) | Redis GET (~0.1ms) |

---

## 2. Architecture

```
                                    Client
                                      │
                                      │  Authorization: Bearer <JWT>      ← human
                                      │  OR
                                      │  X-Api-Key: sk_live_abc123        ← partner
                                      │
                                      ▼
                    ┌──────────────────────────────────────────────────┐
                    │           API Gateway :8080                      │
                    │                                                  │
                    │   Spring Security filter chain (ordered):        │
                    │                                                  │
                    │   1. CorrelationIdWebFilter                      │
                    │                                                  │
                    │   2. AuthenticationWebFilter (API key)   ← FIRST │
                    │        └─ ApiKeyAuthenticationConverter          │
                    │             extracts X-Api-Key header            │
                    │        └─ ApiKeyReactiveAuthenticationManager    │
                    │             validates via ApiKeyStore (Redis)    │
                    │                                                  │
                    │   3. AuthenticationWebFilter (JWT)               │
                    │        └─ (Spring's built-in OAuth2 resource     │
                    │             server converter — unchanged)        │
                    │                                                  │
                    │   4. AuthorizationWebFilter                      │
                    │        └─ pathMatchers + anyExchange.authed()    │
                    │                                                  │
                    │   5. Route filter chain (RateLimit, CB, Retry…)  │
                    │                                                  │
                    │   Principal in SecurityContext is one of:        │
                    │     - ApiKeyAuthentication  (API key)            │
                    │     - JwtAuthenticationToken (JWT)               │
                    └──────────────────────────────────────────────────┘
                                      │
                                      ▼
                    ┌──────────────────────────────────────────────────┐
                    │        Redis :6379                               │
                    │                                                  │
                    │   apikey:<sha256(rawKey)>  →  ApiKeyRecord JSON  │
                    │       ownerId, name, scopes, expiresAt, enabled  │
                    │   TTL: expiresAt - now (or 1y default)           │
                    └──────────────────────────────────────────────────┘
```

### Ordering explained

Our `AuthenticationWebFilter` for API keys is added at `SecurityWebFiltersOrder.AUTHENTICATION` — same slot as the JWT filter. But because we `.addFilterAt(...)` before Spring auto-adds the JWT one, ours runs first.

**Fall-through logic**:
- API-key filter's converter returns `Mono.empty()` when the header is absent → filter no-ops, next filter (JWT) runs.
- API-key header present + invalid → manager throws `BadCredentialsException` → 401. No fall-through (an invalid key isn't "try something else").
- API-key header valid → SecurityContext populated → JWT filter sees existing auth and skips.

---

## 3. Security decisions

### Never store raw keys

```
Client stores:   sk_live_abc123def456...   (kept forever, in their secret manager)
Redis stores:    SHA-256(sk_live_...)      (opaque, useless if leaked)
Lookup:          hash the incoming key → GET by hash
```

Two properties this gives you:

1. **DB breach ≠ credential leak** — attacker gets hashes, can't authenticate.
2. **Log-safe by design** — no code path in the gateway writes the raw key to Redis or logs (only the prefix + keyId ever appear).

### Why SHA-256, not bcrypt/argon2?

Bcrypt/argon2 are for *low-entropy* secrets (passwords). API keys are already 256 bits of `SecureRandom` — SHA-256 fast-hash is fine, and the constant-time cost per request matters more.

Password hashing: slow-by-design (defeats offline dictionary attacks).
Key hashing: fast (per-request cost matters more; no dictionary space to defeat).

### Prefix (`sk_live_`)

Stripe convention:
- **Human-readable identifier** — you know at a glance it's a live secret key
- **Enforces category** — `sk_test_` for test, `sk_live_` for prod; gateway can reject the wrong one
- **GitHub secret-scan compatible** — GitHub can scan repos for known prefixes and auto-revoke leaked keys

### Raw key returned ONCE

```
POST /admin/apikeys → { keyId, rawKey, warning }
                              │
                              └─ Client must persist this now.
                                 Server never stores it in plaintext,
                                 so it CANNOT be retrieved later.
```

Same UX as SSH private keys, GitHub PATs, AWS access keys. Force clients to
adopt safe storage habits from day one.

---

## 4. Config

```yaml
gateway:
  apikey:
    enabled: true                # master switch (@ConditionalOnProperty gates all beans)
    header-name: X-Api-Key
    key-prefix: sk_live_         # required prefix; other-prefixed headers are ignored
    fall-through-to-jwt: true    # (behavior of the converter, currently always true)
    cache-ttl: 60s               # reserved for a future local Caffeine cache
    excluded-paths:              # converter no-ops here (skip API key logic entirely)
      - /actuator/**
      - /fallback/**
      - /admin/**                # admin API uses JWT only
```

---

## 5. Files added / changed

```
api-gateway/
└── src/main/
    ├── java/com/example/apigateway/
    │   ├── apikey/                                                (NEW package)
    │   │   ├── ApiKeyProperties.java                              (NEW)
    │   │   ├── ApiKeyRecord.java                                  (NEW — Redis value)
    │   │   ├── ApiKeyStore.java                                   (NEW — Redis wrapper + SHA-256)
    │   │   ├── ApiKeyAuthentication.java                          (NEW — Spring Security type)
    │   │   ├── ApiKeyAuthenticationConverter.java                 (NEW — header → token)
    │   │   ├── ApiKeyReactiveAuthenticationManager.java           (NEW — validate)
    │   │   └── ApiKeyAdminController.java                         (NEW — CRUD)
    │   └── config/
    │       ├── GatewaySecurityConfig.java                         (registered API key filter)
    │       └── RateLimitConfig.java                               (apiKeyResolver uses keyId now)
    └── resources/
        └── application.yml                                        (gateway.apikey block)

docs/microservices/api-gateway/
└── api-key-auth.md                                                (this file)
```

No new dependencies — `spring-boot-starter-data-redis-reactive` + Spring Security already present.

---

## 6. Verification

Assumes:
- Redis running (`docker run -d --name gateway-redis -p 6379:6379 redis:7-alpine`)
- Eureka, auth-server, user-service, api-gateway all up

```bash
mvn -pl api-gateway clean spring-boot:run

# 0. Get an admin JWT (existing auth-server on :9010)
TOKEN=$(curl -s -u admin:admin123 \
  -d "grant_type=client_credentials&scope=read" \
  http://localhost:9010/oauth2/token | jq -r .access_token)
```

### 6.1 Create an API key via admin endpoint

```bash
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  http://localhost:8080/admin/apikeys \
  -d '{
    "ownerId": "partner-corp",
    "name": "Prod API key",
    "scopes": ["read", "write"],
    "rateLimitTier": "standard"
  }' | jq

# {
#   "keyId": "key_9f2a...",
#   "rawKey": "sk_live_a3f5b8c9d0e1f2...",         ← copy this now!
#   "warning": "Store this key now — it will not be shown again."
# }

API_KEY="sk_live_a3f5b8c9d0e1f2..."   # paste from above
```

### 6.2 Use the API key on a protected route

```bash
curl -i -H "X-Api-Key: $API_KEY" \
  http://localhost:8080/api/v1/users/me
# → 200 OK (authenticated as partner-corp)
```

### 6.3 JWT still works — both mechanisms coexist

```bash
curl -i -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/users/me
# → 200 OK (authenticated as admin via JWT)
```

### 6.4 Wrong prefix → converter skips → falls through to JWT (which fails)

```bash
curl -i -H "X-Api-Key: wrong_prefix_xyz" \
  http://localhost:8080/api/v1/users/me
# → 401 Unauthorized (no JWT either; API key ignored due to prefix mismatch)
```

### 6.5 Correct prefix + invalid key → 401

```bash
curl -i -H "X-Api-Key: sk_live_invalidkey" \
  http://localhost:8080/api/v1/users/me
# → 401 Unauthorized (converter accepts, manager rejects)
```

### 6.6 Inspect Redis

```bash
docker exec -it gateway-redis redis-cli KEYS "apikey:*"
# apikey:<sha256_hex_of_your_key>

docker exec -it gateway-redis redis-cli GET "apikey:<hash>"
# JSON blob — id, ownerId, scopes, prefix (first 10 chars), etc.
# NO raw key inside.
```

### 6.7 Per-key rate limit isolation

Create a second key with a different owner. Confirm each has its own bucket:

```bash
API_KEY_2=$(curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  http://localhost:8080/admin/apikeys \
  -d '{"ownerId":"other-partner","name":"Test","scopes":["read"]}' \
  | jq -r .rawKey)

# Note: rate limiter must be configured to use apiKeyResolver on the route
# (see RateLimitConfig.java — the resolver now uses ApiKeyAuthentication.keyId).

# Blast key 1 until it hits 429
for i in $(seq 1 30); do
  curl -s -o /dev/null -w "%{http_code} " \
    -H "X-Api-Key: $API_KEY" \
    http://localhost:8080/api/v1/products/all
done
echo

# Key 2 should still work — independent bucket
curl -i -H "X-Api-Key: $API_KEY_2" http://localhost:8080/api/v1/products/all
# → 200
```

### 6.8 Revoke a key

```bash
# Use the keyId from creation (e.g. "key_9f2a...")
curl -X DELETE -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/admin/apikeys/key_9f2a...
# → 204 No Content

# Same key now fails
curl -i -H "X-Api-Key: $API_KEY" \
  http://localhost:8080/api/v1/users/me
# → 401 Unauthorized
```

### 6.9 Admin path uses JWT only (not API key)

```bash
# Even if you have a valid API key, admin endpoints are JWT-only
curl -i -H "X-Api-Key: $API_KEY_2" \
  http://localhost:8080/admin/apikeys \
  -X POST -d '{}'
# → 401 (excluded-paths in ApiKeyAuthenticationConverter skips /admin/**)
```

---

## 7. Failure modes & mitigations

| Scenario | Behavior | Mitigation |
|---|---|---|
| Redis down | ApiKeyStore returns error; manager throws → 401 | Fail-open toggle: if Redis down + `fall-through-to-jwt=true`, skip API key and try JWT |
| Header case (X-Api-Key vs x-api-key) | `HttpHeaders.getFirst()` is case-insensitive per HTTP spec | Handled |
| Raw key in logs | Never logged (only keyId + prefix appear) | Present in code |
| Key rotation (client wants to swap) | Client creates new key, uses both concurrently, revokes old | Extension: `POST /admin/apikeys/{id}/rotate` for atomic dual-valid |
| Scope not granted → user hits endpoint | Currently anyExchange.authenticated() only — no scope check | Add `.pathMatchers("/admin/**").hasAuthority("SCOPE_admin")` |
| Multi-tenant per key | Currently ownerId is the only identity | Add tenantId field; wire to `AddTenantHeader` filter |
| Very high RPS on Redis | Every request = one GET | Add local Caffeine cache with 60s TTL (`cacheTtl` already reserved) |
| Timing attack on hash comparison | SHA-256 comparison via `equals()` — timing negligible for high-entropy values | Fine as-is; use `MessageDigest.isEqual` if paranoid |
| Client leaks key on GitHub | We don't detect | Add secret-scan endpoint (return 200/404 for "is this key valid?") |

---

## 8. Interview cheat-sheet

| Question | Answer |
|---|---|
| Why not JWT everywhere? | Different lifetime + revocation model. JWT stateless (can't revoke pre-expiry); API keys stateful (instant Redis DEL). |
| Why hash the key? | DB breach doesn't leak credentials; logs can't accidentally leak. |
| Why SHA-256 not bcrypt? | API keys are high-entropy (256 bits SecureRandom). Bcrypt is for low-entropy passwords. Different threat models. |
| Why the raw key is shown only once? | Force safe storage. Same pattern as SSH keys, GitHub PATs, AWS keys. |
| How does key rotation work? | Create new key, use both concurrently, revoke old. Zero-downtime. |
| Why prefix (`sk_live_`)? | Instant identification; env-aware (`sk_test_` for test); GitHub secret-scanning friendly. |
| API key AND JWT both sent? | API key wins (cheaper lookup). Alternative: reject with 400. |
| Rate limit per key or per owner? | Per KEY id — one owner may have keys with different tiers. |
| How do scopes work? | Redis record has `scopes: ["read","write"]`; converted to `SimpleGrantedAuthority("SCOPE_read")` etc. Then `@PreAuthorize("hasAuthority('SCOPE_write')")`. |
| Spring Security integration pattern? | `ServerAuthenticationConverter` (extract) + `ReactiveAuthenticationManager` (validate) + `AuthenticationWebFilter` (glue) + `.addFilterAt(...)` at `SecurityWebFiltersOrder.AUTHENTICATION`. |
| Why reactive versions? | Gateway is Netty. Blocking Spring Security = blocked event loop = disaster under load. |
| Multi-tenancy? | Include `tenantId` in `ApiKeyRecord`; propagate via `AddTenantHeader`-style filter. |
| Downstream services need to know the caller? | Either forward `X-Api-Key-Owner` header, or mint a short-lived JWT with owner+scopes for uniform downstream auth. |

---

## 9. Common pitfalls (interview probes)

1. **Order matters** — our filter must be added at `SecurityWebFiltersOrder.AUTHENTICATION`. Before that (INIT) has no SecurityContext; after (AUTHORIZATION) is too late.
2. **Converter returning `null` vs `Mono.empty()`** — must be `Mono.empty()` for the fall-through to work. Returning `null` breaks reactive contract.
3. **Manager throwing vs returning empty** — throwing = 401 (client sent invalid key). Returning `Mono.empty()` = no auth attempted (would fall through). Wrong choice = wrong error.
4. **Bean visibility** — `ApiKeyReactiveAuthenticationManager` gated by `@ConditionalOnProperty`. Security config must use `ObjectProvider` to allow disabled state.
5. **Rate limiter key format** — the resolver returns `"apiKey:" + keyId`. If the resolver runs BEFORE our filter (misconfigured route), the principal isn't set → falls back to `"apiKey:missing"` and all traffic shares one bucket.
6. **`@ConditionalOnProperty` cascade** — when disabled, admin controller + store + manager all disappear. No leftover partial state.

---

## 10. Extensions (parked)

- **Local Caffeine cache** — `cacheTtl` property exists but unused. Wrap `ApiKeyStore.lookup()` with `Cache<String, ApiKeyRecord>` to reduce Redis load. Trade-off: revocation delayed up to cache TTL.
- **Scope-based path authorization** — `pathMatchers("/admin/**").hasAuthority("SCOPE_admin")`.
- **Rotation endpoint** — `POST /admin/apikeys/{id}/rotate` returns a new key; both old + new valid for N days, then old auto-revoked.
- **Secret scanning callback** — `POST /admin/apikeys/verify` (unauthenticated) returns 200/404 for GitHub secret scanning integrations.
- **Downstream JWT minting** — the API-key-authenticated request has no JWT to forward. Add a filter that mints a short-lived JWT (owner + scopes) so downstream services can enforce authz uniformly.
- **Micrometer metrics** — counter `apikey.auth.success`, `apikey.auth.failure`, timer `apikey.lookup.duration`.
- **Two-chain Security config** — split `/partner/**` and `/api/**` into separate `SecurityWebFilterChain` beans for cleaner isolation.
- **Admin usage stats** — endpoint returning per-key request counts, last-used timestamp, error rates.
