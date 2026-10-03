# API Gateway — Centralized CORS

Cross-Origin Resource Sharing enforced at the gateway, not in each downstream
service. One `CorsWebFilter` bean, config-driven origins/methods/headers.

Companion: [websocket.md](websocket.md) (same build session).

---

## 1. Why at the gateway?

```
┌────────────────────────────────────────────────────────────────────────┐
│  Without gateway CORS — each service handles its own                   │
│  ────────────────────                                                  │
│  user-service    → CorsFilter config                                   │
│  product-service → CorsFilter config                                   │
│  order-service   → CorsFilter config                                   │
│                                                                        │
│  Problems:                                                             │
│    ✗ Duplicate config in every service                                 │
│    ✗ Version drift — one service allows *, another restrictively       │
│    ✗ Adding a new UI origin = redeploy N services                      │
│    ✗ Preflight (OPTIONS) hits every service unnecessarily              │
│                                                                        │
│  With gateway CORS — one place                                         │
│  ─────────────────                                                     │
│  Client ──OPTIONS──▶ Gateway (responds directly, downstream never sees)│
│  Client ──GET──────▶ Gateway ──▶ downstream (already CORS-cleared)     │
│                                                                        │
│  Benefits:                                                             │
│    ✓ Single source of truth                                            │
│    ✓ Downstream services don't need CORS awareness                     │
│    ✓ Add new UI origin = one config change + refresh                   │
│    ✓ Preflight handled at the edge                                     │
└────────────────────────────────────────────────────────────────────────┘
```

**Interview soundbite**: *"CORS is a browser-imposed rule, not a security
boundary. Centralize it at the gateway; downstream services trust the gateway
to enforce it."*

---

## 2. Design decisions

### 2a. `CorsWebFilter` bean, not `spring.cloud.gateway.globalcors`

Spring Cloud Gateway offers three CORS approaches. We picked the `CorsWebFilter`
bean approach because:

- **Config-driven** via `CorsProperties` — same pattern as every other feature
- **`@ConditionalOnProperty` gated** — disable via `gateway.cors.enabled: false`
- **Programmatic flexibility** — can add per-path configs, dynamic origins from DB, etc. later

The alternative `spring.cloud.gateway.globalcors.cors-configurations`
YAML-only approach works but is harder to toggle and extend.

### 2b. Explicit origin list (not `*`)

```
allowed-origins: [http://localhost:5173, https://admin.example.com]
allow-credentials: true
```

Cannot use `*` origin when credentials (cookies, Authorization header) are
allowed — browsers reject the response entirely. The origin list must be
explicit and match exactly (scheme + host + port).

If you need wildcarding, use `allowedOriginPatterns` (`.setAllowedOriginPatterns(List.of("https://*.example.com"))`)
— that's the modern replacement that plays nicely with credentials.

### 2c. `exposed-headers` — the silent killer

Browsers **strip response headers** before your JavaScript can read them,
unless the header is listed in `Access-Control-Expose-Headers`. Without this,
your React app's `fetch().then(r => r.headers.get('X-Correlation-Id'))` returns
`null` — the header IS in the network response but JS can't see it.

Our exposed list includes every custom `X-*` header the gateway's filters set:

```
X-Correlation-Id          (correlation for tracing)
X-Idempotent-Replay        (idempotency filter marker)
X-Cache, X-Cache-Age       (cached-fallback markers)
X-Fallback-Reason          (bulkhead / CB / timeout distinguisher)
X-RateLimit-Remaining ...  (rate limiter feedback)
X-Idempotency-Bypassed     (fail-open marker)
Retry-After, Warning       (RFC standard headers)
```

Add any new custom response header to this list AND rebuild.

### 2d. Security integration

The CORS filter and Spring Security need to cooperate. Two things:

- **CORS filter runs first** — via `CorsWebFilter` registered as a plain WebFilter, ordering handled by Spring's internal chain
- **`http.cors(Customizer.withDefaults())`** — tells Spring Security to consult the CorsWebFilter bean AND permit preflight OPTIONS (which have no Authorization header)

Without step 2, browsers preflight `/api/v1/users/me` → Security rejects with
401 (no auth) → CORS fails at the preflight stage.

**Interview point**: *"the CORS filter and the security filter cooperate —
security recognizes preflight and lets it through if CORS approves. Enable
via `http.cors(...)`."*

### 2e. Preflight caching

`max-age: 3600` — browser caches the preflight response for 3600 seconds.
Subsequent requests to the same URL from the same origin skip the OPTIONS
call. Useful for chatty SPAs.

