# API Gateway — Dynamic Routes from DB

Add / update / delete routes at runtime through an admin API, backed by a
reactive H2 database (R2DBC). Multiple gateway replicas converge on DB
state through a scheduled refresh.

Interview-favorite answer to *"how do you add a new backend without redeploying
the gateway?"*

---

## 1. Static vs dynamic mental model

```
Static (application.yml only)         Dynamic (this build)
─────────────────────────────         ──────────────────────────────────
 yml routes                            yml routes + DB routes
 read once at startup                  read at startup + on each RefreshRoutesEvent
 change → restart                      change → POST /admin/routes → live in < 1s

                                       ┌── admin API ── DB write ── publish event
                                       │                                    │
                                       │                                    ▼
                                       │              CachingRouteLocator rebuilds
                                       └── scheduled refresh (30s) triggers same rebuild
                                                                           on other replicas
```

---

## 2. Spring Cloud Gateway interfaces you touch

```
┌────────────────────────────────────────────────────────────────────┐
│  RouteDefinition            data class: id, uri, predicates,       │
│                             filters, order, metadata               │
│                                                                    │
│  RouteDefinitionLocator     read-only: getRouteDefinitions()       │
│                             Framework composes ALL locators in the │
│                             context (yml, discovery, and yours).   │
│                                                                    │
│  RouteDefinitionRepository  read-write extension:                  │
│                             save() / delete() / getRouteDefinitions()│
│                             What YOU implement.                    │
│                                                                    │
│  RefreshRoutesEvent         Application event that tells the       │
│                             CachingRouteLocator to rebuild.        │
└────────────────────────────────────────────────────────────────────┘
```

Register a `RouteDefinitionRepository` bean → framework picks it up automatically.
No wiring of the composite locator needed.

---

## 3. Architecture

```
                                  ┌────────────────────────────┐
POST /admin/routes ──────▶        │  RouteAdminController      │
DELETE /admin/routes/{id}         │  - CRUD via                │
GET /admin/routes                 │    RouteDefinitionRepository│
POST /admin/routes/refresh        │  - publishEvent(RefreshRoutesEvent)│
                                  └────────────────────────────┘
                                             │
                                             │ save/delete
                                             ▼
                                  ┌────────────────────────────┐
                                  │  JdbcRouteDefinitionRepo   │
                                  │  implements RouteDefRepo   │
                                  │  entity ↔ RouteDefinition  │
                                  │  (JSON serialization)      │
                                  └────────────────────────────┘
                                             │
                                             ▼
                                  ┌────────────────────────────┐
                                  │  H2 (file mode)            │
                                  │  ./data/gateway-routes.mv.db│
                                  │  route_definition table    │
                                  └────────────────────────────┘

Route refresh path:                          ▲
                                             │ read on refresh
  RefreshRoutesEvent ──▶ CachingRouteLocator │
                              │              │
                              ├─ yml routes ──────────────────────┐
                              ├─ JdbcRouteDefRepo ────────────────┤
                              └─ (optional) Eureka discovery      │
                                                                  │
                              merged in-memory route table  ◀─────┘

RouteRefreshScheduler:
  @Scheduled(30s) → publishEvent(RefreshRoutesEvent)   ← all replicas converge
```

---

## 4. Design choices

### R2DBC over blocking JDBC

Gateway runs on Netty (reactive). Blocking JDBC on the request path would block
event loop threads. R2DBC keeps everything non-blocking.

Admin API is low-volume — could use JDBC there — but keeping one flavor is simpler.

### H2 default, Postgres via profile

- **Default profile — H2 file mode** — `r2dbc:h2:file:///./data/gateway-routes` — survives
  restart, zero external infra. Schema created via `schema.sql` on boot. Single-writer only.
- **`postgres` profile — real DB** — `r2dbc:postgresql://localhost:5432/gateway`. Schema
  managed by **Flyway** (see below). Multi-writer, production shape.

