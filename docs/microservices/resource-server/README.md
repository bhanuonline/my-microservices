# resource-server

A **minimal OAuth2 resource server** — the smallest possible Spring Security
configuration that validates JWTs issued by auth-server and serves data to
the authenticated caller.

This module exists as a **teaching reference**. Business services (user-service,
product-service, order-service) use the same pattern with real domain data.
Keeping resource-server small lets anyone see the pattern in isolation.

## Mental model

```
┌──────────┐         1. token request          ┌───────────────┐
│  Client  │ ────────────────────────────────▶ │ auth-server   │
│  (you)   │ ◀──────────────────────────────── │   :8095       │
└──────────┘         2. access_token           └───────────────┘
      │
      │ 3. GET /api/hello
      │    Authorization: Bearer eyJhbG...
      ▼
┌────────────────────────────────────────────────────────────┐
│ resource-server :8096                                      │
│                                                            │
│  Spring Security filter chain:                             │
│    ├─ fetches JWKS from auth-server at startup            │
│    ├─ validates signature + exp + iss on every request    │
│    ├─ extracts scope → SCOPE_*, roles → ROLE_* authorities│
│    └─ @PreAuthorize gate on the method                    │
│                                                            │
│  Observability (same as every other service):             │
│    - JSON logs with traceId/spanId/correlationId          │
│    - /actuator/health, /info, /prometheus, /metrics       │
│    - Zipkin spans for every request                       │
└────────────────────────────────────────────────────────────┘
```

## Endpoints

| Method | Path | Required | Purpose |
|---|---|---|---|
| GET | `/api/hello` | `SCOPE_read` | Greeting using the JWT sub |
| POST | `/api/echo` | `SCOPE_write` | Echoes JSON body + metadata |
| GET | `/api/admin` | `ROLE_ADMIN` | Admin-only ping |
| GET | `/api/me` | authenticated | Full JWT claim diagnostic |

## Quick start

```bash
# Boot auth-server + Zipkin + Prometheus first (see docs/microservices/observability-multi-service.md)
mvn -pl auth-server spring-boot:run
docker-compose -f api-gateway/docker-compose.observability.yml up -d

# Boot resource-server
mvn -pl resource-server spring-boot:run

# Fetch a token
TOKEN=$(curl -s -u admin:admin123 \
  -d "grant_type=client_credentials&scope=read" \
  http://localhost:9010/oauth2/token | jq -r .access_token)

# Call it
curl -H "Authorization: Bearer $TOKEN" http://localhost:8096/api/hello
# → "Hello, admin"

# See full claims
curl -H "Authorization: Bearer $TOKEN" http://localhost:8096/api/me | jq
```

## Documentation

Build-up docs (what changed, why, how it works):

- [tier-0-bugfixes.md](tier-0-bugfixes.md) — three bug fixes done first
- [tier-1-observability.md](tier-1-observability.md) — JSON logs + Zipkin + Prometheus + /actuator/info rollout
- [tier-3-oauth2-depth.md](tier-3-oauth2-depth.md) — scope + role authorization, custom claims converter, /api/me
- [tier-4b-tests-docker.md](tier-4b-tests-docker.md) — test strategy + Dockerfile with Maven cache

## File tree

```
resource-server/
├── pom.xml                 (slimmed, inherits plugin config from parent)
├── Dockerfile              (multi-stage with BuildKit cache mount)
└── src/
    ├── main/
    │   ├── java/com/example/resourceserver/
    │   │   ├── ResourceServerApplication.java   (Spring Boot entry)
    │   │   ├── SecurityConfig.java              (JWT + method security + custom converter)
    │   │   └── ApiController.java               (/api/hello /echo /admin /me)
    │   └── resources/
    │       ├── application.yml                  (replaces old .properties)
    │       └── logback-spring.xml               (3-line include of shared config)
    └── test/
        └── java/com/example/resourceserver/
            ├── CombinedAuthoritiesConverterTest.java   (fast unit tests)
            └── ApiControllerTest.java                  (@SpringBootTest with mock JWTs)
```

## Why keep this as a stub?

**user-service, product-service, order-service all demonstrate the same
pattern with real data.** The temptation is to grow resource-server into a
real "documents" or "files" service, but that duplicates what the business
services already teach.

Keeping resource-server minimal preserves its value as a **reference** —
anyone reading the codebase can see the *smallest viable OAuth2 resource
server* without wading through domain code.

If you ever need a real resource that order-service calls through, add it to
user-service (as another endpoint) or spin up a new service with a purpose.
Don't bloat resource-server.

## Interview soundbite

> resource-server is a reference implementation of the OAuth2 resource-server
> pattern — JWT validation via auth-server's JWKS endpoint, method-level
> scope + role enforcement, nothing else. The business services use the same
> pattern with real domain data. I kept resource-server small so the pattern
> is visible in isolation.
