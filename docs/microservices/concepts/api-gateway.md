# API Gateway — Spring Cloud Gateway

> **The single front door for every HTTP client.** All real traffic enters
> through `localhost:8080`.
>
> On every request, the gateway **authenticates** (JWT),
> **routes** (via Eureka), **logs** (body + correlation-id), and
> wraps the downstream call in a **circuit breaker + bulkhead**.
> Depending on the matched route, it may also **rate-limit**,
> **retry**, **rewrite the path**, **enforce an idempotency key**, or
> **replay a cached response** when the breaker opens.
>
> See [§5](#5-features-built) for the full "always-on / per-route /
> conditional" status of each feature.

**Module:** `infra/api-gateway/` · **Port:** `8080` · **Stack:** Reactive (Spring WebFlux + Netty)

---

## Table of contents

1. [What is an API gateway (concept)](#1-what-is-an-api-gateway)
2. [Gateway vs direct port — when to call which](#2-gateway-vs-direct-port)
3. [Request lifecycle in one picture](#3-request-lifecycle)
4. [Route table — what we have built](#4-route-table)
5. [Features built in this project (21 of them)](#5-features-built)
5.5. [Gateway vs service — who should handle what](#55-gateway-vs-service--who-should-handle-what)
6. [Config file tour (`application.yml`)](#6-config-file-tour)
7. [How to USE it (common tasks)](#7-how-to-use)
8. [How to CUSTOMISE it](#8-how-to-customise)
9. [Observing it live](#9-observing-it-live)
10. [Common failure modes](#10-common-failure-modes)
11. [Interview talking points](#11-interview-talking-points)
12. [How to tail gateway logs (compose-profile gotcha)](#12-how-to-tail-gateway-logs-and-the-compose-profile-gotcha)

---

## 1. What is an API gateway

**The problem**: with N microservices, every client (browser, mobile, Postman, another backend) would otherwise need to:

- Know N different base URLs
- Repeat auth logic for every backend
- Implement its own rate-limiting / retry / circuit-breaking
- Deal with CORS N times
- Trace a request across services on its own

**The solution**: put ONE service in front. Clients talk only to the gateway. Cross-cutting concerns (auth, retry, rate-limit, CORS, idempotency, tracing, caching) live in one place. Downstream services stay simple — they can assume "if the request got here, it was already authenticated".

```
Without a gateway                      With a gateway
───────────────────                    ──────────────
                                       Client
Client ──► user-service :8081          │
Client ──► product-service :8082       │  Authorization: Bearer eyJ…
Client ──► order-service   :8083       ▼
                                       Gateway :8080 ─┬─► user-service
each client re-implements:             │               ├─► product-service
  - JWT validation                     │               ├─► order-service
  - rate limit                         ▼               ├─► payment-service
  - retry logic                        auth check     └─► notification
  - CORS                               rate limit
  - correlation IDs                    retry
                                       circuit break
                                       idempotency
                                       body logging
                                       correlation ID
                                       → forward
```

**Spring Cloud Gateway** is the implementation we use. It's **reactive** (non-blocking I/O on Netty), which makes it suited for the gateway's job: holding many concurrent connections open while waiting for downstream services.

### 1.1 How the common gateways compare

API-gateway is a *role*, not a product — many implementations exist. The right pick depends on cloud, language, and operational posture (managed service vs self-hosted).

| Gateway | Strongest in | Best for |
|---|---|---|
| **AWS API Gateway** | Security, rate limiting, zero-ops (fully managed) | Apps on AWS, especially fronting Lambda |
| **Azure API Management** | Security, monitoring, developer portal | Teams standardized on Microsoft Azure |
| **Google Apigee** | Monitoring, analytics, API lifecycle management | Large enterprises monetizing / managing many APIs |
| **Google Cloud API Gateway** | Simple routing + security | Small apps on Google Cloud |
| **Kong** | Plugins, flexibility, performance | Teams wanting open-source + heavy customization |
| **NGINX** | Routing, load balancing, caching, raw speed | High-traffic sites where latency per hop matters |
| **Spring Cloud Gateway** ← *this project* | Routing + custom filters in Java | Spring Boot microservices where filters share domain code with services |

**Why this project picked Spring Cloud Gateway**:
- The rest of the stack is Spring Boot — same build (Maven), same observability stack (Micrometer, Actuator), same DI container. Custom filters can `@Autowired` the same configuration properties as services.
- Reactive runtime (Netty) scales to many concurrent connections without a thread-per-request model.
- Open-source, self-hosted — no cloud lock-in; runs in nano via `docker compose` on your laptop.
- Trade-off: single JVM throughput is lower than NGINX/Kong; latency per hop is higher. Fine for most teams; large-scale fleets often front SCG with NGINX for TLS termination + static caching.

---

## 2. Gateway vs direct port — when to call which

The single question that trips people up: *"should I call `localhost:8080/...` or `localhost:8081/...`?"*

### Rule of thumb

```
┌──────────────────────────────────────────────────────────────────────┐
│  Path starts with /api/v1/...   →   use gateway  (:8080)             │
│  Path is /actuator, /eureka,    →   use direct port                  │
│    /webhooks, /admin/dlq, etc.                                       │
└──────────────────────────────────────────────────────────────────────┘
```

Why: the gateway only knows the routes we explicitly configured (see [§4 Route table](#4-route-table)). Anything else → 404 at the gateway, so you must go direct.

### Port cheat sheet

| Service | Direct port | Via gateway? |
|---|---|---|
| api-gateway | **8080** | — (it IS the gateway) |
| eureka | 8761 | ❌ no gateway route |
| config-server | 8888 | ❌ no gateway route |
| auth-server | 8095 | ❌ no gateway route |
| user-service | 8081 | ✅ `/api/v1/users/**` |
| product-service | 8082 | ✅ `/api/v1/products/**` |
| order-service | 8083 | ✅ `/api/v1/orders/**`, `/api/v1/checkout/**`, `/admin/orders/**`, `/razorpay/checkout`, `/ws/**` |
| payment-service | 8091 | ✅ `/api/v1/payments/**` |
| notification | 8099 | ❌ no gateway route (direct only) |

### Three legitimate reasons to call direct

1. **Infra / actuator endpoints** — `curl http://localhost:8081/actuator/health` — no choice, the gateway doesn't route these.
2. **Debug isolation** — "the gateway call failed, is the problem the gateway or the service?" Hit the service's own port to find out.
3. **Public webhooks** — Stripe / Razorpay callbacks don't carry our JWT, so they go direct to payment-service `/webhooks/*`.

### Service-to-service calls (east/west)

Internal service calls — e.g. order-service calling product-service to check stock — do **NOT** go through the gateway. They go: Eureka lookup → load balancer → direct over the service network. The gateway is north/south only.

---

## 3. Request lifecycle

What actually happens on `POST /api/v1/orders` through the gateway, in order. The **When** column shows what has to be true for that step to do anything observable.

```
Client sends request with Authorization: Bearer eyJ… + Idempotency-Key
    │
    ▼
┌───────── Gateway (Netty, event loop) ─────────────────┬──── When ──────────────────┐
│                                                       │                            │
│  1. Correlation-ID filter   assign X-Correlation-Id   │ ALWAYS                     │
│  2. CORS                     preflight short-circuits │ only on cross-origin/OPTS  │
│  3. API-key check            validate X-Api-Key→Redis │ only if X-Api-Key present  │
│  4. JWT resource-server      validate sig + exp + iss │ ALWAYS (nano uses JWT)     │
│  5. Admin authority check    /admin/** → scope=admin  │ only on /admin/**          │
│  6. Match route by predicate Path=/api/v1/orders/**   │ ALWAYS                     │
│  7. Bulkhead                 orderCB slot available?  │ ALWAYS wraps; blocks only  │
│                                                       │   if 5 concurrent in-flight│
│  8. Circuit breaker          orderCB open? fallback   │ ALWAYS wraps; short-cuts   │
│                                                       │   only if CB=OPEN          │
│  9. Idempotency filter       seen this key? replay    │ only on POST/PATCH /orders │
│ 10. Request body logging     redact + log req body    │ ALWAYS (sample=1.0 in nano)│
│ 11. Rate limiter             Redis token bucket       │ user + product routes only │
│                                                       │   (NOT on order route)     │
│ 12. Request fingerprint      SHA-256 → header         │ only if filter in route    │
│ 13. Discovery lookup         lb://order-service       │ ALWAYS (route uses lb://)  │
│ 14. Load balancer            pick healthy instance    │ ALWAYS                     │
│ 15. TokenRelay               forward JWT downstream   │ ALWAYS (default-filter)    │
│ 16. HTTP client (Netty)      TCP to :8083             │ ALWAYS                     │
│                                                       │                            │
└───────────────────────────────┬───────────────────────┴────────────────────────────┘
                                │
                                ▼
                       order-service :8083
                                │
                                ▼ (response bubbles back through same chain in reverse)
                 Response body logging          │ ALWAYS (sample=1.0 in nano)
                 Response cache capture         │ GET 2xx only (POST/PATCH never)
                                │
                                ▼
                             Client
```

**Important: "wraps" vs "fires"**

Several filters (bulkhead, circuit breaker, response cache) run on every matching request but their observable effect is conditional:

- **Bulkhead** always counts concurrent calls; blocks only when the cap is hit.
- **Circuit breaker** always wraps the downstream call; short-circuits only when enough failures have accumulated to open it.
- **Response cache** always considers caching GET/HEAD responses, but only replays an old entry when the CB is open AND the current call fails.

If the downstream call fails or times out, retry fires (if the route has a Retry filter AND the method is in its allowed list — the order route has no Retry on purpose, writes shouldn't be auto-replayed). If retries exhaust and the CB error-rate threshold trips, the breaker opens and future calls short-circuit to the `forward:/fallback/orders` route (which replays a cached GET if available, or returns a static 503 for writes).

---

## 4. Route table

Defined in `infra/api-gateway/src/main/resources/application.yml` under `spring.cloud.gateway.routes`.

| Route id | Path predicate | Extra predicate | Target | Notable filters |
|---|---|---|---|---|
| `user-service-v2-override` | `/api/v1/users/**` | `Header=X-Canary,force-v2` | `lb://user-service` | RewritePath → v2, RateLimit, AddTenantHeader |
| `user-service-v1` | `/api/v1/users/**` | `Weight=userGroup,95` | `lb://user-service` | RateLimit, Bulkhead, CB, Retry, FingerPrint, TenantHeader |
| `user-service-v2` | `/api/v1/users/**` | `Weight=userGroup,5` | `lb://user-service` | RewritePath → v2, same chain as v1 |
| `product-service` | `/api/v1/products/**` | — | `lb://product-service` | RateLimit (by IP), Bulkhead, CB, Retry |
| `order-service` | `/api/v1/orders/**`, `/api/v1/checkout/**` | — | `lb://order-service` | Bulkhead, CB, FingerPrint, **IdempotencyKey** (no Retry — writes) |
| `razorpay-checkout-page` | `/razorpay/checkout` | — | `lb://order-service` | public (browser GET) |
| `websocket-echo` | `/ws/**` | — | `lb:ws://user-service` | TokenRelay (WS upgrade) |
| `order-service-admin` | `/admin/orders/**` | — | `lb://order-service` | admin auth required |
| `payment-service` | `/api/v1/payments/**` | — | `lb://payment-service` | — |
| `openapi-<svc>` | `/v3/api-docs/<svc>` | — | `lb://<svc>` | SetPath=/v3/api-docs (aggregated Swagger) |

**Route evaluation**: first match wins. Order in the yml matters when predicates could overlap — e.g. the `X-Canary` header route is listed first so it beats the Weight split.

---

## 5. Features built in this project

Each feature has its own deep-dive doc in `docs/microservices/api-gateway/` — this is the one-line "what and why" index.

**Status legend** — which features actually touch your request:

- 🟢 **Always-on**: runs on every request that reaches the gateway
- 🟡 **Per-route**: configured on some routes, not others
- 🔵 **Conditional**: runs only when a specific trigger is present (header, failure, etc.)
- ⚪ **Wrapper**: in the chain on every request but observable only under its trigger condition

| # | Feature | Status | What it does | Which routes USE it today | Config key | Deep-dive |
|---|---|---|---|---|---|---|
| 1 | **JWT resource-server** | 🟢 Always-on | Validates Bearer JWT (sig + exp + iss) against auth-server JWKS | ALL `/api/v1/**` + `/admin/**` | `spring.security.oauth2.resourceserver.jwt.issuer-uri` | — |
| 2 | **TokenRelay** | 🟢 Always-on | Forwards the JWT header downstream | All routes (set as `default-filter`) | `spring.cloud.gateway.default-filters` | — |
| 3 | **Routing (Eureka-based)** | 🟢 Always-on | `lb://<service>` → Eureka lookup → load-balanced instance IP | All 10 routes | `spring.cloud.gateway.routes[].uri` | — |
| 4 | **Body logging + redaction** | 🟢 Always-on | Audit log req/resp bodies, mask password/token/CC + sampling | All (except `excluded-paths`) | `gateway.body-logging.*` | [body-logging.md](../api-gateway/body-logging.md) |
| 5 | **Correlation-id, tracing, info** | 🟢 Always-on | X-Correlation-Id propagation + Micrometer tracing + build info | All | built-in + micrometer | [logging-tracing-info.md](../api-gateway/logging-tracing-info.md), [observability.md](../api-gateway/observability.md) |
| 6 | **Rate limiting** | 🟡 Per-route | Redis token bucket per key | `user-service-v1/v2` (by user id), `product-service` (by IP) — NOT on `order-service` | `gateway.ratelimit.*` + per-route `RequestRateLimiter` | [rate-limiting.md](../api-gateway/rate-limiting.md) |
| 7 | **Retry** | 🟡 Per-route | Exponential backoff on 5xx | `user-service-v1` (3 retries, GET/PUT/DELETE), `product-service` (2 retries, GET only) — NOT on `order-service` (writes mustn't duplicate) | per-route `Retry` filter | [retry-timeout.md](../api-gateway/retry-timeout.md) |
| 8 | **Path rewrite** | 🟡 Per-route | Regex path rewrite | `user-service-v2` + `-v2-override` (`/api/v1/users/x` → `/api/v2/users/x`), `openapi-*` (`SetPath=/v3/api-docs`) | per-route `RewritePath` / `SetPath` filter | — |
| 9 | **Idempotency-Key** | 🟡 Per-route | Stripe-style POST/PATCH dedup via Redis | `order-service` only | `gateway.idempotency.*` + `IdempotencyKey` filter | [idempotency-key.md](../api-gateway/idempotency-key.md) |
| 10 | **Request fingerprinting** | 🟡 Per-route | SHA-256 hash of path/body; used by idempotency | `user-service-v1`, `order-service` | `RequestFingerprint` filter | — |
| 11 | **Canary routing** | 🟡 Per-route | 95/5 Weight split for user-service + `X-Canary` override | `user-service-*` only | `gateway.canary.*` + `Weight` predicate | [canary-routing.md](../api-gateway/canary-routing.md) |
| 12 | **Circuit breaker** | ⚪ Wrapper | Wraps every business route; opens on failure-rate threshold → fallback URI | All business routes (userCB, productCB, orderCB) — fires only when enough failures accumulate | `gateway.circuitbreaker.*` + `CircuitBreaker` filter | [circuit-breaker.md](../api-gateway/circuit-breaker.md) |
| 13 | **Bulkhead** | ⚪ Wrapper | Caps concurrent in-flight calls per downstream | All business routes — rejects only when cap is hit (user=15, product=20, order=5) | `gateway.circuitbreaker.*.bulkhead` + `Bulkhead` filter | [bulkhead.md](../api-gateway/bulkhead.md) |
| 14 | **Response cache (stale-while-CB-open)** | ⚪ Wrapper | Captures 2xx GET/HEAD to Redis; replays with `Warning: 110` when CB fires | All GET/HEAD (except excluded paths); replay only when CB is OPEN | `gateway.response-cache.*` | [cached-fallback.md](../api-gateway/cached-fallback.md) |
| 15 | **Timeout** | ⚪ Wrapper | Global Netty client `connect` + `response-timeout` | All routes — triggers only when downstream slow | `spring.cloud.gateway.httpclient.*` | [retry-timeout.md](../api-gateway/retry-timeout.md) |
| 16 | **CORS** | 🔵 Conditional | Centralized preflight + allowed origins/methods | Only cross-origin or OPTIONS requests | `gateway.cors.*` | [cors.md](../api-gateway/cors.md) |
| 17 | **API-key auth** | 🔵 Conditional | `X-Api-Key: sk_live_*` → Redis lookup; falls through to JWT if missing | Only when `X-Api-Key` header present | `gateway.apikey.*` | [api-key-auth.md](../api-gateway/api-key-auth.md) |
| 18 | **Admin authority gate** | 🔵 Conditional | Requires `SCOPE_admin` / `ROLE_ADMIN` on the token | Only `/admin/**` paths | `gateway.admin.*` | — |
| 19 | **WebSocket** | 🔵 Conditional | `lb:ws://` for load-balanced WS upgrade with TokenRelay | Only `/ws/**` path | `websocket-echo` route | [websocket.md](../api-gateway/websocket.md) |
| 20 | **Dynamic routes** | 🔵 Conditional | Runtime add/edit via `/admin/routes`, DB-persisted, Redis pub/sub broadcast | Only when admin creates a dynamic route | `gateway.dynamic-routes.*` | [dynamic-routes.md](../api-gateway/dynamic-routes.md), [-pubsub.md](../api-gateway/dynamic-routes-pubsub.md) |
| 21 | **Admin UI** | 🔵 Conditional | React (default) or Thymeleaf UI at `/admin/ui` | Only when a human opens the UI | `gateway.admin.ui` | [admin-ui.md](../api-gateway/admin-ui.md), [-thymeleaf.md](../api-gateway/admin-ui-thymeleaf.md) |

Also: **custom filters** registered as `@Component` get picked up automatically — see [custom-filters.md](../api-gateway/custom-filters.md).

### What this means for a typical nano-mode request

For a standard `GET /api/v1/users` (JWT, no idempotency header, downstream healthy):

- ✅ **Always-on fires** — JWT validated, routing happens, body logged, correlation-id added, TokenRelay forwards the token
- ✅ **Per-route fires** — rate limit decrement in Redis, fingerprint header added, response-cache capture considers caching the 200
- ⚪ **Wrappers run but don't act** — CB stays CLOSED (no failures), bulkhead has free slots, retry has nothing to retry, timeout doesn't trip
- ❌ **Conditional skipped** — no CORS (same-origin curl), no API-key, no admin check, no idempotency (GET), no WebSocket upgrade

So 11 features *run* but only ~6 do visible work. Keep that in mind when reading the lifecycle in [§3](#3-request-lifecycle).

---

## 5.5 Gateway vs service — who should handle what

A common mistake: either dumping too much into the gateway (slow, hard-to-reason-about layer) OR duplicating gateway-shaped work inside every service (DRY violation, impossible to standardize). Use this table to decide.

### Rule of thumb

```
┌──────────────────────────────────────────────────────────────────────┐
│  Does it apply the SAME WAY to every service?                        │
│    yes → gateway                                                     │
│    no  → service                                                     │
│                                                                      │
│  Does it need the DOMAIN MODEL (entities, business rules)?           │
│    yes → service                                                     │
│    no  → candidate for gateway                                       │
└──────────────────────────────────────────────────────────────────────┘
```

### Decision matrix

Legend: ✅ do it here · ⚠️ sometimes · ❌ don't put it here · **In this project**: `yes @ <where>` / `no`

| Concern | Gateway | Service | In this project | Why / caveat |
|---|:---:|:---:|---|---|
| **Authentication** (JWT validation) | ✅ | ❌ | yes @ gateway | Validate once at the edge. Services trust the propagated JWT. |
| **Authorization** — coarse (`/admin/**` scope check) | ✅ | ❌ | yes @ gateway (`AdminJwtAuthenticationConverter`) | Keep role / scope gates at edge. |
| **Authorization** — fine (`user owns this order`) | ❌ | ✅ | no | Needs DB lookup; the gateway doesn't own the data. |
| **Rate limiting** — global per user / IP | ✅ | ❌ | yes @ gateway (user, product) | One Redis bucket beats N uncoordinated per-service buckets. |
| **Rate limiting** — per domain object (per-order throttle) | ❌ | ✅ | no | Needs domain context. |
| **Retry** — idempotent GET / PUT / DELETE | ✅ | ❌ | yes @ gateway (user, product) | Service already saw it as "one call"; gateway makes failure cheap. |
| **Retry** — POST / write | ❌ | ⚠️ | no (deliberately off on order route) | Only safe with idempotency key. |
| **Circuit breaker** across downstream service | ✅ | ⚠️ | yes @ gateway (user/product/order CBs) + service (order → payment) | Edge CB saves the fleet; service CB protects its own outbound calls. |
| **Timeout** per downstream hop | ✅ | ✅ | yes @ gateway (`httpclient.response-timeout`) + services | Both. Gateway caps edge; services cap DB / outbound HTTP. |
| **Idempotency key** (dedup POST/PATCH) | ✅ | ⚠️ | yes @ gateway (order route) + common-lib (`IdempotencyRecord`) | Gateway catches most; services also idempotent via DB. |
| **CORS** (origins, methods, headers) | ✅ | ❌ | yes @ gateway (`CorsConfig`) | One place. Services disable CORS. |
| **TLS termination** | ❌ | ❌ | no (nano runs plaintext; prod would use NGINX/LB) | Terminate upstream of the gateway. |
| **Request / response logging + redaction** | ✅ | ⚠️ | yes @ gateway (`BodyLoggingGlobalFilter`) | Gateway logs raw audit; services add domain context. |
| **Correlation-id + trace propagation** | ✅ | ✅ | yes @ gateway + all services (micrometer-tracing) | Gateway generates; services propagate. |
| **Metrics** — HTTP RED (rate/errors/duration) | ✅ | ✅ | yes @ gateway (`GatewayMetrics`) + services (Prometheus) | Both — edge view and internal view. |
| **Metrics** — business (orders/sec, revenue) | ❌ | ✅ | yes @ order-service (`SagaMetrics`) | Gateway doesn't know what a "paid order" is. |
| **Path rewrite** (`/api/v1/users/*` → `/api/v2/users/*`) | ✅ | ❌ | yes @ gateway (canary + openapi routes) | Routing concern — keep at router. |
| **Request validation** (JSON schema, field presence) | ⚠️ | ✅ | yes @ services (`@Valid` on DTOs) | Field rules belong with the DTO. |
| **API versioning** (route v1/v2 side-by-side) | ✅ | ✅ | yes @ gateway (route) + user-service (v1 + v2 controllers) | Gateway maps paths; service owns both impls. |
| **Canary / weighted routing** | ✅ | ❌ | yes @ gateway (95/5 user-service split) | Routing is the gateway's job. |
| **Response caching** (stale-while-CB-open) | ✅ | ❌ | yes @ gateway (`ResponseCacheGlobalFilter`) | Edge is the right layer. |
| **Business-logic caching** (hot catalog query) | ❌ | ✅ | yes @ product-service (`StampedeLock` + Redis) | Service's own cache — needs domain keys. |
| **Idempotency storage** (dedup window) | ✅ | ⚠️ | yes @ gateway (Redis) + common-lib (DB `idempotency_records`) | Gateway in Redis; services via DB uniqueness. |
| **Request transformation** (header add/drop) | ✅ | ❌ | yes @ gateway (`AddTenantHeader`, `RequestFingerprint`) | Edge filter — don't rewrite in services. |
| **Response transformation** (envelope wrap, HATEOAS) | ❌ | ✅ | no (plain JSON today) | Response shape is a service concern. |
| **Field-level data masking** (hide SSN for low-privilege callers) | ❌ | ✅ | no | Needs schema + ACL knowledge. |
| **Content negotiation** (JSON vs XML) | ❌ | ✅ | no (JSON-only) | Service owns its DTOs. |
| **Request body transformation** (XML → JSON) | ⚠️ | ❌ | no | Belongs in a BFF for complex cases. |
| **Authentication** — issuing tokens (login flow) | ❌ | ❌ | yes @ **auth-server** (`:8095`) | Separate module. Gateway only validates. |
| **User registration, password reset** | ❌ | ✅ | yes @ user-service, auth-server | Business flow. |
| **Business workflows** (saga orchestration) | ❌ | ✅ | yes @ order-service (`OrderSaga*`) | Orchestrator. Gateway is stateless. |
| **Event publishing** (Kafka produce) | ❌ | ✅ | yes @ user-service, order-service, payment-service, notification | Domain events — gateway doesn't produce. |
| **Event consumption** (Kafka consume) | ❌ | ✅ | yes @ product-query, order-query, payment-service, notification | Same reason. |
| **DB access** | ❌ | ✅ | yes @ all business services; gateway touches Redis only | No business tables in gateway. |
| **File uploads** | ⚠️ | ✅ | no (not implemented) | Prefer streaming directly to object storage from the service. |
| **WebSocket / SSE termination** | ✅ | ⚠️ | yes @ gateway (`lb:ws://` route) + user-service (`EchoWebSocketHandler`) | Gateway handles upgrade; service handles socket lifecycle. |
| **Custom HTML error pages** | ⚠️ | ❌ | no (returns JSON fallback) | API-first stack — JSON `/fallback/*` instead. |
| **GraphQL schema stitching** | ❌ | ❌ | yes @ **`bff/graphql-bff`** (separate module) | BFF concern, not gateway. |
| **Admin UI** (route editor, API-key manager) | ✅ | — | yes @ gateway (React @ `:5173` or Thymeleaf `/admin/ui`) | Operational concern tied to the gateway. |
| **SBOM / dependency scanning** | ❌ | ❌ | no (not wired) | Build-time / CI concern (Trivy, Snyk). |
| **At-rest encryption** (DB column, secret store) | ❌ | ✅ | no (plaintext secrets in dev; prod would use Vault/KMS) | Gateway doesn't encrypt data. |

### Quick count — what's actually built

- **At the gateway**: ~17 concerns from this table (auth, coarse authz, rate-limit, retry, CB, timeout, idempotency, CORS, body logging, correlation-id, HTTP metrics, path rewrite, versioning route, canary, response cache, transform, admin UI, WS upgrade)
- **In the services**: fine authz is not needed yet; business metrics / caching / validation / workflows / events / DB — all handled by the right service
- **In separate modules**: token issuing → auth-server; GraphQL → `bff/graphql-bff`
- **Not built (gaps)**: TLS termination, fine authz, response envelope / HATEOAS, field masking, content negotiation, file uploads, HTML error pages, SBOM, at-rest encryption

### The 3 anti-patterns people fall into

**1. "The gateway can do everything" — the god-gateway trap**

Pushing field-level masking, response envelope wrapping, body transformations, or business validation into the gateway. Result: a 50k-line application.yml, unreasonable release coupling (every service change becomes a gateway change), and a slow critical path.

**2. "The service should re-do everything the gateway does" — the paranoid-service trap**

Services re-validating JWTs on every endpoint, re-enforcing rate limits, writing their own CORS. Result: duplicated logic, inconsistent behavior (service A allows origin X, service B doesn't), and the gateway becomes decorative.

**3. "I'll add this one tiny thing to the gateway" — the slow-creep trap**

A one-off header rewrite for one route. Six months later it's 40 one-off hacks and the next engineer can't tell which rule applies to which path. Fix: keep the gateway's config declarative and symmetric; one-off business logic belongs in services.

### Short answer when you're not sure

> *"If I remove this feature from the gateway, will EVERY service need to re-add it to work correctly?"*
> **Yes** → gateway is the right place.
> **No** → the service(s) that need it should own it.

---

## 6. Config file tour

The gateway's `infra/api-gateway/src/main/resources/application.yml` is ~657 lines. Here's the structural map so you know where to look:

```
info:                                    build info surfaced via /actuator/info
spring:                        line 14
  cloud.gateway:                         ← routes + global HTTP client config
    discovery.locator.enabled: false     ← we own explicit routes
    httpclient:                          ← connect + response timeout
    default-filters: [TokenRelay]        ← applied to every route
    routes: [...]                        ← THE route table (§4)
  security.oauth2.resourceserver.jwt:    ← JWT validation (issuer-uri)
server:                        line 254  server.port=8080
springdoc:                     line 267  aggregated Swagger
eureka:                        line 285  Eureka client config
management:                    line 292  actuator exposure, Prometheus, tracing
logging:                       line 342
gateway:                       line 351  ← OUR custom config (features §5)
  ratelimit:
  circuitbreaker:
  retry:
  timeout:
  idempotency:
  dynamic-routes:
  canary:
  cors:
  admin:
  response-cache:
  body-logging:
  apikey:
---                            line 647  demo profile overrides
```

### The most important config keys (in order of blast radius if broken)

| Key | What it controls | Break-glass value |
|---|---|---|
| `server.port` | Gateway's own port | `8080` — change and NOTHING works |
| `spring.cloud.gateway.routes[].uri: lb://<svc>` | Discovery target per route | `lb://` requires Eureka up |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | JWT validation anchor | Must match auth-server's actual `iss` claim exactly |
| `spring.cloud.gateway.httpclient.response-timeout` | Per-request budget to downstream | `5s` — bumping affects every route |
| `gateway.circuitbreaker.enabled` | Master switch for CB wrapping | Turn off during performance baseline tests |
| `gateway.ratelimit.enabled` | Master switch for rate limiter | Turn off during load tests |
| `gateway.cors.allowed-origins` | Which browser origins may call us | Prod: must NOT be `*` with credentials |
| `gateway.admin.required-authorities` | Who can hit `/admin/**` | Default accepts `SCOPE_admin` OR `ROLE_ADMIN` |

---

## 7. How to USE it

### 7.1 Call a business endpoint

```bash
# 1. Get a token (M2M for Postman / tests)
TOKEN=$(curl -s -u m2m-client:m2m-secret \
  -d 'grant_type=client_credentials&scope=read write' \
  http://localhost:8095/oauth2/token | jq -r .access_token)

# 2. Call the gateway
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/users
```

### 7.2 Force the v2 canary explicitly

```bash
curl -H "Authorization: Bearer $TOKEN" \
     -H 'X-Canary: force-v2' \
     http://localhost:8080/api/v1/users
# Response includes: X-Canary-Route: v2
```

### 7.3 Use an idempotency key on a write

```bash
curl -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"productId":1,"quantity":1,"provider":"mock"}' \
  http://localhost:8080/api/v1/checkout
# Second call with SAME key + body → replays original response (header: X-Idempotent-Replay)
# Same key + DIFFERENT body → 422
```

### 7.4 Hit an admin endpoint (JWT with admin scope OR Basic)

```bash
# via JWT (needs scope=admin in the token's `scope` claim)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/admin/routes

# via HTTP Basic — only works when gateway.admin.ui=THYMELEAF
curl -u admin:admin123 http://localhost:8080/admin/routes
```

### 7.5 View what the gateway currently thinks its routes are

```bash
curl -s http://localhost:8080/actuator/gateway/routes | jq '.[] | {id, uri, predicate}'
```

### 7.6 Add a new route AT RUNTIME (dynamic routes)

```bash
curl -X POST -u admin:admin123 \
  -H 'Content-Type: application/json' \
  http://localhost:8080/admin/routes \
  -d '{
    "id": "my-new-route",
    "uri": "lb://user-service",
    "predicates": [{"name":"Path","args":{"_genkey_0":"/foo/**"}}],
    "filters": []
  }'
# Persisted to DB, broadcast via Redis pub/sub — other gateway replicas pick it up <1s.
```

---

## 8. How to CUSTOMISE it

### 8.1 Add a new route for a new service

Edit `infra/api-gateway/src/main/resources/application.yml` under `spring.cloud.gateway.routes`:

```yaml
- id: inventory-service
  uri: lb://inventory-service          # must match Eureka's registered name
  predicates:
    - Path=/api/v1/inventory/**
  filters:
    - Bulkhead=inventoryCB
    - name: CircuitBreaker
      args:
        name: inventoryCB
        fallbackUri: forward:/fallback/inventory
    - name: RequestRateLimiter
      args:
        key-resolver: "#{@ipKeyResolver}"
        redis-rate-limiter.replenishRate: 10
        redis-rate-limiter.burstCapacity: 20
```

Also add a matching `circuitbreaker.instances.inventoryCB` block if you want non-default behavior. Restart the gateway for yml changes — or use dynamic routes (§7.6) for runtime add.

### 8.2 Change a rate limit for a specific route

```yaml
gateway:
  ratelimit:
    routes:
      product-service:
        enabled: true
        limit:
          replenish-rate: 50     # was 5
          burst-capacity: 100    # was 10
```

If config-server is running, you can tweak these in `config-repo/api-gateway.yml` and call `POST /actuator/refresh` to apply without restart (for `@RefreshScope`-aware beans).

### 8.3 Toggle a feature off

Every custom feature has an `enabled:` flag:

```yaml
gateway:
  idempotency.enabled: false       # disables the idempotency filter chain
  response-cache.enabled: false    # disables the stale-while-CB cache
  body-logging.enabled: false      # stops req/resp body logs
```

### 8.4 Write a new filter (custom)

Create a `@Component` that implements `GlobalFilter` (applied to every request) OR a `GatewayFilterFactory` (opt-in per route). See [custom-filters.md](../api-gateway/custom-filters.md) for the full template.

### 8.5 Change CORS allowed origins

```yaml
gateway:
  cors:
    allowed-origins:
      - http://localhost:5173
      - https://my-prod-ui.example.com     # add prod origin
```

### 8.6 Use Postgres instead of H2 for dynamic routes

Switch profile: `-Dspring.profiles.active=postgres`. The `application-postgres.yml` sibling overrides the DataSource.

### 8.7 Serve the admin UI from Thymeleaf (bundled, no React)

```yaml
gateway:
  admin:
    ui: THYMELEAF        # was REACT
```

Then `http://localhost:8080/admin/ui` serves server-rendered pages, with HTTP Basic `admin:admin123` for login.

---

## 9. Observing it live

```bash
# Live route table (what the gateway thinks is active, incl. dynamic routes)
curl -s http://localhost:8080/actuator/gateway/routes | jq

# Gateway's own health (incl. redis, eureka)
curl -s http://localhost:8080/actuator/health | jq

# Which build / commit is deployed
curl -s http://localhost:8080/actuator/info | jq

# Prometheus metrics (gateway timings, CB state, rate-limit rejections)
curl -s http://localhost:8080/actuator/prometheus | grep -i "spring_cloud_gateway"
curl -s http://localhost:8080/actuator/prometheus | grep -i "resilience4j"

# Circuit-breaker state transitions
curl -s http://localhost:8080/actuator/circuitbreakerevents | jq

# Current CB state (open / closed / half-open) per instance
curl -s http://localhost:8080/actuator/circuitbreakers | jq

# What's in the request-fingerprint / idempotency cache (Redis)
make redis-keys PATTERN='idem:*'
make redis-keys PATTERN='fp:*'
```

### Spot a problem fast

| Symptom | Where to look |
|---|---|
| 401 everywhere | `/actuator/env/spring.security.oauth2.resourceserver.jwt.issuer-uri` — matches auth-server? |
| 404 for a path that should route | `/actuator/gateway/routes` — is the id there? |
| 503 at the gateway | `/actuator/circuitbreakers` — is the CB OPEN? |
| 429 Too Many Requests | `/actuator/prometheus` for `redis_rate_limiter_rejected_requests_total` |
| Replay unexpected | Idempotency key reused — check response header `X-Idempotent-Replay: true` |

---

## 10. Common failure modes

| Symptom | Root cause | Fix |
|---|---|---|
| Gateway starts as MVC (not Netty) | Something pulled in `spring-boot-starter-web` | Exclude it from Eureka client (already done in pom.xml) |
| Route "defined" but 404 | Path predicate typo (missing `/**`, wrong case) | `curl /actuator/gateway/routes` to see what's live |
| 401 on every call | `issuer-uri` doesn't match auth-server's actual `iss` claim | Point at exact auth-server URL |
| 503 with `Warning: 110` header | Downstream is dead, CB opened, cached response served | Downstream fix will auto-close CB after `wait-duration-in-open-state` |
| 503 with no Warning header | CB open AND no cached response (writes, first-request GETs) | Same as above; add `Warning` to CORS `exposed-headers` to see it |
| Weight predicate always picks the same route | Weight group has only one route defined | Both routes must share same `Weight=<group>,<n>` tag |
| Dynamic routes don't propagate | Redis pub/sub down | Falls back to `refresh-interval: PT5M` scan |
| Idempotency replay returns stale body | TTL too long or wrong key reuse pattern | Lower `gateway.idempotency.key-ttl` or document stricter key contract |
| WebSocket upgrade fails | Route uses `lb://` instead of `lb:ws://` | Switch URI scheme |

---

## 11. Interview talking points

- **Why reactive**: gateway is I/O-bound — it holds many connections open while waiting for downstream. Netty's event loop handles 10k+ concurrent sockets with a handful of threads, where Tomcat would need 10k threads.
- **Predicates vs filters**: predicates decide which route matches (`Path=`, `Weight=`, `Header=`); filters modify request/response or add behavior (`RateLimit`, `Retry`, `CircuitBreaker`). Predicates always run before filters.
- **Default filters** apply to every route — `TokenRelay` is a classic.
- **Order of filters**: Idempotency MUST run after RequestFingerprint (needs the hash); RateLimit should run before CB (don't waste CB budget on throttled requests); body logging goes near the end so it sees final body.
- **CB opens on failure RATE, not count** — `failure-rate-threshold: 50` + `sliding-window-size: 10` + `minimum-number-of-calls: 5` means "once I have ≥5 calls, if >50% failed, open".
- **Bulkhead** is complementary to CB: CB stops calling a sick service; bulkhead stops a slow service from monopolizing the gateway's own thread pool.
- **Stale-while-CB-open** is a resilience pattern — serve the last known good response (with `Warning: 110`) instead of a hard 503, giving the client the chance to proceed.
- **Idempotency-Key** is Stripe's pattern — same key + same body → replay original response; same key + different body → 422. Prevents duplicate charges on client retries.
- **Dynamic routes via Redis pub/sub**: every replica of the gateway listens; a mutation on one replica propagates to the others sub-second via `RefreshRoutesEvent`. Scheduled DB poll is the eventual-consistency floor if pub/sub is down.
- **Gateway vs BFF**: gateway is generic (same shape for every client); BFF is tailored per client (web vs mobile), often collapses N backend calls into one. We have a `graphql-bff` module separate from the gateway for this.

---

## 12. How to tail gateway logs (and the compose-profile gotcha)

The first thing you'll want when debugging anything is **real-time logs**. Here's the one quirk that bites every first-time attempt.

### The gotcha

```bash
$ docker compose logs -f api-gateway
no such service: redis
```

The gateway is running. Redis is running. But `docker compose logs api-gateway` fails with `no such service: redis` — misleading, because the error isn't about api-gateway.

**Why it happens**:

- api-gateway's `depends_on` in `docker-compose.yml` includes `redis`
- redis has `profiles: [cache]` — meaning it's only "visible" to compose when the `cache` profile is active
- When you ran `make up-nano`, the Makefile set `COMPOSE_PROFILES=cache` internally → redis was visible, everything started
- Your current terminal has NO `COMPOSE_PROFILES` env var → redis is invisible → compose refuses to resolve api-gateway's dependency graph

The containers are fine. Compose is just being strict about profile scoping.

Confirm redis is up (it is):

```bash
docker ps | grep redis
# redis:7-alpine ... Up ... 0.0.0.0:6379->6379/tcp
```

Confirm the Makefile already handles this:

```bash
grep -n "COMPOSE_PROFILES" Makefile | head
# up-nano:  COMPOSE_PROFILES=cache docker compose up -d ...
```

### Two ways to fix

**Option 1 — Bypass compose, go straight to Docker** (recommended for ad-hoc tailing)

```bash
docker logs -f api-gateway
```

Works without profile setup. The container was already started; Docker doesn't care about compose profiles.

**Option 2 — Export the profile for this shell** (recommended when running multiple compose commands)

```bash
export COMPOSE_PROFILES=cache
docker compose logs -f api-gateway
```

Add to `~/.zshrc` to make it permanent:

```bash
echo 'export COMPOSE_PROFILES=cache' >> ~/.zshrc
```

### Which to use when

| Situation | Use |
|---|---|
| Just want to see gateway logs | `docker logs -f api-gateway` |
| Running multiple compose commands (`restart`, `exec`, `logs`) | `export COMPOSE_PROFILES=cache` once, then `docker compose ...` |
| Running on another dev's machine | Export the profile — more portable |

### Useful grep patterns

Default log level is **INFO** — most filters run silently. To see filter chatter you'd bump `logging.level.org.springframework.cloud.gateway=DEBUG` in application.yml. For INFO-level, these are the signals you'll actually see:

```bash
# Everything — raw stream
docker logs -f api-gateway

# Startup only (routes registered, port bound)
docker logs api-gateway 2>&1 | grep -iE "started|route|listening"

# Audit entries — one line per request (body-logging filter)
docker logs -f api-gateway | grep -i audit

# Errors + warnings
docker logs -f api-gateway | grep -iE "error|warn|exception"

# Circuit breaker transitions (CLOSED → OPEN → HALF_OPEN)
docker logs -f api-gateway | grep -iE "circuit|CircuitBreaker"

# A specific correlation-id across the request
docker logs -f api-gateway | grep "<your-X-Correlation-Id>"
```

### Watch a request move through

Two terminals. Terminal A tails, terminal B fires.

**Terminal A**:

```bash
docker logs -f api-gateway
```

**Terminal B**:

```bash
TOKEN=$(curl -s -u m2m-client:m2m-secret \
  -d 'grant_type=client_credentials&scope=read write' \
  http://localhost:8095/oauth2/token | jq -r .access_token)

curl -v -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/users
```

In terminal A you should see audit lines flow past. In terminal B, note the response headers:

- `X-Correlation-Id` — correlation-id filter
- `X-RateLimit-Remaining` — rate-limit filter
- `X-Cache` — response-cache filter
- `X-Idempotent-Replay` (sometimes) — idempotency filter

Each header is a feature leaving a fingerprint. Correlate it with the audit line in terminal A using the X-Correlation-Id.

### If `docker logs` returns nothing

- Container never produced output since start — try `docker logs api-gateway` (no `-f`) to see historical logs
- Container is crashlooping — `docker ps -a | grep gateway` to see if status is `Exited`
- Wrong name — `docker ps --format 'table {{.Names}}' | grep gateway` to find the actual container name

### Related: live metrics, not just logs

Logs are reactive — you see what happened. For live state, actuator endpoints are better:

```bash
# What routes does the gateway think are live?
curl -s http://localhost:8080/actuator/gateway/routes | jq

# Circuit breaker state per instance
curl -s http://localhost:8080/actuator/circuitbreakers | jq

# Rate-limit rejection counter (Prometheus text format)
curl -s http://localhost:8080/actuator/prometheus | grep -i rate_limiter

# Current filter chain for a specific route
curl -s http://localhost:8080/actuator/gateway/routes/user-service-v1 | jq
```

Pair tailing logs with hitting these on-demand — fastest way to understand what the gateway is doing right now.

---

## See also

- Setup → first-time boot: [`setup/00-first-time-setup.md`](../setup/00-first-time-setup.md)
- Request flow walk-through (one call end-to-end): [`flows/debug_level1_gateway_discovery.md`](../flows/debug_level1_gateway_discovery.md)
- Service communication (east/west, no gateway): [`design/communication-of-service.md`](../design/communication-of-service.md)
- Postman collection: [`postman/nano-debug-flow.README.md`](../postman/nano-debug-flow.README.md)