Same Java code — R2DBC abstracts the driver. Only YAML differs. Switch with
`-Dspring.profiles.active=postgres` and `docker-compose up postgres`.

### Flyway for Postgres migrations

`schema.sql` (H2 profile) uses `CREATE TABLE IF NOT EXISTS` — fine for demos,
but no way to safely add a column or track applied changes.

The `postgres` profile uses Flyway instead:

- Migrations live in `src/main/resources/db/migration/` — one file per version
  (`V1__init_routes_and_audit.sql`, `V2__...`, ...)
- Flyway records applied migrations in a `flyway_schema_history` table
- On boot: applies pending migrations, refuses to run out-of-order or with
  edited-after-apply files (checksum mismatch)
- Uses **blocking JDBC** for migrations (not R2DBC). This is fine — migrations
  are startup ops, not request path

Trade-offs:
- Adding a column? → `V2__add_column.sql` with `ALTER TABLE ADD COLUMN`. Never
  edit V1 after it's applied.
- Two pods boot simultaneously? → Flyway acquires a Postgres advisory lock;
  one migrates, others wait.
- Existing Postgres with no history? → `baseline-on-migrate: true` creates the
  history table and marks pre-existing migrations as applied at `baseline-version`.

Interview soundbite: *"Flyway on JDBC + app on R2DBC — migration is a
one-shot ops task at startup; reactive semantics don't matter there."*

### Predicates/filters as JSON columns

```sql
CREATE TABLE route_definition (
    id           VARCHAR(64)  PRIMARY KEY,
    uri          VARCHAR(255) NOT NULL,
    predicates   TEXT         NOT NULL,  -- JSON array
    filters      TEXT,                   -- JSON array
    route_order  INTEGER      DEFAULT 0,
    metadata     TEXT,                   -- JSON object
    enabled      BOOLEAN      DEFAULT TRUE,
    ...
);
```

Why not normalize into `route_predicate` / `route_filter` tables?

- Each predicate/filter has a *different* args shape (Path takes `_genkey_0`, RewritePath takes `regexp` + `replacement`)
- Would need EAV pattern → maintenance nightmare
- Framework's `PredicateDefinition` / `FilterDefinition` already map cleanly to JSON

### Cache invalidation

`CachingRouteLocator` already caches merged routes. We just need to trigger `RefreshRoutesEvent`:

```
Read  path: Request → CachingRouteLocator (cached) → matched route
Write path: Admin API → repository.save() → publishEvent(RefreshRoutesEvent)
                                                           │
                                                           ▼
                                                    Cache rebuild
```

### Multi-replica sync

Two gateway pods; one gets a POST to `/admin/routes`. The other has stale cache.

| Approach | Complexity | Staleness |
|---|---|---|
| **Poll DB every N sec + refresh** (what we built) | Trivial | 30s (config) |
| Redis pub/sub → all subscribers refresh | Medium | Sub-second |
| Service mesh / config server (Consul) | High | Sub-second |

We ship polling with an extension note for pub/sub. Interviewer will ask about
the trade-off — you can answer with both.

### Route validation (fail fast on bad admin input)

Without validation, a bad `RouteDefinition` posted to `/admin/routes` would:
1. Persist to the DB (H2 accepts any JSON in TEXT columns)
2. Trigger `RefreshRoutesEvent`
3. Framework logs a warning when it fails to build the route
4. Silently dead — bad row stays in DB, polluting every future refresh

`RouteValidator` dry-runs the definition before persist:

- **id** required, matches `[a-zA-Z0-9._:-]{1,64}`
- **uri** parseable + scheme in `{lb, http, https, ws, wss, forward, no}`
- **at least one predicate**
- **every predicate name** resolves to a `RoutePredicateFactory` bean (looks up the factory registry)
- **every filter name** resolves to a `GatewayFilterFactory` bean

Bad input → `400 Bad Request` with `{"error":"invalid_route","message":"..."}`. Never touches DB.