Trade-off: if you change allowed methods/headers, cached browsers still send
requests based on old preflight results for `max-age` seconds after config
change. Lower this if you frequently modify CORS config.

---

## 3. Config

```yaml
gateway:
  cors:
    enabled: true
    allowed-origins:
      - http://localhost:5173      # React admin UI
      - http://localhost:5174      # future admin UI
    allowed-methods: [GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD]
    allowed-headers:
      - Authorization
      - Content-Type
      - X-Api-Key
      - X-Correlation-Id
      - Idempotency-Key
    exposed-headers:
      - X-Correlation-Id
      - X-Idempotent-Replay
      - X-Cache
      - X-Cache-Age
      - X-Cache-Original-Url
      - X-Fallback-Reason
      - X-RateLimit-Remaining
      - X-RateLimit-Burst-Capacity
      - X-RateLimit-Replenish-Rate
      - X-Idempotency-Bypassed
      - Retry-After
      - Warning
    allow-credentials: true
    max-age: 3600
```

---

## 4. Files added / changed

```
api-gateway/
└── src/main/
    ├── java/com/example/apigateway/
    │   └── config/
    │       ├── CorsProperties.java                 (NEW)
    │       ├── CorsConfig.java                     (NEW — CorsWebFilter bean)
    │       └── GatewaySecurityConfig.java          (+ .cors(withDefaults()))
    └── resources/
        └── application.yml                         (+ gateway.cors block)

docs/microservices/api-gateway/
└── cors.md                                         (this file)
```

No new dependencies — `spring-web` (transitively present via Cloud Gateway)
provides `CorsWebFilter`.

---

## 5. Verification

### 5.1 Preflight from allowed origin → 200 with CORS headers

```bash
curl -i -X OPTIONS http://localhost:8080/api/v1/users/me \
  -H "Origin: http://localhost:5173" \
  -H "Access-Control-Request-Method: GET" \
  -H "Access-Control-Request-Headers: Authorization"

# HTTP/1.1 200 OK
# Access-Control-Allow-Origin: http://localhost:5173
# Access-Control-Allow-Methods: GET,POST,PUT,PATCH,DELETE,OPTIONS,HEAD
# Access-Control-Allow-Headers: Authorization,Content-Type,X-Api-Key,X-Correlation-Id,Idempotency-Key
# Access-Control-Allow-Credentials: true
# Access-Control-Max-Age: 3600
```

### 5.2 Preflight from disallowed origin → 403 / no CORS headers

```bash
curl -i -X OPTIONS http://localhost:8080/api/v1/users/me \
  -H "Origin: http://evil.example.com" \
  -H "Access-Control-Request-Method: GET"

# HTTP/1.1 403 Forbidden
# (or 200 with NO Access-Control-Allow-Origin header — browser rejects response either way)
```

### 5.3 Real request — CORS headers + exposed headers on response

```bash
TOKEN=$(curl -s -u admin:admin123 \
  -d "grant_type=client_credentials&scope=read" \
  http://localhost:9010/oauth2/token | jq -r .access_token)

curl -i -H "Authorization: Bearer $TOKEN" \
  -H "Origin: http://localhost:5173" \
  http://localhost:8080/api/v1/users/me

# HTTP/1.1 200 OK
# Access-Control-Allow-Origin: http://localhost:5173
# Access-Control-Allow-Credentials: true
# Access-Control-Expose-Headers: X-Correlation-Id, X-Idempotent-Replay, X-Cache, ...
# X-Correlation-Id: abc-123
# (body)
```

### 5.4 From the React admin UI

Open `http://localhost:5173` (React admin), log in, watch DevTools Network
tab:

- Preflight OPTIONS to `/oauth2/token`, `/admin/routes`, etc. all return 200
- Real requests carry `Access-Control-Allow-*` headers
- Response headers panel shows the exposed custom headers (X-Correlation-Id, etc.)

Without the CORS filter, you'd see:
```
Access to fetch at 'http://localhost:8080/admin/routes' from origin
'http://localhost:5173' has been blocked by CORS policy: Response to
preflight request doesn't pass access control check: No
'Access-Control-Allow-Origin' header is present on the requested resource.
```

### 5.5 Disable CORS

```bash
mvn -pl api-gateway spring-boot:run \
  -Dspring-boot.run.arguments="--gateway.cors.enabled=false"

# CorsWebFilter bean not created. Cross-origin requests fail with browser CORS errors.
```

---

## 6. Failure modes & mitigations

