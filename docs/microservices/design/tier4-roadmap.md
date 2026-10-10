# Tier 4 Roadmap — Advanced Patterns (Big Differentiators)

Tier 1 made it observable and stable. Tier 2 made it correct under event
volume. Tier 3 (not written yet — security, infra, deploy) makes it shippable.
**Tier 4** is where you earn senior-level signal in interviews: patterns
that aren't needed on day one but mark the difference between "I built a
microservice" and "I've run one."

Audience: you, learning for interviews. Each section follows the same
shape as prior tiers: **what it is**, **why interviewers ask**, **current
state**, **implementation steps**, **verify**, **talking points**.

- [1. Saga compensation admin UI](#1-saga-compensation-admin-ui)
- [2. Caching strategies with Redis](#2-caching-strategies-with-redis)
- [3. Rate limiting at the gateway](#3-rate-limiting-at-the-gateway)
- [4. Feature flags](#4-feature-flags)
- [5. Multi-tenancy](#5-multi-tenancy)
- [6. GraphQL BFF](#6-graphql-bff)
- [7. gRPC between internal services](#7-grpc-between-internal-services)
- [8. WebSocket / SSE push from Notification](#8-websocket--sse-push)

---

## Target architecture after Tier 4

```
                         ┌─────────────────────────────────────────┐
                         │  Admin / Operator Console (Thymeleaf)   │
                         │                                         │
                         │  /admin/sagas    in-flight state view   │
                         │  /admin/dlq      replay (Tier 2)        │
                         │  /admin/flags    toggle feature flags   │
                         └──────────────────┬──────────────────────┘
                                            │ reads/writes shared state
                                            ▼
                                    ┌───────────────┐
                                    │  Redis :6379  │
                                    │               │
   ┌──────┐  ┌─────────────┐        │ - cache L2    │   ┌──────────────┐
   │Client│─▶│  API GW     │─token──│ - rate buckets│   │ Flag Store   │
   └──────┘  │             │ bucket │ - sagas state │   │ (DB table OR │
             │ RateLimiter │        │   (optional)  │   │  Unleash)    │
             │ /flags gate │        └───────┬───────┘   └──────────────┘
             │ tenant      │                │
             │   header    │                │ pub/sub (flag updates)
             │ injection   │                │
             └──┬───────┬──┘                ▼
                │       │               ┌──────────────────────┐
                │       │               │  @FeatureEnabled     │
                │       │               │    aspect in common  │
                │       │               │    checks flag value │
                │       │               └──────────────────────┘
                │       │
      ┌─────────┼───────┼──────────┬──────────────┬──────────────┐
      ▼         ▼       ▼          ▼              ▼              ▼
  ┌───────┐ ┌─────────┐ ┌─────────┐  ┌───────┐   ┌───────┐   ┌──────────────┐
  │ Order │ │ Payment │ │ Product │  │ User  │   │ Notif │──▶│ Browser (SSE)│
  │       │ │         │ │         │  │       │   │       │   └──────────────┘
  │ Saga  │ │ gRPC    │ │ cache   │  │       │   │ WS/SSE │
  │ view  │ │ internal│ │ aside   │  │       │   │ push   │
  └───────┘ └─────────┘ └─────────┘  └───────┘   └───────┘
         ▲                  ▲
         │                  │
         │                  │
         │                 Redis cache for GET /products/{id}
         │
         │
   ┌─────┴─────────┐
   │ GraphQL BFF   │  one endpoint, composes Order+Product+User+Notif
   │ :8090         │
   └───────────────┘
         ▲
         │ GraphQL over HTTP
         │
      Mobile / Web client

      Tenant isolation: every service reads TenantContext from X-Tenant-Id
      header; JPA interceptor adds WHERE tenant_id = … to every query.
```

---

## 1. Saga compensation admin UI

### What it is
A screen that shows:
- Every saga currently in flight.
- Its correlation id, started-at, current state, time-in-current-state.
- The sequence of commands it has emitted and replies received.
- A "force compensate" button for stuck sagas.
- A "replay from step N" button for recoverable failures.

Think "git blame for your saga."

### Why interviewers ask
"Payment confirmed but inventory didn't deduct — what do you do?" The
textbook answer is "orchestrator fires a compensation." The real-world
answer is "someone looks at a screen, sees where it stopped, decides."
Senior engineers build that screen.

### Current state in this repo
- ✅ Saga orchestrator exists (`OrderSagaOrchestrator`).
- ❌ No persistence of saga state. The orchestrator is stateless by design;
  nothing to inspect.
- ❌ No admin screen.

### Implementation steps

**1.1 Persist saga state**
```sql
CREATE TABLE saga_instances (
  saga_id         VARCHAR(64)  PRIMARY KEY,
  type            VARCHAR(64)  NOT NULL,            -- 'ORDER_CHECKOUT'
  state           VARCHAR(32)  NOT NULL,            -- 'STARTED','PAYMENT_PENDING','PAID','COMPENSATING','COMPLETED','FAILED'
  correlation_id  VARCHAR(64),
  started_at      TIMESTAMPTZ,
  updated_at      TIMESTAMPTZ,
  context         JSONB NOT NULL                     -- orderId, amount, lastReply, nextStep…
);

CREATE TABLE saga_steps (
  id          BIGSERIAL PRIMARY KEY,
  saga_id     VARCHAR(64) NOT NULL,
  step_name   VARCHAR(64) NOT NULL,                  -- 'reserve-payment'
  direction   VARCHAR(8)  NOT NULL,                  -- 'FORWARD' | 'COMPENSATE'
  status      VARCHAR(16) NOT NULL,                  -- 'EMITTED','SUCCEEDED','FAILED','TIMEOUT'
  attempt     INT NOT NULL DEFAULT 1,
  command     TEXT,                                  -- emitted command payload
  reply       TEXT,                                  -- reply payload (if any)
  occurred_at TIMESTAMPTZ
);
```

Update the orchestrator to write a row per command emitted and per reply
received. Keep it in the same tx as the outbox event write.

**1.2 Admin controller + Thymeleaf view**
```java
@Controller
@RequestMapping("/admin/sagas")
public class SagaAdminController {
  @GetMapping
  String list(@RequestParam(required=false) String state, Model m) { … }

  @GetMapping("/{id}")
  String detail(@PathVariable String id, Model m) { … }

  @PostMapping("/{id}/compensate")
  String forceCompensate(@PathVariable String id) {
    orchestrator.triggerCompensation(id);
    return "redirect:/admin/sagas/" + id;
  }
}
```

**1.3 Alert on stuck sagas**
Scheduled job: `SELECT * FROM saga_instances WHERE updated_at < NOW() - INTERVAL '5 min' AND state NOT IN ('COMPLETED','FAILED')`. Fire a Micrometer gauge:
```
sagas_stuck_count{type="ORDER_CHECKOUT"}
```
Grafana dashboard + alert.

### Verify
- Start an order saga.
- `/admin/sagas` lists it in `PAYMENT_PENDING`.
- Kill payment-service; wait 1 min. State transitions to `TIMEOUT`.
- Click "Force compensate"; refund command fires; state → `COMPENSATED`.

### Interview talking points
- **Sagas without observability are landmines.** You can't afford
  "it's stuck somewhere, we'll find out tomorrow."
- **Idempotency of compensations.** Multiple compensate clicks must not
  refund twice. Compensation commands carry a dedup id too.
- **Where state lives** — same DB as the business write side (strong read-your-writes), or a dedicated saga DB (clean separation, more complex).
- **Stateful vs stateless orchestrators** — pure stateless means every reply
  must contain enough context to decide the next step. Stateful means the
  orchestrator loads state per saga, which needs a saga store.
- **State machine as code.** Use an actual state machine library (Spring
  Statemachine, or a hand-rolled enum-driven FSM) so transitions are explicit
  and auditable.

---

## 2. Caching strategies with Redis

### What it is
In-memory key-value store sitting between a service and its database.
Five flavors interviewers ask about:

| Pattern | How | When |
|---|---|---|
| **Cache-aside** | App reads cache; miss → DB → write back to cache | Default. Most workloads. |
| **Read-through** | Cache lib does the DB fetch on miss | Hides complexity; locks you into one DB per cache. |
| **Write-through** | App writes cache + DB synchronously | Reads always fresh. Writes 2× latency. |
| **Write-behind** | App writes cache; cache flushes to DB async | Fast writes. Risky on crash. |
| **Refresh-ahead** | Cache proactively refetches before TTL | Smooths spikes on hot keys. |

### Why interviewers ask
"Your product detail page gets 10k RPS; the DB can't handle it." Caching is
step one. "A hot product gets 100k RPS all at once" → cache stampede. "What
if cached data goes stale?" → TTL + eventing. All classic interview rounds.

### Current state in this repo
- ⚠️ `product-service` has JPA to MySQL. No cache layer. Every GET hits DB.
- ✅ Redis dependency available (used in gateway for rate limiting).

### Implementation steps

**2.1 Add Spring Cache + Redis to product-service**
```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
```
```yaml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 10m
  data:
    redis:
      host: ${REDIS_HOST:redis}
      port: 6379
```

**2.2 Annotate the read path**
```java
@Service
public class ProductService {
  @Cacheable(value = "products", key = "#id")
  public Product get(Long id) { return repo.findById(id).orElseThrow(); }

  @CacheEvict(value = "products", key = "#p.id")
  public Product update(Product p) { return repo.save(p); }

  @CacheEvict(value = "products", allEntries = true)
  public void bulkReload() { … }
}
```

**2.3 Protect against cache stampede**
Scenario: TTL expires on a hot key; 1000 concurrent requests all miss and
hammer the DB. Fix with Redis-level distributed lock:
```
GET product:123 → miss
SET lock:product:123 "pid" NX EX 2    ← only one caller wins
   if won:
     fetch from DB; populate cache; DEL lock
   if lost:
     sleep 50 ms; GOTO top (now cache populated)
```
Alternative: **probabilistic early expiry** — refresh with p = (now - fetchedAt) / ttl.

**2.4 Negative caching**
Cache "not found" too, with a shorter TTL (30 s). Stops 10k requests for a
non-existent product from drilling through to DB.

**2.5 Cache-stampede metric panel**
```
cache_misses_total{cache="products"} / cache_requests_total{cache="products"}
```
Alert when > 10%.

### Verify
- Hit `GET /products/1` twice; second call doesn't touch MySQL (log check).
- Delete the key; concurrent 100 requests → only one DB query (lock test).
- `GET /products/9999` → first call cached; second returns 404 without DB.

### Interview talking points
- **Cache invalidation is the second hard problem.** Options:
  - TTL only (simple, stale-tolerant).
  - Event-driven invalidation (publish `product.updated`, consumers evict).
  - Write-through (fresh at cost of write latency).
- **TTL ≠ consistency.** Pick based on how stale the business can tolerate.
- **Cache key design** — include version, locale, user segment if those
  change the response. `product:v2:en:123`.
- **What to NOT cache** — personalised views (cardinality explosion), fresh
  pricing / inventory counts (correctness over speed).
- **The 3 cache problems** — stampede (concurrent miss), penetration
  (requests for nonexistent keys), avalanche (TTLs all expire together).
  All have standard mitigations.

---

## 3. Rate limiting at the gateway

### What it is
A guard that limits requests per client per time window. Classic variants:

| Algo | Behaviour |
|---|---|
| **Fixed window** | Count in a 1-min window; reset at the top. Burst at boundary. |
| **Sliding window** | Weighted mix of this + last window; smoother. |
| **Token bucket** | Bucket holds N tokens, refills at rate R. Burst up to N; sustained ≤ R. |
| **Leaky bucket** | Fixed drain rate; excess queued or dropped. |

Spring Cloud Gateway ships `RequestRateLimiter` backed by Redis (token-bucket).

### Why interviewers ask
"How do you stop one tenant from DOS-ing another?" "How do you enforce API
plan limits (free: 100 rpm, pro: 10k rpm)?" "What happens when the rate
limit backend (Redis) is down?"

### Current state in this repo
- ✅ `RequestRateLimiter` filter is on multiple routes in `api-gateway/application.yml`.
- ✅ Reactive Redis dep already in the gateway pom.
- ⚠️ Keyed on IP by default — needs per-API-key / per-tenant resolver.

### Implementation steps

**3.1 Per-API-key resolver bean**
```java
@Bean
public KeyResolver apiKeyResolver() {
  return exchange -> {
    String key = exchange.getRequest().getHeaders().getFirst("X-Api-Key");
    if (key == null) key = exchange.getRequest().getRemoteAddress().getHostString();
    return Mono.just(key);
  };
}
```
Reference it in route config:
```yaml
filters:
  - name: RequestRateLimiter
    args:
      redis-rate-limiter.replenishRate: 10        # steady state tokens/sec
      redis-rate-limiter.burstCapacity: 20        # peak burst
      redis-rate-limiter.requestedTokens: 1
      key-resolver: "#{@apiKeyResolver}"
```

**3.2 Plan-aware limits**
Lookup plan in Redis / DB: `GET plan:<apiKey>` → `{"rpm": 1000, "burst": 2000}`.
Resolver fetches plan and returns a composite key `plan:1000:<apiKey>` so
different buckets apply.

**3.3 Response headers**
Return `X-RateLimit-Limit`, `-Remaining`, `-Reset` so clients can self-regulate.
Spring Cloud Gateway sets these out of the box when enabled.

**3.4 Graceful degradation**
If Redis is down, `RequestRateLimiter` fails open (allows request) by default
— flip to fail-closed only if your SLA demands it. Metrics panel: `spring_cloud_gateway_requests_rate_limit_remaining` + Redis up/down.

**3.5 429 payload shape**
Return RFC 7807 problem+json:
```json
HTTP/1.1 429 Too Many Requests
Retry-After: 15
Content-Type: application/problem+json

{"type": "https://errors.example/rate-limit",
 "title": "Rate limit exceeded",
 "detail": "100 req/min allowed; retry in 15 s"}
```

### Verify
- Hammer `/api/v1/orders` with the same `X-Api-Key` at > 10 rps.
- First 20 pass (burst); then 429s with `Retry-After`.
- Switch key → fresh bucket.
- Grafana panel: `spring_cloud_gateway_requests_seconds_count{outcome="SUCCESS"}` plateaus at the configured rate.

### Interview talking points
- **Why Redis, not in-memory** — the gateway scales horizontally; counters must be shared.
- **Fail-open vs fail-closed** when the backing store is down. Default is fail-open — explain the trade-off.
- **Token bucket beats fixed window** for burst-tolerant APIs.
- **Where to put rate limiting** — at the edge (gateway) for request-count limits; at the service (Resilience4j RateLimiter) for concurrency limits per downstream.
- **Nested limits** — global, per-tenant, per-user, per-endpoint. Pick the tightest.
- **Observability** — rate-limit hits need to be first-class metrics, not just 429 logs.

---

## 4. Feature flags

### What it is
Runtime switches that toggle behaviour without a redeploy. Four kinds:

| Kind | Example |
|---|---|
| **Release toggles** | New payment provider dark-launched, flag-off |
| **Experiment toggles** | A/B test — 50 % of users see new UI |
| **Ops toggles** | Kill-switch for a flaky integration |
| **Permission toggles** | Beta feature for enterprise plan only |

### Why interviewers ask
"How do you deploy without releasing?" "How do you roll out to 1 % of users?"
"How do you kill a bad feature without a redeploy?" All answers: flags.

### Current state in this repo
- ✅ Stub exists: `notification.core.feature.FeatureFlagService` interface.
- ❌ No real impl, no store, no admin.

### Implementation steps

**4.1 Flag store schema**
```sql
CREATE TABLE feature_flags (
  flag_key    VARCHAR(128) PRIMARY KEY,
  description TEXT,
  enabled     BOOLEAN NOT NULL DEFAULT FALSE,
  rules       JSONB,                                 -- rollout rules: percentage, tenant whitelist, user whitelist
  updated_at  TIMESTAMPTZ,
  updated_by  VARCHAR(128)
);
```

**4.2 Evaluation**
```java
public boolean isEnabled(String key, EvalContext ctx) {
  Flag f = cache.get(key);                            // Redis-backed, 5 s TTL
  if (f == null || !f.enabled) return false;
  if (f.rules.tenantWhitelist.contains(ctx.tenantId())) return true;
  if (ctx.userId() != null && f.rules.userWhitelist.contains(ctx.userId())) return true;
  if (f.rules.percentage > 0) {
    int bucket = Hashing.murmur3_32().hashString(ctx.userId() + key).asInt() % 100;
    return bucket < f.rules.percentage;              // sticky per-user
  }
  return true;
}
```
Sticky hashing matters: user X either always sees the feature or never, so
UI doesn't flicker.

**4.3 Admin surface**
```
GET  /admin/flags
POST /admin/flags/{key}       body: {enabled, rules}
```
Audit every change. `updated_at` + `updated_by` lets you trace outages.

**4.4 Annotation sugar**
```java
@FeatureEnabled("new-payment-provider")
public PaymentResult chargeViaNewProvider(Order o) { … }

@FeatureEnabled(value = "nightly-batch", disabled = PaymentProcessor.LegacyBatch.class)
```
Aspect intercepts; falls through to the `disabled` default.

**4.5 Hot config**
Publish `flag.updated` on Kafka when any flag changes; local cache evicts
on consume. Changes propagate in < 1 s cluster-wide, no restart.

### Verify
- Create flag `new-payment-provider` disabled.
- POST an order → old provider path.
- Enable flag, same user retries → new provider path (within 1 s).
- Change percentage to 10 — same user sticks (hash-based), others roll separately.

### Interview talking points
- **Flags are code debt** — every flag is an if/else forever unless cleaned
  up. Have a sunset date.
- **Dark launch vs feature release** — flag on in prod with code path exercised
  on real traffic BEFORE anyone sees the UI change. Lets you shake out
  perf/scale issues safely.
- **Sticky evaluation** — hash by stable id (userId, tenantId), not random.
- **Why not just git branches** — long-lived branches are the opposite of
  continuous integration; flags are branches in config.
- **Build vs buy** — Unleash / LaunchDarkly / Flagsmith are mature. Build only
  if you need tight integration with your perms system.
- **Observability** — emit a metric per flag eval:
  `feature_flag_evaluations_total{flag,result}`. Lets you see what actually
  shipped.

---

## 5. Multi-tenancy

### What it is
Serving multiple customers (tenants) from the same deployed service while
keeping their data separated. Three isolation levels:

```
Isolation        Storage              Blast radius of bug         Cost
─────────        ───────              ───────────────────         ────
Row-per-tenant   tenant_id column     One query bug exposes all   Lowest
Schema-per-tenant one schema/tenant   Harder to leak cross-tenant Medium
DB-per-tenant    separate DBs         Full isolation              Highest
```

### Why interviewers ask
"How do you serve 1000 customers without 1000 deployments?" "Prevent tenant
A from reading tenant B's data." "Noisy tenant X eats all CPU — how do you
cap them?"

### Current state in this repo
- ✅ `AddTenantHeaderGatewayFilterFactory` already extracts `tenant_id`
  claim from JWT and injects `X-Tenant-Id` header downstream.
- ❌ No `TenantContext`, no JPA interceptor, no per-tenant data isolation.
- ❌ No per-tenant rate limits.

### Implementation steps

**5.1 TenantContext in common-lib**
```java
public class TenantContext {
  private static final ThreadLocal<String> CTX = new ThreadLocal<>();
  public static void set(String tenantId) { CTX.set(tenantId); }
  public static String get()               { return CTX.get(); }
  public static void clear()               { CTX.remove(); }
}
```
Servlet filter reads `X-Tenant-Id`, sets ctx, clears in finally. For reactive:
`Context` propagation via `ContextView`.

**5.2 JPA interceptor (row-per-tenant)**
Add `tenant_id` column + non-null constraint to every tenant-scoped table.
Hibernate `@Filter` + `@FilterDef`:
```java
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Entity
public class Order { @Column(nullable=false) private String tenantId; … }
```
Enable per session:
```java
@Component @RequiredArgsConstructor
public class TenantFilterInterceptor implements HibernatePropertiesCustomizer, SessionFactoryObserver {
  @PersistenceContext EntityManager em;

  @EventListener
  public void onSessionOpened(SessionOpenedEvent e) {
    e.getSession().enableFilter("tenantFilter")
        .setParameter("tenantId", TenantContext.get());
  }
}
```
Every `SELECT` now gets `WHERE tenant_id = :tenantId` auto-added — forget
the filter and queries return empty, not someone else's rows.

**5.3 Insert-time stamp**
`@PrePersist` on the entity sets `tenantId = TenantContext.get()` so devs
can't forget.

**5.4 Per-tenant rate limits**
`KeyResolver` returns `tenant:<id>` instead of IP. Combine with plan:
`tenant:acme-corp:pro`.

**5.5 Per-tenant Grafana dashboards**
Tag every metric with `tenant`:
```java
Counter.builder("orders.placed").tag("tenant", TenantContext.get()).register(reg);
```
**Careful:** tenant count × endpoint count × status × etc = cardinality bomb.
Tag the aggregate counters only, not histograms.

### Verify
- Create orders as tenant A and tenant B.
- `GET /orders` as tenant A returns only A's rows.
- Manually hit DB without a filter → both tenants visible (filter is in-code,
  not DB-enforced — document this).
- Hit rate limit as A; B's calls still succeed.

### Interview talking points
- **Row-per-tenant is the right default** for most SaaS. DB-per-tenant for
  regulated industries or giant enterprise contracts.
- **Noisy neighbour** — bulkhead per tenant (Resilience4j), rate limit per
  tenant, DB connection pool per tenant for the biggest ones.
- **Admin / support access** — "see-as-tenant" switch for support staff.
  Audit every impersonation.
- **Data export / GDPR** — tenants want their data out. Build a per-tenant
  export endpoint that reads from events (easier if you have event sourcing).
- **Database migrations** — row-per-tenant: one migration. Schema-per-tenant:
  N migrations. DB-per-tenant: migration orchestration becomes a product.
- **The leak vector** — forgetting `WHERE tenant_id =`. Enforce with DB-level
  row-level security (Postgres RLS) if you can; it's belt-and-suspenders.

---

## 6. GraphQL BFF

### What it is
**Backend for Frontend** — a dedicated service sitting between the client
and the microservices, exposing a single GraphQL endpoint that composes
calls across many downstreams.

```
   Mobile client             BFF (GraphQL)              Backends
   ─────────────             ─────────────              ────────
   query {                     ┌─────────────┐          ┌────────┐
     order(id: "o1") {         │  resolvers  │ ──REST──▶│ Order  │
       status                  │             │          └────────┘
       customer { name }       │  resolves   │          ┌────────┐
       items {                 │  per-field  │ ──REST──▶│ User   │
         product { title }     │             │          └────────┘
       }                       │  batches +  │          ┌────────┐
     }                         │  caches     │ ──gRPC──▶│Product │
   }                           └─────────────┘          └────────┘
```

One client request → one BFF query → many backend calls under the hood.

### Why interviewers ask
"Your mobile app makes 7 REST calls to render one screen — latency sucks on
3G." "iOS and web need slightly different fields — how?" GraphQL BFF
collapses this.

### Current state in this repo
- ❌ None. All clients call REST through the gateway.

### Implementation steps

**6.1 New Spring Boot module `graphql-bff`**
```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-graphql</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

**6.2 Schema**
```graphql
# resources/graphql/schema.graphqls
type Order {
  id: ID!
  status: String!
  amount: String!
  productId: ID!
  product: Product        # resolved via nested call
  customer: User
}
type Product { id: ID! name: String! price: String! }
type User    { id: ID! name: String! email: String! }

type Query {
  order(id: ID!): Order
  myOrders(limit: Int = 20): [Order!]!
}
```

**6.3 Resolvers**
```java
@Controller
public class OrderQueryResolver {
  @QueryMapping
  public Order order(@Argument String id) { return orderClient.get(id); }

  @SchemaMapping(typeName="Order", field="product")
  public Product resolveProduct(Order o) { return productClient.get(o.productId()); }
}
```

**6.4 Batching (N+1 fix)**
For `myOrders { product }` across 50 orders you'd fire 50 product calls.
Use a `DataLoader`:
```java
@Bean
public BatchLoaderRegistry batchLoaderRegistry() {
  BatchLoaderRegistry r = new DefaultBatchLoaderRegistry();
  r.forTypePair(String.class, Product.class)
   .registerMappedBatchLoader((ids, env) -> productClient.getBatch(ids));
  return r;
}
```
50 orders → 1 batched product call.

**6.5 Depth / cost limits**
Protect against malicious queries:
```yaml
spring.graphql:
  schema:
    introspection:
      enabled: false      # prod
graphql:
  max-depth: 10
  max-complexity: 1000
```

### Verify
- Fire a nested query from GraphiQL (`/graphiql`); confirm one HTTP in →
  N backend calls out.
- Enable batching; confirm `myOrders` with 20 orders triggers 1 product-batch
  call, not 20.
- Attempt a 15-level deep query → 400 Bad Request.

### Interview talking points
- **BFF per client** — mobile BFF ≠ web BFF ≠ partner-API BFF. Each
  optimises for its consumer.
- **GraphQL is not a database** — resolvers call existing services. Don't
  reinvent JOINs.
- **N+1 is the GraphQL footgun.** Always discuss DataLoader when GraphQL
  comes up.
- **Caching GraphQL** is harder — queries vary; cache per-resolver, not
  per-endpoint. Apollo-style persisted queries help.
- **Authn/authz** — JWT propagates through; per-field auth is a thing
  (`@Secured("ROLE_ADMIN")` on specific resolvers).
- **When NOT to use** — simple CRUD, few clients, team unfamiliar. REST +
  BFF composition in code is often enough.

---

## 7. gRPC between internal services

### What it is
HTTP/2 + Protobuf RPC. Strongly-typed, binary, bidirectional-streaming
support. Default internal-API choice at Google / Netflix.

### Why interviewers ask
"Internal order→payment calls are REST+JSON. What would you change?" Right
answers mention perf (binary codec, HTTP/2 multiplexing), schema
(Protobuf, codegen), streaming (bidirectional).

### Current state in this repo
- ❌ No gRPC anywhere. All internal calls are REST (RestTemplate / Feign).

### Implementation steps

**7.1 Shared `.proto` module**
```
proto-lib/
  src/main/proto/payment.proto
```
```protobuf
syntax = "proto3";
option java_package = "com.example.payment.grpc";
option java_multiple_files = true;

service PaymentService {
  rpc Charge(ChargeRequest) returns (ChargeResponse);
  rpc StreamStatus(StatusRequest) returns (stream StatusUpdate);  // server-streaming
}

message ChargeRequest { string orderId = 1; string amount = 2; }
message ChargeResponse { string paymentId = 1; string status = 2; }
```
Maven protobuf plugin generates Java stubs.

**7.2 Server side (payment-service)**
```java
@GrpcService
public class PaymentGrpcImpl extends PaymentServiceGrpc.PaymentServiceImplBase {
  @Override
  public void charge(ChargeRequest req, StreamObserver<ChargeResponse> obs) {
    PaymentResult r = paymentCore.charge(req.getOrderId(), new BigDecimal(req.getAmount()));
    obs.onNext(ChargeResponse.newBuilder().setPaymentId(r.id()).setStatus(r.status()).build());
    obs.onCompleted();
  }
}
```
`grpc-spring-boot-starter` wires it onto a separate port (default 9090).

**7.3 Client side (order-service)**
```java
@GrpcClient("payment")
private PaymentServiceGrpc.PaymentServiceBlockingStub stub;
```
```yaml
grpc.client.payment:
  address: static://payment-service:9090
  negotiationType: plaintext
```

**7.4 Interceptors**
Trace propagation (W3C `traceparent` in metadata), auth (JWT in metadata),
error mapping (`StatusRuntimeException` → domain exceptions).

**7.5 Keep REST for external**
External clients still hit REST at the gateway. gRPC is internal only
(or expose via gRPC-Web if the browser needs it).

### Verify
- `grpcurl -plaintext payment-service:9090 PaymentService/Charge -d '{"orderId":"o1","amount":"9.99"}'` returns a response.
- Latency panel: p99 order→payment drops ~30 % vs REST.
- `grpcurl ... StreamStatus` streams updates until closed.

### Interview talking points
- **gRPC vs REST** — binary smaller, HTTP/2 multiplexes, Protobuf codegen
  = type safety. REST easier to curl / debug / cache.
- **When gRPC wins** — internal, high-RPS, strict schema, streaming.
- **When REST wins** — external public APIs, browser clients, debuggability,
  team familiarity.
- **Streaming kinds** — unary, server-streaming, client-streaming,
  bidirectional. Know when each applies.
- **Error model** — gRPC status codes (`INVALID_ARGUMENT`, `NOT_FOUND`,
  `RESOURCE_EXHAUSTED`) + rich error details in metadata.
- **Load balancing** — gRPC's long-lived connections don't balance well with
  classic L4 LBs. Use client-side LB (gRPC name resolver) or Envoy/Istio.

---

## 8. WebSocket / SSE push

### What it is
Server pushes data to the client without the client polling.

| | WebSocket | SSE | Polling |
|---|---|---|---|
| Direction | bidirectional | server→client | client→server |
| Protocol | ws:// (upgraded HTTP) | HTTP/1.1 streaming | HTTP |
| Reconnect | manual | auto by browser | by request |
| When to pick | chat, collab editing | notifications, dashboards | last resort |

### Why interviewers ask
"User gets a notification when their order ships — how?" "Build a live
order-status page." These flush out whether you understand push vs pull.

### Current state in this repo
- ✅ `spring-boot-starter-websocket` on `user-service` and `angle-app`.
- ❌ Notification service doesn't push to clients; it just logs.

### Implementation steps (notification SSE is the simpler win)

**8.1 Add SSE endpoint**
```java
@RestController
@RequestMapping("/notifications")
public class NotificationStreamController {
  private final Map<String, Sinks.Many<ServerSentEvent<String>>> perUser = new ConcurrentHashMap<>();

  @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<ServerSentEvent<String>> stream(@AuthenticationPrincipal Jwt jwt) {
    String userId = jwt.getSubject();
    Sinks.Many<ServerSentEvent<String>> sink = perUser.computeIfAbsent(userId,
        k -> Sinks.many().multicast().onBackpressureBuffer());
    return sink.asFlux().doOnCancel(() -> perUser.remove(userId));
  }

  // called by the Kafka listener below
  public void push(String userId, String json) {
    Sinks.Many<ServerSentEvent<String>> sink = perUser.get(userId);
    if (sink != null) sink.tryEmitNext(ServerSentEvent.builder(json).build());
  }
}
```

**8.2 Kafka → push bridge**
```java
@Component
@RequiredArgsConstructor
public class OrderEventListener {
  private final NotificationStreamController stream;

  @KafkaListener(topics = "payment.completed", groupId = "notification-sse")
  public void onPaid(PaymentCompletedEvent e) {
    stream.push(e.userId(), toJson(e));
  }
}
```

**8.3 Browser consumer**
```javascript
const evt = new EventSource('/notifications/stream', { withCredentials: true });
evt.onmessage = ev => showToast(JSON.parse(ev.data));
```
Auto-reconnect is built in.

**8.4 Fan-out across instances**
Problem: user U connects to instance N1; the Kafka message lands on
instance N2's listener; N2's `perUser` map doesn't know about U.

Fix: use Redis pub/sub as the fan-out bus between instances, or make the
notification topic consumer group one-partition-per-instance with sticky
assignment, or front it with a dedicated push server (sticky LB).

### Verify
- Open browser, connect to `/notifications/stream` (DevTools → Network → EventStream).
- Push a `PaymentCompleted` event to Kafka — toast appears in browser < 1 s.
- Kill the connection; `perUser` map cleans up.
- Scale notification to 2 replicas; push still arrives (requires Redis fan-out).

### Interview talking points
- **SSE first, WS when needed.** SSE is one-way, works through firewalls, has
  built-in reconnect. 90 % of "push to UI" is SSE.
- **Backpressure** — slow client can starve the sink. Reactor's
  `onBackpressureBuffer` with a bound + drop policy.
- **Horizontal scale** — the hard part. Sticky sessions OR a shared pub/sub
  bus OR a dedicated push gateway (Pusher, Ably, Centrifugo pattern).
- **Auth** — browsers can't set custom headers on SSE/EventSource; use
  cookies (SameSite=Strict) or query-string token with short TTL.
- **Order of delivery** — push is best-effort. Pair with "mark-as-read" API
  so clients can backfill on reconnect.
- **Mobile** — SSE/WS compete with battery; push notifications (APNs/FCM)
  are the native answer for anything the user isn't actively looking at.

---

## Suggested build order (4 weekends)

**Weekend 1 — ops muscle**
1. (4 h) Saga admin UI (schema, orchestrator writes, Thymeleaf list + detail).
2. (3 h) Rate limiting polish — per-API-key resolver + response headers + 429 problem+json.

**Weekend 2 — speed**
3. (3 h) Redis caching on product-service (cache-aside + stampede lock).
4. (3 h) Feature flag impl + aspect + admin surface.

**Weekend 3 — scale**
5. (5 h) Multi-tenancy: TenantContext filter, JPA Hibernate filter, per-tenant metrics.

**Weekend 4 — modern surfaces**
6. (4 h) GraphQL BFF composing Order + Product + User.
7. (4 h) gRPC between order-service and payment-service (keep REST too).
8. (2 h) SSE push from notification to a demo HTML page.

Weekend 3 is the biggest; everything else fits a long afternoon.

---

## Interview-grade one-liner summary

> *"We expose a saga admin UI that persists orchestrator state so operators
> can inspect stuck sagas and trigger compensations. Hot reads go through
> Redis cache-aside with a stampede-lock per hot key; the gateway rate-limits
> per API key via Redis token bucket with RFC 7807 problem+json on 429.
> Runtime behaviour is gated by a feature-flag store with sticky percentage
> rollouts, invalidated cluster-wide via a Kafka 'flag.updated' topic.
> Multi-tenant data is isolated via a Hibernate filter driven by a
> TenantContext populated from a JWT claim at the gateway. A GraphQL BFF
> composes Order + Product + User for mobile with per-field DataLoader
> batching to kill the N+1. Order↔payment uses gRPC internally for lower
> latency and strict schema; REST stays for external. Live order updates
> stream to the browser over SSE, fanned out across notification instances
> via Redis pub/sub."*

If you can say that cleanly and point to running code behind each clause,
you've aced the "advanced patterns" portion of any staff-level interview.

---

## Related docs

- [tier1-architecture.md](tier1-architecture.md) · [tier2-architecture.md](tier2-architecture.md)
- [tier2-roadmap.md](tier2-roadmap.md) — if you want to see the earlier style
- [concepts/circuit-breaker.md](concepts/circuit-breaker.md)
- [concepts/api-gateway.md](concepts/api-gateway.md)
- [concepts/saga-orchestration.md](concepts/saga-orchestration.md)