Not validated (intentionally): predicate/filter args shape. Those bind at
route-build time; a wrong-shape args would surface on the next
`RefreshRoutesEvent`. Trade-off — full arg validation would require
re-implementing Spring's config binding.

### Role-based admin access

Previously: any authenticated JWT could hit `/admin/**`. Now: caller needs an
authority listed in `gateway.admin.required-authorities` (defaults: `SCOPE_admin`
OR `ROLE_ADMIN`).

`AdminJwtAuthenticationConverter` extracts authorities from the JWT:
- `scope` claim (space-separated or list) → `SCOPE_<value>`
- `roles` claim (list or CSV) → `ROLE_<VALUE>`
- **Demo escape hatch**: `demo-admin-subs` list bypasses claim inspection and grants
  `SCOPE_admin` to matching JWT subs. Useful when your auth-server hasn't wired
  scope claims yet (`admin:admin123` client_credentials with sub=`admin` works
  out of the box). **KEEP EMPTY IN PROD.**

Wired via `.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(...)))`.

### Audit trail

Every admin mutation writes a row to `route_audit`:

| Column | Value |
|---|---|
| action | `CREATE`, `UPDATE`, `DELETE`, `REFRESH` |
| actor | JWT sub / API-key ownerId / `anonymous` |
| actor_type | `JWT`, `API_KEY`, `ANONYMOUS` |
| payload | JSON snapshot of the `RouteDefinition` (null for DELETE / REFRESH) |
| outcome | `SUCCESS`, `VALIDATION_FAILED`, `STORE_ERROR` |
| reason | error message when outcome ≠ SUCCESS |
| correlation_id | from `X-Correlation-Id` request header |
| created_at | server timestamp |

Append-only in normal ops — never updated or deleted. Query via:
- `GET /admin/routes/audit` — latest 100 events
- `GET /admin/routes/{id}/audit` — full history for one route

Failure-mode-friendly: audit writes use `.onErrorResume(e -> Mono.empty())` so
a broken audit table can NEVER break the admin API itself.

Interview soundbite: *"the audit table is defensive infrastructure. If it fails,
we log and continue — admin operations must not depend on audit availability."*

---

## 5. Config

```yaml
spring:
  r2dbc:
    url: r2dbc:h2:file:///./data/gateway-routes;DB_CLOSE_DELAY=-1;MODE=LEGACY
    username: sa
    password: ""
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql

gateway:
  dynamic-routes:
    enabled: true                # @ConditionalOnProperty gates all beans
    refresh-interval: 5m         # poll fallback (pub/sub is fast path)
    pubsub:
      enabled: true
      channel: gateway.routes.refresh

  admin:
    required-authorities:        # any-of; caller needs at least one
      - SCOPE_admin
      - ROLE_ADMIN
    scope-claim: scope           # JWT claim name for scopes
    roles-claim: roles           # JWT claim name for roles
    demo-admin-subs:             # dev-only escape hatch — subs auto-granted SCOPE_admin
      - admin                    # KEEP EMPTY IN PROD
```

Enable scheduling on the main class:
```java
@SpringBootApplication
@EnableScheduling
public class ApiGatewayApps { ... }
```

---

## 6. Files added / changed

