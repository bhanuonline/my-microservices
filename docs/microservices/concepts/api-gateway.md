# API Gateway — Spring Cloud Gateway (reactive)

## The problem it solves

- Single entry point (auth, rate limit, routing) instead of every client knowing every backend URL.
- Cross-cutting concerns centralized.

## How it works

```
<!-- ASCII: Client → Gateway → discovery lookup → target service -->
```

## How it's wired in THIS project

- Module: `api-gateway`
- Port: 8080
- Web stack: **reactive** (Netty). Must NOT pull in `spring-boot-starter-web`.
- Config: `api-gateway/src/main/resources/application.yml`
- Key config keys:
  - `spring.cloud.gateway.discovery.locator.enabled: true` — auto-routes per Eureka service
  - `spring.cloud.gateway.routes` — explicit routes with predicates + filters
  - `spring.cloud.gateway.default-filters: [TokenRelay]` — forwards JWT downstream
  - `spring.security.oauth2.resourceserver.jwt.issuer-uri` — for JWT validation

## Route table

| Path predicate | Target |
|---|---|
| `/api/v1/users/**` | `lb://user-service` |
| `/api/v1/products/**` | `lb://product-service` |
| `/api/v1/orders/**` | `lb://order-service` |

## How to observe it running

```bash
# Live route table
curl -s http://localhost:8080/actuator/gateway/routes | jq

# Health
curl -s http://localhost:8080/actuator/health | jq
```

## Common failure modes

- Netty + Tomcat both on classpath → gateway starts as MVC, routes fail. Fix: exclude `spring-boot-starter-web` from Eureka client (already done in pom).
- Route defined but path predicate wrong (case, prefix). Use `/actuator/gateway/routes` to see what's live.
- JWT validation fails → 401. Confirm issuer-uri matches auth-server exactly.

## Interview talking points

- Why reactive: gateway is I/O-bound; Netty handles many concurrent connections cheaply.
- Filters vs predicates: predicates decide match; filters modify request/response.
- Common built-in filters: `StripPrefix`, `AddRequestHeader`, `RateLimiter`, `Retry`, `TokenRelay`.
- Gateway vs BFF (Backend-For-Frontend) pattern.
