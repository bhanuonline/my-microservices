# nano-debug-flow — Postman collection for nano mode

A single importable collection + environment that walks you through the
nano stack end-to-end — happy path, validation failures, 401, 404, 409,
and the "payment-service down" timeout scenario — plus a dedicated
**Debug tour** folder that tells you exactly where to drop a breakpoint
for each request, and a **If it goes wrong** folder for diagnostics.

Files in this folder:

```
nano-debug-flow.postman_collection.json    ← the collection (9 folders, ~40 requests)
nano-debug-flow.postman_environment.json   ← variables (ports, creds, chained ids)
nano-debug-flow.README.md                  ← this file
```

## 1. Prerequisites

| Need | Check with |
|---|---|
| Nano stack up (7 containers) | `make verify` or `docker compose ps` |
| At least user-service running on your laptop | `curl http://localhost:8081/actuator/health` → `UP` |
| For saga tests: product + order + payment | `make debug-saga` |
| For full event flow: + notification | `make debug-all` |

## 2. Import into Postman

1. Open Postman → **File → Import** (or the top-left Import button)
2. Drop both files on the dialog:
   - `nano-debug-flow.postman_collection.json`
   - `nano-debug-flow.postman_environment.json`
3. Click Import
4. Top-right environment dropdown → select **nano-debug (local)**

## 3. Run order (first time)

The requests are organized to be run top-to-bottom. Each one assumes
the previous worked.

```
0 · Preflight             ← verify infra is up
    ├─ Gateway /actuator/health          200
    ├─ Eureka — list registered apps     USER-SERVICE in list
    ├─ Eureka — user-service instance    status: UP, real LAN IP
    └─ Config-server /actuator/health    200

1 · Auth — get a JWT
    ├─ Get token (M2M · client_credentials) ✅   ← stores {{access_token}}
    ├─ Get token — wrong secret ❌ 401
    ├─ Get token — unknown client ❌ 401
    └─ Call /api/v1/users WITHOUT token ❌ 401

2 · User service
    ├─ List users ✅ 200
    ├─ Create user ✅ 201                 ← stores {{last_user_id}}
    ├─ Create user — bad email ❌ 400
    ├─ Create user — short password ❌ 400
    ├─ Create user — missing name ❌ 400
    ├─ Get user by id ✅ 200
    ├─ Get user by id — not found ❌ 404
    └─ List users — DIRECT (bypass gateway) ✅

3 · Product service                        ← prereq: make debug-product
    ├─ List / Create / Get / Availability / 404 / 400

4 · Order service                          ← prereq: make debug-order (+ product)
    ├─ Create order ✅ 201                 ← stores {{last_order_id}}
    ├─ Create order — unavailable product ❌ 409
    └─ Get order ✅ 200

5 · Checkout saga                          ← prereq: make debug-saga
    ├─ Checkout (mock) ✅ 201              ← stores {{last_saga_id}}
    ├─ Checkout — product unavailable ❌ 409
    ├─ Checkout — invalid provider ❌ 400
    ├─ Checkout (stripe) — payment-service DOWN scenario
    └─ Get order (poll saga state)

6 · Payment service                        ← prereq: make debug-payment
    ├─ Initiate payment (hosted) ✅
    ├─ Get payment by order ✅
    └─ Webhook — mock provider

7 · Notification                           ← prereq: make debug-notification
    └─ Send notification ✅ 202

8 · Debug tour (where to breakpoint)       ← curated requests + file:method map
    ├─ A. UserController.getAll
    ├─ B. UserController.register (tx + outbox)
    ├─ C. CheckoutController.checkout (multi-service)
    └─ D. ProductController.getAvailability

9 · If it goes wrong (diagnostics)
    ├─ Service UP? /actuator/health (direct bypass)
    ├─ DB UP? /actuator/health/db
    ├─ Which URL is the service using? /actuator/env
    ├─ Gateway routes dump
    └─ /actuator/info — which commit is running?
```

## 4. How chaining works

The collection sets environment variables after successful creates. For
example, `2 · User service → Create user` stores the new id as
`{{last_user_id}}`, and `Get user by id` interpolates it into the URL
automatically. If you run requests out of order, you may see literal
`{{last_user_id}}` in the URL — go back and run the Create first.

Chained variables:

| Variable | Set by |
|---|---|
| `{{access_token}}` | `1 · Auth → Get token (M2M)` |
| `{{last_user_id}}` | `2 · User → Create user` |
| `{{last_product_id}}` | `3 · Product → Create product` |
| `{{last_order_id}}` | `4 · Order → Create order` **or** `5 · Checkout` |
| `{{last_saga_id}}` | `5 · Checkout` |

## 5. Auth model (recap)

```
┌─────────────────────────────────────────────────────────────────┐
│  Who can call what                                              │
│                                                                 │
│  /api/v1/**              → requires Bearer JWT (M2M token OK)   │
│  /actuator/health        → public (no auth)                     │
│  /actuator/gateway/**    → admin (JWT w/ admin scope OR Basic)  │
│  /webhooks/**            → public (providers don't carry JWT)   │
│  /api/v1/notifications   → public (direct on :8099 only)        │
└─────────────────────────────────────────────────────────────────┘
```

The collection sets a **Bearer token at the collection level**, picking
up `{{access_token}}`. Individual requests override to Basic (for
`/oauth2/token`) or no-auth (for `/webhooks/**`, actuator health, etc.)
as needed.

## 6. When something fails — matrix

| Symptom in Postman | Likely cause | Diagnostic request to run |
|---|---|---|
| **401** on `/api/v1/*` | No token OR expired (default 5 min) | Re-run `1 · Auth → Get token` |
| **404** on `/api/v1/*` through gateway | Route not registered | `9 · Gateway routes dump` |
| **503** on `/api/v1/*` through gateway | Service down / not in Eureka | `0 · Eureka — list apps` + `9 · Service UP?` |
| **409** on `/orders` or `/checkout` | Product not AVAILABLE | `3 · Check availability` |
| **500** on `/users` POST | DB connection broken | `9 · DB UP?` + `9 · Which URL?` |
| Checkout stuck at `PENDING` | payment-service not running / Kafka lag | `make kafka-group-describe GROUP=order-service` |
| curl works but Postman 401s | Environment not selected | top-right → select `nano-debug (local)` |

## 7. Running the whole thing via Newman (optional CI)

```bash
# One-time: install newman
npm i -g newman

# Run the whole collection, happy path only
newman run docs/microservices/postman/nano-debug-flow.postman_collection.json \
  -e docs/microservices/postman/nano-debug-flow.postman_environment.json \
  --folder "0 · Preflight" \
  --folder "1 · Auth — get a JWT" \
  --folder "2 · User service"
```

Great for a nightly smoke test — fails loudly if any happy-path call
breaks after a refactor.