```
api-gateway/
├── pom.xml                                                          (+ r2dbc, r2dbc-h2, h2,
│                                                                       r2dbc-postgresql, postgresql JDBC,
│                                                                       flyway-core, flyway-database-postgresql)
├── docker-compose.postgres.yml                                      (NEW — local Postgres for postgres profile)
├── data/                                                            (created — H2 file lives here on default profile)
└── src/main/
    ├── java/com/example/apigateway/
    │   ├── ApiGatewayApps.java                                      (+ @EnableScheduling)
    │   ├── config/
    │   │   ├── DynamicRoutesProperties.java                         (NEW)
    │   │   └── GatewaySecurityConfig.java                           (+ /admin/** requires admin authority)
    │   ├── security/                                                 (NEW package)
    │   │   ├── AdminAuthProperties.java                             (NEW)
    │   │   └── AdminJwtAuthenticationConverter.java                 (NEW — scope+roles→authorities)
    │   └── dynamicroutes/
    │       ├── RouteEntity.java                                     (NEW)
    │       ├── RouteEntityRepository.java                           (NEW — R2DBC CRUD)
    │       ├── JdbcRouteDefinitionRepository.java                   (NEW — the bridge)
    │       ├── RouteValidator.java                                  (NEW — pre-persist validation)
    │       ├── RouteAuditEntity.java                                (NEW — audit row)
    │       ├── RouteAuditRepository.java                            (NEW)
    │       ├── RouteAuditService.java                               (NEW — actor extraction + persist)
    │       ├── RouteAdminController.java                            (NEW — /admin/routes + audit endpoints)
    │       └── RouteRefreshScheduler.java                           (NEW — scheduled refresh)
    └── resources/
        ├── schema.sql                                                (H2 profile — route_definition + route_audit)
        ├── application.yml                                          (r2dbc + gateway.dynamic-routes + gateway.admin)
        ├── application-postgres.yml                                 (NEW — Postgres R2DBC + JDBC + Flyway config)
        └── db/migration/
            └── V1__init_routes_and_audit.sql                        (NEW — Flyway migration, Postgres idioms)

docs/microservices/api-gateway/
└── dynamic-routes.md                                                 (this file)
```

---

## 7. Verification

Prereqs: everything from previous builds — Redis, Eureka, auth-server, user-service,
product-service must be running.

```bash
mvn -pl api-gateway clean spring-boot:run

TOKEN=$(curl -s -u admin:admin123 \
  -d "grant_type=client_credentials&scope=read" \
  http://localhost:9010/oauth2/token | jq -r .access_token)
```

