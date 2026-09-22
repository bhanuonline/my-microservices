# Postman collection

Import `my-microservices.postman_collection.json` into Postman.

## How to import

1. Postman → File → Import
2. Drag the `.json` file
3. New collection "my-microservices" appears in the left sidebar

## Structure

```
my-microservices/
├── 0 — Infrastructure & discovery    Eureka, Zipkin
├── 1 — Auth-server                    OIDC config, JWKS, token
├── 2 — User-service (direct)          register, list, get, validation
├── 3 — Product-service (direct)       CRUD + availability check
├── 4 — Order-service (direct)         saga triggers, get, validation
├── 5 — Through Gateway (JWT required) same endpoints but via :8080 with JWT
└── 6 — Actuator / debug               health, gateway routes, CB events
```

## Collection variables

Edit the collection (right-click → Edit → Variables tab). Ports pre-populated:

| Variable | Default | Purpose |
|---|---|---|
| gateway | `http://localhost:8080` | API Gateway |
| user_svc | `http://localhost:8081` | Direct hit user-service |
| product_svc | `http://localhost:8082` | Direct hit product-service |
| order_svc | `http://localhost:8083` | Direct hit order-service |
| payment_svc | `http://localhost:8091` | (no HTTP endpoints yet, reserved) |
| auth_svc | `http://localhost:8095` | Auth-server |
| resource_svc | `http://localhost:8096` | Resource-server |
| eureka | `http://localhost:8761` | Eureka |
| zipkin | `http://localhost:9411` | Zipkin |
| jwt | `` (empty) | Paste JWT here after obtaining one |

## Typical workflow

1. **Bring stack up** (see [01-startup-runbook.md](../01-startup-runbook.md))
2. **Run "Eureka — list registered apps"** — confirm services registered
3. **Run "Register user"** — happy path, watch outbox drain + notification consume
4. **Run "Create order — happy path"** — watch saga complete
5. **Run "Create order — payment declined"** — watch saga fail
6. **Run "Order — health"** — inspect circuit breaker state

## Getting a JWT (for gateway-routed requests)

The auth-server needs a registered client before you can issue tokens. Once you do:

1. Update Basic Auth in "Token — client_credentials" (username = client id, password = secret)
2. Send it
3. Copy `access_token` from response
4. Paste into collection variable `jwt` (Edit collection → Variables → save)
5. Now "Through Gateway" folder requests use it via `Authorization: Bearer {{jwt}}`

## Notes

- **Direct-hit folders (2, 3, 4)** bypass JWT — services allow all requests when hit directly (unless SecurityConfig says otherwise). Use for iterating on business logic.
- **Gateway folder (5)** exercises the full auth stack.
- **Order-service uses H2 in-memory** — DB wipes on restart. Order IDs from previous runs won't be there.
