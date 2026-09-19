# 00 — Architecture overview

## System diagram

```
                                    ┌────────────────────┐
                                    │  Client (curl/UI)  │
                                    └──────────┬─────────┘
                                               │ JWT
                                               ▼
                                 ┌──────────────────────────┐
                                 │  api-gateway (:8080)     │
                                 │  reactive, Netty         │
                                 │  - validates JWT         │
                                 │  - discovers via Eureka  │
                                 │  - routes /api/v1/*      │
                                 │  - TokenRelay filter     │
                                 └──────┬────────────┬──────┘
                                        │            │
                    ┌───────────────────┼────────────┼───────────────────┐
                    ▼                   ▼            ▼                   ▼
        ┌──────────────────┐  ┌──────────────────┐ ┌──────────────────┐  Eureka
        │ user-service     │  │ product-service  │ │ order-service    │  (:8761)
        │ (:8081)          │  │ (:8082)          │ │ (:8083)          │
        │ MySQL userdb     │  │ MySQL productdb  │ │ H2 orderdb       │
        │ outbox pattern   │  │                  │ │ saga orchestrator│
        └────────┬─────────┘  └────────▲─────────┘ └─────┬────┬──────┘
                 │                     │                 │    │
                 │            Feign lb://product-service │    │
                 │                     │                 ▼    │
                 │            ┌────────┴──────────────────┐   │
                 │            │  Resilience4j on the call │   │
                 │            │  Retry → CB → Bulkhead    │   │
                 │            └───────────────────────────┘   │
                 │                                            │
                 │                                            │
                 ▼                                            ▼
        ┌──────────────────────────────────────────────────────────┐
        │  Kafka (:9092)                                           │
        │                                                          │
        │  Topics:                                                 │
        │    user.registered           order.created               │
        │    payment.completed         payment.failed              │
        │    payment.commands          payment.replies             │
        │    notification.commands     notification.replies        │
        │    payment.commands.refund   *.DLT (dead-letter)         │
        └──────┬────────────────────────────────────────┬──────────┘
               │                                        │
               ▼                                        ▼
        ┌──────────────────┐                   ┌──────────────────┐
        │ notification     │                   │ payment-service  │
        │ (no HTTP port)   │                   │ (:8091)          │
        │ Cloud Stream     │                   │ Cloud Stream     │
        │ - user event     │                   │ - order events   │
        │ - saga commands  │                   │ - saga commands  │
        └──────────────────┘                   └──────────────────┘

        ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
        │ auth-server      │  │ resource-server  │  │ zipkin (:9411)   │
        │ (:8095)          │  │ (:8096)          │  │ trace collector  │
        │ OAuth2 provider  │  │ JWT validator    │  │                  │
        │ MySQL authdb     │  │                  │  │                  │
        └──────────────────┘  └──────────────────┘  └──────────────────┘
```

## Service inventory

| Service | Port | Purpose | Key patterns |
|---|---|---|---|
| eureka-server | 8761 | Service registry | — |
| api-gateway | 8080 | Edge router, JWT validation | Reactive, TokenRelay |
| auth-server | 8095 | OAuth2 authorization server | Spring Authorization Server |
| resource-server | 8096 | Example JWT-protected API | Standalone JWT validation |
| user-service | 8081 | User management | Outbox pattern, JWT |
| product-service | 8082 | Product catalog | JWT |
| order-service | 8083 | Order + saga orchestrator | Circuit breaker, retry, bulkhead, saga, JWT |
| payment-service | 8091 | Payment processing | Cloud Stream consumer + reply |
| notification | (n/a) | Notifications | Cloud Stream consumer + reply |

## Infrastructure

| Component | Port | Purpose |
|---|---|---|
| mysql-user | 3307 | user-service DB |
| mysql-product | 3308 | product-service DB |
| mysql-auth | 3309 | auth-server DB |
| kafka | 9092 | Message broker |
| zipkin | 9411 | Distributed tracing |

## Cross-cutting concerns

- **Tracing:** Micrometer Tracing → Zipkin (100% sampling in dev).
- **Logging:** JSON via Logstash encoder; pattern includes `traceId, spanId, correlationId`.
- **Correlation IDs:** `X-Correlation-Id` header, generated by gateway or accepted from client.
- **Auth:** JWT issued by auth-server, validated at gateway + every downstream service.
- **Resilience:** Circuit breaker + retry + bulkhead on outbound Feign calls.
- **Reliable messaging:** Outbox pattern (user-service), DLQ with 3 retries + `.DLT` topics.

## What each request path teaches

| Path | Patterns exercised |
|---|---|
| `POST /api/v1/users` | Outbox, event publish, JWT |
| `POST /api/v1/orders` | Circuit breaker, retry, bulkhead, saga orchestration, JWT |
| `GET /api/v1/orders/{id}` | Persistence, JWT |
| Reverse Kafka flow | Choreography, idempotent consumer, state machine |

## Recent changes / notable decisions

Track here as you go. Example format:

```
2026-09-19 — Standardized on own auth-server (Option A); deleted jwtAuthApp, spring-security-apps, client-app from reactor.
2026-09-19 — Added outbox pattern to user-service; payment/order services still direct-publish (dual-write risk noted).
2026-09-19 — Chose H2 for order-service persistence (no infra); swap for MySQL later.
```
