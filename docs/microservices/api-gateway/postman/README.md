# Postman Collection — API Gateway

Single collection covering every feature built. Import once → click any
request → it works. Auth is auto-refreshed, IDs chain automatically, tests
validate responses.

Two files:
- `api-gateway.postman_collection.json` — 15 folders, 40+ requests
- `api-gateway.postman_environment.json` — local defaults

---

## Import + first run

```
1. Open Postman
2. File → Import → drag BOTH JSON files (or use "Upload Files")
3. Top-right dropdown: pick "API Gateway — local" environment
4. Open "00 · Setup → Get JWT token (client_credentials)"  →  Send
   → Response 200, jwt env var populated automatically
5. Click any other request in any folder  →  Send
```

The collection's pre-request script auto-refreshes the JWT before every
request when it's within 60s of expiry, so you never manually re-auth.

---

## Prereqs

Whichever features you plan to exercise, the corresponding services must be
running. Minimum for the "smoke test" (folders 00-04):

```bash
mvn -pl infra/eureka-server  spring-boot:run    # :8761
mvn -pl infra/auth-server    spring-boot:run    # :9010
mvn -pl services/user-service   spring-boot:run    # :8090
mvn -pl infra/api-gateway    spring-boot:run    # :8080
```

For folder 05 (idempotency) and 06 (response cache) also need:
```bash
docker run -d -p 6379:6379 redis:7-alpine
```

For folder 07 (dynamic routes) — H2 file mode works out of the box; Postgres
optional (see `../dynamic-routes.md`).

For folder 14 (observability):
```bash
docker-compose -f api-gateway/docker-compose.observability.yml up -d
```

---

## Folder map (matches the docs)

| Folder | Feature | Docs |
|---|---|---|
| 00 · Setup | JWT + health | — |
| 01 · Rate limiting | Redis token bucket | `../rate-limiting.md` |
| 02 · Circuit breaker + Bulkhead | Resilience4j via Spring Cloud CircuitBreaker | `../circuit-breaker.md`, `../bulkhead.md` |
| 03 · Retry + Timeout | Built-in retry filter | `../retry-timeout.md` |
| 04 · Custom filters | Correlation ID, RequestFingerprint, AddTenantHeader | `../custom-filters.md` |
| 05 · Idempotency | Stripe-pattern dedup via Redis | `../idempotency-key.md` |
| 06 · Response cache | Stale-while-CB-open | `../cached-fallback.md` |
| 07 · Dynamic routes | Admin CRUD, validation, audit | `../dynamic-routes.md` |
| 08 · API key auth | SHA-256 keys in Redis | `../api-key-auth.md` |
| 10 · CORS | Preflight + Access-Control-* | `../cors.md` |
| 11 · WebSocket | ws:// via lb:ws:// route | `../websocket.md` |
| 12 · Canary routing | Weight predicate + header override | `../canary-routing.md` |
| 13 · Admin UI | React vs Thymeleaf toggle | `../admin-ui.md`, `../admin-ui-thymeleaf.md` |
| 14 · Observability | Prometheus scrape verification | `../observability.md` |
| 15 · Runbooks | Composite scenarios | — |

(9 is intentionally skipped — Body Logging is verified via gateway stdout, not response body.)

---

## Auto-refresh JWT — how it works

Collection-level pre-request script runs before every request:

```javascript
if (!jwt || now > (expiresAt - 60000)) {
   POST /oauth2/token (client_credentials)
   → save jwt + jwt_expires_at to env
}
```

Configurable via env vars:
- `admin_client_id` — default `admin`
- `admin_client_secret` — default `admin123`
- `auth_base_url` — default `http://localhost:9010`

Change these if your auth-server runs elsewhere or you use different credentials.

---

## Chaining requests

Many folders auto-populate env vars for subsequent requests:

- **"08 · Create API key"** — saves `api_key` + `api_key_id` → next request uses `{{api_key}}` in X-Api-Key header
- **"07 · Create route"** — uses `{{demo_route_id}}` = `products-preview`; Update / Delete reference the same
- **"05 · First POST"** — saves `idem_key` for the folder's replay tests; "Reset idem_key" clears for next run

You can drive the whole thing programmatically with Postman Runner or Newman.

---

## Running via Newman (CI)

Newman = Postman's CLI runner. Great for smoke tests in pipelines.

```bash
npm install -g newman

newman run api-gateway.postman_collection.json \
       --environment api-gateway.postman_environment.json \
       --folder "00 · Setup" \
       --reporters cli,json \
       --reporter-json-export result.json

# Full sweep:
newman run api-gateway.postman_collection.json \
       -e api-gateway.postman_environment.json \
       --iteration-count 1
```

Some folders (`01`, `12 · Canary burst`) are meant for repeated iterations
via Runner — use `--iteration-count N` on those specifically:

```bash
newman run api-gateway.postman_collection.json \
       -e api-gateway.postman_environment.json \
       --folder "01 · Rate limiting" \
       --iteration-count 30
```

---

## Runbooks (folder 15)

Composite scenarios that require setup (edit backend code, restart, etc.).
Each request's `description` field has step-by-step instructions:

1. **Full canary rollout** — verify 95/5 traffic split
2. **Trigger + verify a fallback** — bulkhead saturation demo
3. **Idempotency race** — parallel requests with same key → 409 conflicts

These are the demo-worthy sequences for interviews.

---

## Postman Runner tips

- **Iterations** — repeat a folder N times. Great for rate-limiter bursts (30x), canary tallies (100x).
- **Delay** — pause between requests. Set 0 for concurrency tests, 100+ for realistic pacing.
- **Data file** — CSV with per-iteration variables. Not needed here, but handy for scripted testing.
- **Persist responses** — helpful when you want to inspect all 100 canary responses.

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| 401 on every request | Env not selected. Top-right → pick "API Gateway — local". |
| JWT refresh fails | Check auth-server is up at :9010. Console tab shows the pre-request script errors. |
| 404 on /admin/routes | Gateway not running OR `gateway.dynamic-routes.enabled=false`. |
| 429 on first request | Rate limiter is tight in dev. Wait for refill or increase `replenishRate`. |
| 503 with X-Fallback-Reason | The downstream (user-service etc.) is down. CB kicked in. |
| CORS preflight test fails | Postman doesn't ENFORCE CORS (only browsers do). We verify server-side headers here. |
| WebSocket folder empty | Postman v10+ supports WS but the collection format doesn't include WS requests inline. Right-click → Add → WebSocket Request. |
| Metrics missing in Prometheus scrape | Trigger the corresponding filter first (send a POST for idempotency counters, etc.). Metrics register lazily. |

---

## Extending the collection

Add a new request:

1. Right-click the appropriate folder → Add Request
2. Use `{{gateway_base_url}}` in the URL (not `http://localhost:8080` hardcoded)
3. Add a Test script that asserts expected status + saves any downstream IDs
4. Export the collection (three-dot menu → Export) → replace the JSON file

Add a new env var:

1. Environments tab → API Gateway — local → Add Variable
2. Export environment → replace JSON file

---

## Also useful

- `../../angle-app/frontend` — sibling React UI project (parent repo)
- `../../api-gateway-admin/README.md` — the admin UI companion to this collection
- All feature docs in `../` — 20+ markdown files covering everything shipped