### 7.1 List routes (empty on first run)

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/admin/routes | jq
# → []
```

### 7.2 Hit a route that doesn't exist yet

```bash
curl -i -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/products-preview/all
# → 404 (no route matches this path)
```

### 7.3 Create a dynamic route

Route `/api/v1/products-preview/**` → `product-service`, rewriting the path.

```bash
curl -i -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  http://localhost:8080/admin/routes \
  -d '{
    "id": "products-preview",
    "uri": "lb://product-service",
    "order": 0,
    "predicates": [
      { "name": "Path", "args": { "_genkey_0": "/api/v1/products-preview/**" } }
    ],
    "filters": [
      { "name": "RewritePath", "args": {
          "regexp": "/api/v1/products-preview/(?<seg>.*)",
          "replacement": "/api/v1/products/${seg}"
      }}
    ]
  }'
# → 201 Created
```

### 7.4 Same URL now works

```bash
curl -i -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/products-preview/all
# → 200 — proxied to product-service's /api/v1/products/all
```

### 7.5 Confirm in admin API

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/admin/routes | jq
# → [{ "id": "products-preview", ... }]
```

### 7.6 Confirm via built-in gateway actuator

```bash
curl -s http://localhost:8080/actuator/gateway/routes | jq '.[] | select(.route_id=="products-preview")'
# → shows the full compiled route, filters + predicates included
```

### 7.7 Update a route

```bash
curl -i -X PUT -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  http://localhost:8080/admin/routes/products-preview \
  -d '{
    "uri": "lb://product-service",
    "order": 0,
    "predicates": [
      { "name": "Path", "args": { "_genkey_0": "/api/v1/products-preview-v2/**" } }
    ],
    "filters": []
  }'
# → 200 OK; old path 404s, new path works
```

### 7.8 Delete

```bash
curl -i -X DELETE -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/admin/routes/products-preview
# → 204 No Content

curl -i -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/products-preview/all
# → 404 again
```

### 7.9 Manual refresh (useful in multi-replica setups)

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/admin/routes/refresh
# → 200 OK (rebuilds this instance's cache)
```

### 7.10 Peek inside H2

Any DB client can connect: `jdbc:h2:file:./data/gateway-routes;MODE=LEGACY` (JDBC URL,
same file). Or use H2's built-in web console during dev.

Or just tail the file: routes live in `data/gateway-routes.mv.db`.

---

## 8. Failure modes & mitigations

| Scenario | Behavior | Mitigation |
|---|---|---|
| DB down at startup | R2DBC connection fails → app fails to boot | Add fail-safe: catch in `getRouteDefinitions()`, return empty flux |
| Invalid JSON in DB row (corrupted) | Row skipped via `onErrorContinue` in repository | Add validation on save; scheduled cleanup job |
| Route references unknown filter (`GhostFilter`) | Route rebuild fails; framework logs error; route not activated | Validate at admin-API save (dry-run `RouteDefinitionValidator`) |
| Two replicas race saving same route ID | Last-write-wins in H2 | Add `@Version` for optimistic locking; reject conflicting updates |
| Refresh interval too long | Multi-replica staleness up to N seconds | Reduce interval OR switch to Redis pub/sub |
| Admin API exposed to public net | Anyone with a JWT can add routes | Add role check (needs `JwtAuthenticationConverter` with authorities) |
| Route with `Retry` inside `CircuitBreaker` filter — misconfigured | Runtime error at route build | Validate ordering in admin controller |
| H2 file corruption | DB won't open | Backup `data/gateway-routes.mv.db` periodically OR use Postgres |

---

## 9. Interview cheat-sheet

| Question | Answer |
|---|---|
| Why not just reload yml? | yml is baked into `RouteDefinitionLocator` at startup — needs restart. `RouteDefinitionRepository` lets us mutate the in-memory route table at runtime. |
| How does Spring wire my custom repo in? | Any `@Bean` implementing `RouteDefinitionRepository` — framework composes it with yml + discovery locators automatically. |
| What triggers a route reload? | `RefreshRoutesEvent` published via `ApplicationEventPublisher`. `CachingRouteLocator` listens for it and rebuilds. |
| Why store predicates as JSON? | Heterogeneous shape — each predicate has different args. Normalizing = EAV nightmare. |
| R2DBC vs JDBC in gateway? | Gateway is reactive — blocking JDBC blocks the Netty event loop. R2DBC keeps it clean. |
| Multi-replica sync — how? | Scheduled poll (this build) OR Redis pub/sub (production). Trade-off: staleness vs infra. |
| Security of admin API? | JWT-authenticated + role-based: `/admin/**` requires `SCOPE_admin` OR `ROLE_ADMIN` (configurable). AdminJwtAuthenticationConverter maps `scope` + `roles` claims → Spring authorities. Never expose to public net. |
| What if I POST an invalid route? | RouteValidator rejects with 400 before any DB write. Checks: id format, uri scheme, predicate/filter factory bean existence. Prevents polluting the DB. |
| Who changed what, when? | route_audit table — append-only. GET /admin/routes/audit shows recent 100. Per-route history: GET /admin/routes/{id}/audit. Actor extracted from JWT/API-key auth context. |
| DB down at startup — what should happen? | Depends on ops posture: fail-fast (this build) OR degrade to yml-only. Use `onErrorResume` in `getRouteDefinitions()` for latter. |
| Concurrent modifications? | H2 auto-locks per row. For real concurrency: `@Version` optimistic locking. |
| How much lag is acceptable? | Bounded by refresh interval. 30s for admin-only changes; sub-second needs pub/sub. |
| Caching layer? | `CachingRouteLocator` sits between all `RouteDefinitionLocator`s and the request path. Rebuilds on `RefreshRoutesEvent`. |
| Bulk import? | POST array → iterate save → publish ONE refresh. Add an endpoint. |
| Bad route (unknown filter name)? | Framework logs on rebuild, route stays inactive. Bad row remains in DB but doesn't route. Add validation on admin save. |
| What if a route references a bean like `@userKeyResolver`? | The bean must exist in the JVM. Filter bean loading happens at startup; DB-defined routes referencing non-existent beans just log and fail to activate. |

---

## 10. Common gotchas the interviewer will probe

1. **Cache invalidation** — the composite `RouteLocator` caches. Save without publishing `RefreshRoutesEvent` = admin sees the row in DB but request 404s.
2. **Predicate arg shape** — many predicates use positional `_genkey_0`, `_genkey_1`. RewritePath uses named `regexp`, `replacement`. Both work; know when to use which.
3. **Filter reference resolution** — filters must be registered as beans in the JVM. You can't inject a new filter class via DB.
4. **Race on save + refresh** — call `publishEvent` inside `.doOnSuccess()` (after save completes), not `.doFirst()`.
5. **H2 file lock** — only ONE gateway process can open a file H2 DB. Multi-replica → Postgres.
6. **Bean creation order** — `RouteDefinitionRepository` bean loads before `CachingRouteLocator` reads from it at startup. If your repo throws, gateway fails to start with a cryptic error.

---

## 11. Extensions (parked)

- **Optimistic locking** — add `@Version Integer version` field on RouteEntity; reject stale updates with 409.
- **Bulk import/export** — GET returns all routes; POST accepts array. Useful for GitOps.
- **Deep arg validation** — currently RouteValidator checks factory NAMES exist. Extension: instantiate the config object per predicate/filter, run `apply()`, catch binding errors. Requires reflection into Spring's config-binding machinery.
- **Audit retention** — TTL / partition drop old rows. Currently unbounded growth.
- **Audit-triggered alerts** — subscribe to audit writes; page oncall when `VALIDATION_FAILED` rate > N/min or unauthorized attempts detected.

---

## 12. Verification of admin-hardening pack

Assumes gateway + auth-server + Redis running. `admin:admin123` (client_credentials
sub=`admin`) is granted `SCOPE_admin` via the `demo-admin-subs` escape hatch.

```bash
ADMIN_TOKEN=$(curl -s -u admin:admin123 \
  -d "grant_type=client_credentials&scope=read" \
  http://localhost:9010/oauth2/token | jq -r .access_token)
```

### Role-based auth

```bash
# 1. Admin JWT → 200
curl -i -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/admin/routes
# → 200

# 2. Any non-admin JWT (issue one with a different client / no admin scope) → 403
# (skip if you don't have a non-admin client set up)

# 3. No auth → 401
curl -i http://localhost:8080/admin/routes
# → 401
```

### Route validation

```bash
# 4. Bad predicate name → 400, never persisted
curl -i -X POST -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  http://localhost:8080/admin/routes \
  -d '{
    "id": "bad-route-1",
    "uri": "lb://user-service",
    "predicates": [{"name":"NotARealPredicate","args":{"_genkey_0":"/x"}}]
  }'
# → 400 {"error":"invalid_route","message":"unknown predicate 'NotARealPredicate' — available: [Path, After, Before, ...]"}

# Confirm no DB row
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:8080/admin/routes \
  | jq '.[] | select(.id=="bad-route-1")'
# → (empty)

# 5. Bad URI scheme → 400
curl -i -X POST -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  http://localhost:8080/admin/routes \
  -d '{
    "id": "bad-route-2",
    "uri": "ftp://nope",
    "predicates": [{"name":"Path","args":{"_genkey_0":"/x"}}]
  }'
# → 400 uri scheme 'ftp' not in [lb, http, https, ws, wss, forward, no]

# 6. Missing id → 400
curl -i -X POST -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  http://localhost:8080/admin/routes \
  -d '{"uri":"lb://user-service","predicates":[{"name":"Path","args":{"_genkey_0":"/x"}}]}'
# → 400 id is required

# 7. Valid route → 201 + audit row
curl -i -X POST -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  http://localhost:8080/admin/routes \
  -d '{
    "id": "products-preview",
    "uri": "lb://product-service",
    "predicates": [{"name":"Path","args":{"_genkey_0":"/api/v1/products-preview/**"}}]
  }'
# → 201
```

### Audit trail

```bash
# 8. Recent audit events (last 100)
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/admin/routes/audit | jq
# Shows failed validation attempts + successful CREATE, all with actor=admin, actor_type=JWT

# 9. Per-route history
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/admin/routes/products-preview/audit | jq

# 10. Inspect H2 directly (audit table)
# Using H2 web console or any JDBC client:
#   SELECT id, route_id, action, actor, outcome, reason, created_at
#   FROM route_audit ORDER BY created_at DESC LIMIT 10;

# 11. DELETE writes a DELETE audit row (payload=null)
curl -i -X DELETE -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/admin/routes/products-preview
# → 204

curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/admin/routes/products-preview/audit | jq
# Latest entry: action=DELETE outcome=SUCCESS
```

---

## 13. Verification of Postgres profile + Flyway

### 13.1 Boot Postgres

```bash
docker-compose -f api-gateway/docker-compose.postgres.yml up -d
docker exec -it gateway-postgres psql -U gateway -d gateway -c "\dt"
# → No relations found (empty)
```

### 13.2 Boot gateway with postgres profile

```bash
mvn -pl api-gateway clean spring-boot:run \
  -Dspring-boot.run.arguments="--spring.profiles.active=postgres"

# Watch startup logs for Flyway:
#   Flyway Community Edition ... by Redgate
#   Database: jdbc:postgresql://localhost:5432/gateway (PostgreSQL 16.x)
#   Successfully validated 1 migration
#   Creating Schema History table "public"."flyway_schema_history" ...
#   Migrating schema "public" to version "1 - init routes and audit"
#   Successfully applied 1 migration to schema "public"
```

### 13.3 Verify tables + migration history

```bash
docker exec -it gateway-postgres psql -U gateway -d gateway -c "\dt"
# → flyway_schema_history, route_audit, route_definition

docker exec -it gateway-postgres psql -U gateway -d gateway \
  -c "SELECT version, description, success FROM flyway_schema_history;"
#  version |     description       | success
#  --------+----------------------+---------
#  1       | init routes and audit | t
```

### 13.4 Admin API works identically against Postgres

```bash
ADMIN_TOKEN=$(curl -s -u admin:admin123 \
  -d "grant_type=client_credentials&scope=read" \
  http://localhost:9010/oauth2/token | jq -r .access_token)

curl -i -X POST -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  http://localhost:8080/admin/routes \
  -d '{
    "id": "postgres-demo",
    "uri": "lb://product-service",
    "predicates": [{"name":"Path","args":{"_genkey_0":"/postgres-demo/**"}}]
  }'
# → 201

# Confirm in Postgres
docker exec -it gateway-postgres psql -U gateway -d gateway \
  -c "SELECT id, uri FROM route_definition;"
# → postgres-demo | lb://product-service

docker exec -it gateway-postgres psql -U gateway -d gateway \
  -c "SELECT route_id, action, actor, outcome FROM route_audit;"
# → postgres-demo | CREATE | admin | SUCCESS
```

### 13.5 Second boot — Flyway is idempotent

```bash
# Ctrl-C and restart the gateway
# Startup logs:
#   Current version of schema "public": 1
#   Schema "public" is up to date. No migration necessary.
```

### 13.6 Default profile still works with H2

```bash
# Kill the postgres-profile gateway
mvn -pl api-gateway spring-boot:run
# → boots against H2 file (data/gateway-routes.mv.db) as before
# → Flyway auto-disables (no JDBC datasource in default profile)
# → schema.sql applies as before
```

### 13.7 Adding a future migration

Any schema change goes in a new `V*` file — never edit V1:

```sql
-- src/main/resources/db/migration/V2__add_route_version.sql
ALTER TABLE route_definition ADD COLUMN version INTEGER DEFAULT 0;
```

Restart with postgres profile → Flyway detects V2, applies it, records success.
