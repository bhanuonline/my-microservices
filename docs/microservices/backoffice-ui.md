# backoffice-ui — operator console

Server-rendered Thymeleaf admin console that frames every existing admin
endpoint into one navigation tree. No DB; reads live from the backing
services via their REST APIs.

- [1. Topology](#1-topology)
- [2. Pages built](#2-pages-built)
- [3. Running it](#3-running-it)
- [4. Common tasks](#4-common-tasks)
- [5. Next upgrades](#5-next-upgrades)

---

## 1. Topology

```
   Operator browser
        │ HTML
        ▼
  ┌──────────────────────────────────────────────────────────┐
  │ backoffice-ui  :8090                                     │
  │                                                          │
  │  DashboardController  → PrometheusClient + GatewayClient │
  │                        + OrderQueryClient + ProductQuery │
  │  OrdersController     → OrderQueryClient (ES)            │
  │                        + GatewayClient (saga/compensate) │
  │                        → browser → notification (SSE)    │
  │  ProductsController   → ProductQueryClient (ES listing)  │
  │                        + GatewayClient (CRUD writes)     │
  │  OperationsController → GatewayClient /admin/sagas /dlq  │
  │                        /flags                            │
  │  PlatformController   → external links to Grafana etc    │
  └──────────────────────────────────────────────────────────┘
        │                 │                 │                  │
        ▼                 ▼                 ▼                  ▼
   api-gateway       product-query     order-query        prometheus
     :8080             :8088 (ES)       :8086 (ES)         :9090
       │
       ▼
  product-service · order-service · payment-service · notification
```

**Why two reads?** Lists go to ES (fast, flexible) — point lookups + writes
go to the gateway so the authoritative service answers.

---

## 2. Pages built

| path | what it does | reads | writes |
|---|---|---|---|
| `/` | Dashboard: orders today, sagas in flight, DLQ count, products indexed, p95 | Prometheus, order-query, gateway | — |
| `/orders` | Orders list, filter by status/product, pagination | order-query (ES) | — |
| `/orders/{id}` | Order detail + saga timeline + compensate button | gateway (order + sagas) | gateway `/admin/sagas/{id}/compensate` |
| `/orders/live` | Live SSE feed per orderId | notification SSE | — |
| `/products` | Product catalog with facets, filter, search | product-query (ES) | — |
| `/products/new` | Create product form | — | gateway `POST /api/v1/products` |
| `/products/{id}` | Edit / delete product | gateway | gateway `PUT`/`DELETE /api/v1/products/{id}` |
| `/sagas` | All sagas with state filter | gateway `/admin/sagas` | — |
| `/sagas/{id}` | Saga detail + step history + compensate | gateway | gateway `/admin/sagas/{id}/compensate` |
| `/dlq` | Poison messages, status filter | gateway `/admin/dlq` | — |
| `/dlq/{id}` | DLQ detail + replay/ack buttons | gateway | gateway `/admin/dlq/{id}/{replay,ack}` |
| `/flags` | List flags + per-flag toggle + upsert form | gateway `/admin/flags` | gateway `PUT`/`POST` |
| `/observability` | Link grid to Grafana/Zipkin/Prometheus | — | — |
| `/audit` | Stub for a future admin audit log | — | — |

---

## 3. Running it

```bash
# Comes up alongside shop-ui in wave 5 of start-stack.sh
./start-stack.sh

# Or just this module from source
cd backoffice-ui && mvn spring-boot:run

# Opens on
open http://localhost:8090
```

**Environment overrides** (optional):

```bash
GATEWAY_URL=http://api-gateway:8080
PRODUCT_QUERY_URL=http://product-query:8088
ORDER_QUERY_URL=http://order-query:8086
PROMETHEUS_URL=http://prometheus:9090
GRAFANA_URL=http://grafana:3000
ZIPKIN_URL=http://zipkin:9411
NOTIFICATION_URL=http://notification:8099
ADMIN_USER=admin
ADMIN_PASSWORD=admin123
```

For the docker profile: `SPRING_PROFILES_ACTIVE=docker`.

---

## 4. Common tasks

**See all orders placed in the last hour.**
Dashboard → scroll "Recent orders"; or `/orders` with status filter.

**A customer rings to cancel order o-abc-123.**
`/orders/o-abc-123` → scroll to saga timeline → "Compensate" button → fires
`POST /admin/sagas/<sagaId>/compensate` which emits a refund command. Order
transitions to CANCELLED via the saga. Watch `/dlq` for any downstream errors.

**Add a new product to the catalog.**
`/products/new` → fill form → Save. The write hits `product-service` which
inserts into MySQL + outbox. OutboxRelay publishes to Kafka; the `product-query`
projector upserts into Elasticsearch within ~1s. Refresh `/products` to see it.

**A poison message landed on error.payment.commands.***
`/dlq?status=NEW` → open row → inspect payload + exception. Click Replay to
republish to the original topic (consumer idempotency guards against dupes),
or Ack to drop + keep for audit.

**Enable a new feature flag for 25% of users.**
`/flags` → fill in key `new-payment-provider`, enabled=true, percentage=25,
Save. The flag store propagates (5s TTL) and the FeatureFlagAspect in any
service with `feature-flags.enabled=true` honors it for sticky 25% rollout.

**Watch the saga progress live during a demo.**
Open `/orders/live` in one tab, `/shop-ui` in another. Watch events stream
into the feed as the saga advances.

---

## 5. Next upgrades

| upgrade | why |
|---|---|
| Replace permit-all with OAuth2 + `ROLE_ADMIN` | Real auth; backoffice is privileged |
| Emit `admin.action` events on every mutation + persist in ES | Powers the audit page |
| Bulk CSV upload on product manager | Operator UX — common ask |
| Per-tenant filter on orders/products | Multi-tenancy demo (Tier 4 bits exist) |
| Embed Grafana panels via iframe into dashboard tiles | Rich metrics in context |
| Replace `<meta refresh>` with HTMX polling | Smoother refresh |

---

## Interview talking point

> *"backoffice-ui is a single Thymeleaf app that composes every admin endpoint
> we built across the stack. It uses the ES CQRS read side for list views
> (orders, products) and the gateway for authoritative point lookups + writes.
> The dashboard pulls KPIs from Prometheus so it also serves as a reduced-
> cardinality operational panel. The orders detail page proves the saga
> story end-to-end: it shows the order's current state, the saga's step
> history, and a compensate button that fires a refund command through the
> same orchestrator the shop uses — operator-triggered compensation is just
> an admin-authored call into the saga framework."*