| Scenario | Behavior | Mitigation |
|---|---|---|
| `allow-credentials: true` + `allowed-origins: [*]` | Browsers reject the response | List explicit origins OR use `allowedOriginPatterns` |
| Custom X-* header not in exposed-headers | JS can't read it via `response.headers.get()` | Add to `exposed-headers` list |
| Preflight fails with 401 | Security rejecting OPTIONS before CORS | Ensure `.cors(withDefaults())` in security config |
| Config change not taking effect | Bean cached in a Spring context that survives yaml reload | Restart (or add `@RefreshScope` to CorsConfig — extension) |
| Wildcard origin needed for dev | Can't with credentials | Use `allowedOriginPatterns: ["http://localhost:*"]` |
| Method not in allowed-methods | Browser rejects preflight | Add the method OR use a permissive list `[GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD]` |
| Header name mismatch (case-sensitive?) | CORS spec is case-insensitive; issue rare | If seen, verify exact header names in preflight request |
| Two CorsWebFilter beans | Second overrides first, chaos | Ensure only one — remove any leftover `globalcors` yaml block |

---

## 7. Interview cheat-sheet

| Question | Answer |
|---|---|
| Why centralize CORS at the gateway? | Single source of truth, downstream services stay CORS-agnostic, one config change adds a new UI origin. |
| Why not `*` origin with credentials? | Browsers reject. Must list explicit origins OR use `allowedOriginPatterns` (Spring's regex-based alternative). |
| What does `Access-Control-Expose-Headers` do? | Whitelists response headers JS can read. Without it, custom `X-*` headers are stripped by the browser before fetch() sees them. |
| How do CORS + Spring Security interact? | `http.cors(Customizer.withDefaults())` tells Security to consult CorsWebFilter AND permit preflight OPTIONS. Without this, preflight hits security filter first and returns 401. |
| Preflight caching? | `max-age` seconds. Browser skips OPTIONS on repeat requests within window. Lower it if you tune CORS frequently. |
| Why does preflight exist? | Ensures the server "knows about" CORS. Prevents old servers from being tricked into cross-origin actions they weren't designed for. |
| Is CORS security? | No — it's a browser policy. Server-side auth (JWT, cookies) is the actual security. CORS just prevents accidental cross-origin browser calls. |
| Do server-to-server calls need CORS? | No — CORS is browser-only. `curl`, `httpie`, backend clients ignore it entirely. |
| When to use per-route CORS override? | Different origins per API, e.g. public API on `/public/**` allows more origins than admin API on `/admin/**`. Uses per-route `metadata.cors` yaml. |
| What if downstream also has CORS enabled? | Redundant but harmless — gateway response overrides. Best: turn OFF CORS in downstream services when gateway is authoritative. |
| WebSocket + CORS? | WebSocket handshake IS an HTTP request, but browsers apply a DIFFERENT origin check (not standard CORS). See `websocket.md`. |

---

## 8. Common pitfalls (interview probes)

1. **`*` origin + `allowCredentials=true`** — silently broken in the browser. Explicit list is required.
2. **Forgetting `exposed-headers`** — custom X-* headers invisible to JS. Common source of "why isn't my header working?" bugs.
3. **CORS enabled but security rejects preflight** — must call `.cors(withDefaults())` in security config.
4. **Case sensitivity confusion** — HTTP headers are case-insensitive but some frameworks store them case-preservingly. Test with actual browser DevTools, not just curl.
5. **Setting Origin header manually in curl** — curl doesn't enforce CORS (it's a client library, not a browser). curl is useful for verifying server headers but not for reproducing browser CORS errors.
6. **`allowedOriginPatterns` vs `allowedOrigins`** — `allowedOriginPatterns` supports wildcards and works with `allowCredentials=true`. `allowedOrigins` is exact-match. Different Spring APIs.
7. **Cookie SameSite + credentials** — even with CORS correctly configured, cookies won't be sent cross-origin unless `SameSite=None; Secure`. Two separate mechanisms.

---

## 9. Extensions (parked)

- **`@RefreshScope`** on `CorsConfig` — hot-reload origin list without restart. Same pattern as `IdempotencyProperties`.
- **`allowedOriginPatterns`** for `*.example.com` style wildcards with credentials.
- **Per-route CORS** via YAML `metadata.cors` — different origins per API path.
- **Dynamic origins from DB** — read the origin allowlist from the same DB the routes come from. Admin UI to manage allowed UIs.
- **CORS metrics** — Micrometer counter for `cors.preflight`, `cors.rejected` to see how many requests are being denied.
- **Origin allowlist audit** — log every rejected origin for detecting misconfigured clients / probing.
