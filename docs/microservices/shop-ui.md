# shop-ui — Thymeleaf + HTMX storefront

Server-rendered front end that exercises every Tier 1-4 feature end-to-end.
No DB; cart lives in HttpSession. One Spring Boot process, one browser tab,
one `curl` of a product → one order → one saga → one SSE push.

- [1. Topology](#1-topology)
- [2. Pages & what each one touches](#2-pages--what-each-one-touches)
- [3. Running the full stack](#3-running-the-full-stack)
- [4. Demo flow — browse to checkout](#4-demo-flow--browse-to-checkout)
- [5. Known limits + next steps](#5-known-limits--next-steps)

---

## 1. Topology

```
  Browser
    │  HTML (server-rendered)
    ▼
  ┌───────────────────────────────────────────────────────────┐
  │  shop-ui :8089  (Thymeleaf + HTMX + Bootstrap)            │
  │                                                           │
  │  / HomeController           → featured grid (ES)          │
  │  /products ListingController → facets + pagination (ES)   │
  │  /products/{id} PDPController → PDP (SQL + Redis cache)   │
  │  /cart /cart/add CartController → HttpSession Cart        │
  │  /checkout CheckoutController → POST /api/v1/orders       │
  │  /orders/confirm?id= → polls status until terminal        │
  └───────────────────────────────────────────────────────────┘
         │ REST                                 │ REST
         ▼                                      ▼
  ┌────────────────────┐              ┌────────────────────┐
  │ product-query:8088 │              │ api-gateway :8080  │
  │ (Elasticsearch)    │              │ ┌────────────────┐ │
  │  /products/search/ │              │ │ product-service│ │
  │   query, /suggest  │              │ │ (SQL + Redis)  │ │
  └────────────────────┘              │ ├────────────────┤ │
                                      │ │ order-service  │ │
                                      │ │ (outbox+saga)  │ │
                                      │ └────────────────┘ │
                                      └────────────────────┘
```

**Why two different reads?**
- Listing / facets go to `product-query` (Elasticsearch) — fuzzy, aggregations, fast.
- PDP goes to `product-service` through the gateway — demonstrates the Tier 4
  Redis cache, authoritative stock numbers, and the write path for inventory
  updates (not implemented in UI yet).

---

## 2. Pages & what each one touches

| route | backend hit | tier features exercised |
|---|---|---|
| `GET /` | product-query | ES read model (CQRS from Tier 2) |
| `GET /products` | product-query `?aggregations=category,brand,price` | ES bool queries + facets (Tier 2 + Phase 4) |
| `GET /products/{id}` | product-service via gateway | Redis cache-aside (Tier 4), gateway rate-limit (Tier 4), tracing (Tier 1) |
| `POST /cart/add` | — | HttpSession-scoped Cart bean |
| `GET /cart` | — | session state |
| `GET /checkout` | — | pins `Idempotency-Key` in session (Tier 1) |
| `POST /checkout` | order-service via gateway, `POST /api/v1/orders` | Idempotency-Key filter (Tier 1), outbox + saga orchestrator (Tier 2), tracing (Tier 1), resilience (Tier 1) |
| `GET /orders/confirm?id=…` | order-service via gateway, polls | saga state advances through Kafka hops |

Every single Tier 1-4 primitive lights up on one checkout. That's the point.

---

## 3. Running the full stack

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.0.2.jdk/Contents/Home
mvn -DskipTests package

# Infra
docker compose up -d elasticsearch kafka redis zipkin prometheus grafana loki promtail \
                     mysql-user mysql-product mysql-auth eureka-server auth-server

# App plane
docker compose up -d api-gateway product-service order-service

# ES read side + the UI from source
cd order-query && mvn spring-boot:run &
cd product-query && mvn spring-boot:run &
cd shop-ui && mvn spring-boot:run &

# Open the shop
open http://localhost:8089
```

**Environment overrides** (all optional):

```bash
GATEWAY_URL=http://api-gateway:8080      # inside docker network
PRODUCT_QUERY_URL=http://product-query:8088
ADMIN_USER=admin
ADMIN_PASSWORD=admin123
ZIPKIN_ENDPOINT=http://zipkin:9411/api/v2/spans
```

For the docker profile: `SPRING_PROFILES_ACTIVE=docker`.

---

## 4. Demo flow — browse to checkout

```bash
# Seed a few products (through the gateway so outbox fires)
for p in \
  '{"name":"Blue Widget","description":"shiny blue","price":9.99,"quantityInStock":100,"category":"widgets","brand":"acme"}' \
  '{"name":"Red Widget","description":"compact red","price":14.50,"quantityInStock":50,"category":"widgets","brand":"acme"}' \
  '{"name":"Green Gadget","description":"small green","price":24.00,"quantityInStock":10,"category":"gadgets","brand":"widgetco"}' \
  '{"name":"Yellow Doohickey","description":"premium","price":79.00,"quantityInStock":5,"category":"doohickeys","brand":"premium-co"}'; do
  curl -s -u admin:admin123 -H "Content-Type: application/json" \
       -X POST http://localhost:8080/api/v1/products -d "$p" >/dev/null
done
```

Now click through:

1. `http://localhost:8089/` → featured products from ES.
2. **Products** nav → sidebar shows category/brand/price facets. Click "widgets" → URL gains `?category=widgets`.
3. Click any product → PDP. **Load twice** — second load is served from Redis
   (`product-service` logs show cache hit).
4. **Add to cart** → navbar badge updates (HTMX swap, no page refresh).
5. `/cart` → update qty, remove, clear. Totals re-compute server-side.
6. **Proceed to checkout** → review page shows the `Idempotency-Key` for this
   attempt.
7. **Place order** → `POST /api/v1/orders` through the gateway, redirect to
   `/orders/confirm?id=…`.
8. Confirm page auto-refreshes every 3s until the saga reaches `PAID` or
   `COMPLETED` (success) / `CANCELLED` (compensation).
9. **Double-submit test**: hit back, click Place Order again → same order
   returned (`Idempotency-Replay: true` header). Watch
   `/admin/sagas` to see one saga, not two.

---

## 5. Known limits + next steps

### Single-line checkout
`POST /api/v1/orders` accepts one `productId` + `quantity` today. The UI
submits the FIRST cart line; the rest sit in the cart silently.

**Fix path:**
1. Add `OrderLine` DTO and change the controller to accept a list.
2. `OrderService.create(List<OrderLine>)` creates N `OrderItem` rows + one
   outbox event with a list payload.
3. CheckoutController iterates `cart.lines()` and posts the full list.

### No auth on the UI
`SecurityConfig` is permit-all for demo convenience. To wire real auth:
1. Add `spring-boot-starter-oauth2-client`.
2. Configure `spring.security.oauth2.client.registration.auth-server.*` to
   point at `http://localhost:8095`.
3. Replace `permitAll()` with `anyRequest().authenticated()` on protected
   routes.
4. Forward the user's JWT to the gateway in `RestClientsConfig` instead of
   admin basic auth.

### Confirm page polls, doesn't push
Current confirm page uses `<meta refresh=3>`. Upgrade to SSE:
1. Point the browser at `GET http://notification:9999/notifications/stream/{orderId}`
   (built in Tier 4).
2. Replace meta-refresh with `new EventSource(…)` + DOM swap on
   `payment-completed` event.

### Cart doesn't survive restart
HttpSession is in-memory. For horizontal scale + persistence:
1. Add `spring-session-data-redis`.
2. `spring.session.store-type: redis`.
3. Cart becomes transparent — same code, Redis-backed sessions.

### No search bar interactivity
The top-nav search submits full-page. Easy upgrade: HTMX GET on `keyup` to
`/products/search-partial` returning just the result grid fragment.

---

## Interview talking point

> *"The storefront is a thin Thymeleaf layer that routes reads to the
> appropriate backend: Elasticsearch for listing and facets, SQL+Redis for
> product details, the gateway for writes. Checkout pins an Idempotency-Key
> in the user's session so a browser retry replays safely — the Tier 1 filter
> catches it and returns the stored response instead of creating a second
> order. The confirm page polls the order while the Kafka saga completes;
> you can watch the step history in /admin/sagas on the gateway. The cart
> lives in HttpSession — upgrade to Spring Session + Redis for horizontal
> scale; the UI code doesn't change. The whole thing exercises every tier
> on one checkout."*
