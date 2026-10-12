# Spring Features — Living Reference

> This file is updated every session. Each section covers a Spring feature used in this project:
> what it does, key annotations, where it lives in the codebase, and how it fits the bigger picture.

---

## Table of Contents

1. [Spring Boot Core](#1-spring-boot-core)
2. [Spring Data JPA](#2-spring-data-jpa)
3. [Spring Data Redis](#3-spring-data-redis)
4. [Spring Security + OAuth2](#4-spring-security--oauth2)
5. [Spring Cloud — Eureka (Service Discovery)](#5-spring-cloud--eureka-service-discovery)
6. [Spring Cloud — API Gateway](#6-spring-cloud--api-gateway)
7. [Spring Cloud — Config Server](#7-spring-cloud--config-server)
8. [Spring Cloud — OpenFeign](#8-spring-cloud--openfeign)
9. [Spring Cloud — Load Balancer](#9-spring-cloud--load-balancer)
10. [Spring Cloud — Circuit Breaker (Resilience4j)](#10-spring-cloud--circuit-breaker-resilience4j)
11. [Spring Cloud Stream + Kafka](#11-spring-cloud-stream--kafka)
12. [Spring AOP](#12-spring-aop)
13. [Spring Cache](#13-spring-cache)
14. [Spring Retry](#14-spring-retry)
15. [Spring Actuator](#15-spring-actuator)
16. [Spring GraphQL](#16-spring-graphql)
17. [Spring WebSocket](#17-spring-websocket)
18. [Spring HATEOAS](#18-spring-hateoas)
19. [Spring State Machine](#19-spring-state-machine)
20. [Spring Vault](#20-spring-vault)
21. [Spring WebFlux (Reactive)](#21-spring-webflux-reactive)

---

## 1. Spring Boot Core

**What it does:** Auto-configures the whole application. You don't wire beans by hand — Boot detects what's on the classpath and sets it up.

**Key annotations:**

| Annotation | Purpose |
|---|---|
| `@SpringBootApplication` | Combines `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan` |
| `@RestController` | Marks a class as HTTP controller, returns JSON by default |
| `@Service` | Marks business logic layer |
| `@Repository` | Marks data access layer |
| `@Component` | Generic Spring-managed bean |
| `@Configuration` | Marks a class that declares `@Bean` methods |

**Where in this project:**
- Every service has a main class with `@SpringBootApplication`
- Parent `pom.xml` uses `spring-boot-starter-parent` as the base

**Mental model:**
```
You write:   @SpringBootApplication
Boot does:   scans packages → finds @Component/@Service/@Repository
             → wires dependencies → starts embedded Tomcat
             → applies auto-configs based on classpath
```

---

## 2. Spring Data JPA

**What it does:** Maps Java objects to database tables. Write an interface, Spring generates the SQL.

**Key annotations:**

| Annotation | Purpose |
|---|---|
| `@Entity` | Marks a class as a DB table |
| `@Id` + `@GeneratedValue` | Primary key |
| `@Column` | Maps field to column |
| `@OneToMany` / `@ManyToOne` | Relationships |
| `@Transactional` | Wraps method in a DB transaction |

**Key interface:** `JpaRepository<Entity, ID>` — gives you `save()`, `findById()`, `findAll()`, etc. for free.

**Where in this project:**
- `order-service`, `user-service`, `product-service` all use JPA for their domain models

**Mental model:**
```
@Entity Order  ←→  orders table in DB
       │
JpaRepository  →  Spring generates:  SELECT * FROM orders WHERE id=?
                                      INSERT INTO orders ...
```

---

## 3. Spring Data Redis

**What it does:** Connects to Redis for caching or session storage. Two flavors: blocking (`spring-data-redis`) and reactive (`spring-data-redis-reactive`).

**Key annotations:**

| Annotation | Purpose |
|---|---|
| `@Cacheable` | Cache method result |
| `@CacheEvict` | Remove from cache |
| `@EnableCaching` | Turn on cache support |

**Where in this project:** `user-service`, `order-service`

**Mental model:**
```
Request → @Cacheable method
              │
         cache hit? → return cached value (no DB call)
         cache miss? → call method → store in Redis → return
```

---

## 4. Spring Security + OAuth2

**What it does:** Protects endpoints. This project uses the full OAuth2 stack.

**Three roles in this project:**

```
┌─────────────────────────────────────────────────────┐
│  auth-server       → issues JWT tokens (Auth Server) │
│  resource-server   → validates JWT (Resource Server) │
│  angle-app / ui    → logs in via OAuth2 (Client)     │
└─────────────────────────────────────────────────────┘
```

**Key annotations:**

| Annotation | Purpose |
|---|---|
| `@EnableWebSecurity` | Turn on Security |
| `@PreAuthorize("hasRole('ADMIN')")` | Method-level auth |
| `@AuthenticationPrincipal` | Inject current user into controller |

**Dependencies used:**
- `spring-security-oauth2-authorization-server` — auth-server
- `spring-boot-starter-oauth2-resource-server` — order-service, user-service, product-service
- `spring-boot-starter-oauth2-client` — angle-app

**Flow:**
```
Client (angle-app)
   │  POST /oauth2/token
   ▼
Auth Server  ──── issues JWT ────►  Client stores token
                                        │
                                        │  GET /api/v1/orders  + Bearer JWT
                                        ▼
                                   API Gateway → Resource Server validates JWT
```

---

## 5. Spring Cloud — Eureka (Service Discovery)

**What it does:** Services register themselves; other services look them up by name instead of hardcoded URLs.

**Key annotations:**

| Annotation | Purpose |
|---|---|
| `@EnableEurekaServer` | Turn a service into a registry |
| `@EnableDiscoveryClient` | Register this service with Eureka |

**Where in this project:**
- `infra/eureka-server` — the registry
- All other services auto-register via `spring-cloud-starter-netflix-eureka-client`

**Mental model:**
```
order-service starts → registers as "order-service" at eureka-server:8761
payment-service starts → registers as "payment-service"

Feign call:  @FeignClient(name = "payment-service")
                │
                ▼
          Eureka lookup → returns actual IP:port → HTTP call
```

---

## 6. Spring Cloud — API Gateway

**What it does:** Single entry point for all client requests. Routes, filters, rate limits, circuit breaks.

**Where in this project:** `infra/api-gateway` (uses WebFlux/reactive)

**Key config pattern (application.yml):**
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: order-service
          uri: lb://order-service       # lb:// = load-balanced via Eureka
          predicates:
            - Path=/api/v1/orders/**
          filters:
            - CircuitBreaker=...
            - RateLimiter=...
```

**What's implemented in this project:**
- Custom filters (logging, API key auth, body logging)
- Rate limiting, circuit breaker, bulkhead
- Canary routing, CORS, WebSocket proxying
- Dynamic routes via pub/sub

---

## 7. Spring Cloud — Config Server

**What it does:** Centralised configuration. All services fetch their `application.yml` from one place instead of bundling config inside each jar.

**Where in this project:** `infra/config-server`

**Mental model:**
```
config-server  reads  git-repo/order-service.yml
      │
      │  GET /order-service/default
      ▼
order-service  (at startup, before app context loads)
```

**Key annotation:** `@EnableConfigServer` on the config-server main class.

---

## 8. Spring Cloud — OpenFeign

**What it does:** Write an interface, Spring generates the HTTP client. No `RestTemplate` boilerplate.

**Key annotations:**

| Annotation | Purpose |
|---|---|
| `@FeignClient(name="service-name")` | Declare a client; name = Eureka service name |
| `@GetMapping`, `@PostMapping` | Map to HTTP methods |
| `@PathVariable`, `@RequestBody` | Parameters |

**Where in this project:**
- `order-service`: `PaymentClient`, `ProductClient`
- `user-service`: `ProductService` (calls product-service)

**Example from this project:**
```java
@FeignClient(name = "payment-service")   // resolves via Eureka
public interface PaymentClient {
    @PostMapping("/api/v1/payments/initiate")
    InitiateResponse initiate(@RequestBody InitiateRequest req);
}
```

---

## 9. Spring Cloud — Load Balancer

**What it does:** When Feign calls `lb://payment-service`, Load Balancer picks which instance to call (round-robin by default).

**Where in this project:** `spring-cloud-starter-loadbalancer` in order-service, user-service.

**Mental model:**
```
Feign → lb://payment-service
              │
        Load Balancer
              │
     ┌────────┴────────┐
  instance-1        instance-2   (Eureka returned both)
  :8081              :8082
```

---

## 10. Spring Cloud — Circuit Breaker (Resilience4j)

**What it does:** Stops calling a failing service. After N failures, the circuit "opens" and calls are short-circuited to a fallback.

**Key annotations (Resilience4j):**

| Annotation | Purpose |
|---|---|
| `@CircuitBreaker(name="x", fallbackMethod="f")` | Open circuit on failures |
| `@Retry(name="x", fallbackMethod="f")` | Retry on failure |
| `@Bulkhead(name="x")` | Limit concurrent calls |

**Where in this project:** `order-service/ProductService.java`

```java
@Retry(name = "productClient", fallbackMethod = "checkFallback")
@CircuitBreaker(name = "productClient", fallbackMethod = "checkFallback")
@Bulkhead(name = "productClient", fallbackMethod = "checkFallback")
public String checkAvailability(Long productId) {
    return productClient.checkProductAvailability(productId);
}
```

**State machine:**
```
CLOSED (normal)
  │ failure rate > threshold
  ▼
OPEN (calls go to fallback immediately)
  │ after wait duration
  ▼
HALF-OPEN (test one call)
  │ success → CLOSED
  │ fail    → OPEN again
```

---

## 11. Spring Cloud Stream + Kafka

**What it does:** Abstraction over Kafka (or RabbitMQ). You write producer/consumer code against a Spring interface; the binder handles the actual Kafka plumbing.

**Key concepts:**

| Term | Meaning |
|---|---|
| Supplier | Producer (publishes messages) |
| Consumer | Consumer (receives messages) |
| Function | Both producer + consumer (transform) |
| Binder | The actual broker adapter (Kafka binder here) |

**Where in this project:** `order-service`, `payment-service`, `notification-service`

**Flow:**
```
order-service                    payment-service
    │                                 │
    │  publish OrderCreatedEvent      │
    │ ──────────────────────────────► │  consume → process payment
    │                                 │
    │  ◄───────────────────────────── │  publish PaymentSucceededEvent
    │  consume → update order status  │
```

---

## 12. Spring AOP

**What it does:** Apply cross-cutting logic (logging, security, feature flags) to methods without touching the business code.

**Key annotations:**

| Annotation | Purpose |
|---|---|
| `@Aspect` | Marks the class as an aspect |
| `@Around` | Wraps method — can skip, modify, or replace execution |
| `@Before` | Runs before method |
| `@After` | Runs after method (always) |
| `@AfterReturning` | Runs after successful return |
| `@AfterThrowing` | Runs after exception |
| `@Pointcut` | Reusable expression for where to apply |

**Where in this project:**

1. `common-lib/FeatureFlagAspect` — `@Around` intercepts `@FeatureEnabled` methods, skips them if flag is off
2. `spring-security-apps/ControllerLoggerAspect` — `@Before` logs every controller hit

**Mental model:**
```
Real method: checkAvailability()
                │
         AOP Proxy wraps it
                │
         @Around fires
           ├── flag disabled? → return default (method NEVER runs)
           └── flag enabled?  → pjp.proceed() → method runs
```

---

## 13. Spring Cache

**What it does:** Transparent method-level caching. First call hits the method; subsequent calls return the cached result.

**Key annotations:**

| Annotation | Purpose |
|---|---|
| `@EnableCaching` | Turn on caching |
| `@Cacheable("name")` | Cache the return value |
| `@CacheEvict("name")` | Remove entry from cache |
| `@CachePut("name")` | Always run method, update cache |

**Backed by:** Redis (`spring-boot-starter-data-redis`) in this project.

**Where in this project:** `order-service`, `user-service`

---

## 14. Spring Retry

**What it does:** Automatically retry a method when it throws an exception. Supports exponential backoff and a fallback (`@Recover`) after all attempts fail.

**Added:** 2026-10-12 — implemented in `order-service` for the `PaymentClient` Feign call.

**Key annotations:**

| Annotation | Purpose |
|---|---|
| `@EnableRetry` | Enable Spring Retry (on main class) |
| `@Retryable` | Retry this method on exception |
| `@Recover` | Fallback called after all retries exhausted |
| `@Backoff` | Configure delay between retries |

**Where in this project:** `order-service/service/PaymentService.java`

```java
@Retryable(
    retryFor = RuntimeException.class,
    maxAttempts = 3,
    backoff = @Backoff(delay = 500, multiplier = 2)  // 500ms, 1000ms
)
public InitiateResponse initiate(InitiateRequest req) { ... }

@Recover
public InitiateResponse recover(RuntimeException e, InitiateRequest req) {
    // called only after 3 failures
    return null;
}
```

**How it works — timeline:**
```
call initiate()
  │
  ├── attempt 1 → RuntimeException → wait 500ms
  ├── attempt 2 → RuntimeException → wait 1000ms
  ├── attempt 3 → RuntimeException
  │
  └── @Recover fires → return null
         │
         ▼
  CheckoutController still returns 201
  (saga continues via Kafka; client polls for redirect URL)
```

**Spring Retry vs Resilience4j Retry:**

| | Spring Retry (`PaymentService`) | Resilience4j (`ProductService`) |
|---|---|---|
| Annotation | `@Retryable` | `@Retry` |
| Circuit Breaker | No | Yes (`@CircuitBreaker`) |
| Bulkhead | No | Yes (`@Bulkhead`) |
| Config location | Annotation params | `application.yml` |
| Best for | Simple retry with backoff | Full resilience pipeline |

**Important rule:** `@Retryable` must be on a Spring bean called **from outside** that bean (via proxy). Putting it on a private method or calling it from the same class won't work — AOP proxy won't intercept it.

---

## 15. Spring Actuator

**What it does:** Exposes HTTP endpoints for health, metrics, info, env — used by monitoring and orchestration tools.

**Key endpoints:**

| Endpoint | What it shows |
|---|---|
| `/actuator/health` | App + dependency health |
| `/actuator/metrics` | JVM, HTTP, custom metrics |
| `/actuator/info` | Git commit, build info |
| `/actuator/env` | Active config properties |

**Where in this project:** `order-service`, `user-service` (and others via common config)

---

## 16. Spring GraphQL

**What it does:** Serve a GraphQL API instead of (or alongside) REST. Clients request exactly the fields they need.

**Key annotations:**

| Annotation | Purpose |
|---|---|
| `@QueryMapping` | Maps a GraphQL query to a method |
| `@MutationMapping` | Maps a GraphQL mutation |
| `@SchemaMapping` | Maps a field resolver |

**Where in this project:** `bff/graphql-bff` — a Backend-for-Frontend that aggregates data from multiple services.

---

## 17. Spring WebSocket

**What it does:** Full-duplex, persistent connection between client and server. Used for real-time updates (order status, notifications).

**Where in this project:** `order-service` (publishes order state changes to connected browser clients)

---

## 18. Spring HATEOAS

**What it does:** Adds hypermedia links to REST responses so clients can discover next actions without hardcoding URLs.

**Example response with HATEOAS:**
```json
{
  "orderId": "abc-123",
  "status": "PENDING",
  "_links": {
    "self": { "href": "/api/v1/orders/abc-123" },
    "cancel": { "href": "/api/v1/orders/abc-123/cancel" }
  }
}
```

**Where in this project:** Declared as dependency; used for enriched order/product responses.

---

## 19. Spring State Machine

**What it does:** Model an entity's lifecycle as explicit states and transitions. Prevents illegal state changes at the framework level.

**Where in this project:** Models the `Order` lifecycle:
```
CREATED → PAYMENT_PENDING → PAID → SHIPPED → DELIVERED
                         ↘ PAYMENT_FAILED → CANCELLED
```

---

## 20. Spring Vault

**What it does:** Fetches secrets (DB passwords, API keys) from HashiCorp Vault instead of storing them in config files.

**Where in this project:** Declared as dependency for secret management in production-like setups.

---

## 21. Spring WebFlux (Reactive)

**What it does:** Non-blocking, reactive web framework. Handles many concurrent requests with fewer threads than traditional Servlet/MVC.

**Where in this project:**
- `infra/api-gateway` — Gateway is WebFlux-based (required by Spring Cloud Gateway)
- `bff/graphql-bff` — uses WebFlux for reactive aggregation

**MVC vs WebFlux:**
```
Spring MVC (Servlet)          Spring WebFlux (Reactive)
─────────────────────         ─────────────────────────
1 thread per request          Event loop (few threads)
Blocking I/O is fine          Must use Mono/Flux
Easier to understand          Higher throughput under load
order-service, user-service   api-gateway, graphql-bff
```

---

## Session Log

| Date | What we discussed / built |
|---|---|
| 2026-10-12 | Audited all Spring features in the project (~35 features across 9 Spring ecosystem projects) |
| 2026-10-12 | Discussed where `spring-boot-starter-aop` is used: `FeatureFlagAspect` (common-lib) and `ControllerLoggerAspect` (spring-security-apps) |
| 2026-10-12 | Implemented **Spring Retry** in `order-service`: added `spring-retry` dep, `@EnableRetry` on main class, created `PaymentService` with `@Retryable`+`@Recover`, updated `CheckoutController` to use it |
| 2026-10-12 | Implemented **Spring Batch** in `order-service`: stale order cleanup job — `StaleOrderReader`, `StaleOrderProcessor`, `StaleOrderWriter`, `StaleOrderJobConfig`, `StaleOrderJobScheduler` under `batch/` package |
| 2026-10-12 | Implemented **Spring Events** in `order-service`: `OrderCancelledEvent` (record), published from `StaleOrderProcessor` via `ApplicationEventPublisher`, consumed by `OrderCancelledEventListener` with `@EventListener` |
| 2026-10-12 | Implemented **@Async** in `order-service`: `@EnableAsync` on main class, named `ThreadPoolTaskExecutor` (`eventExecutor`, core=2 max=5) in `AsyncConfig`, `@Async("eventExecutor")` on `OrderCancelledEventListener` |
| 2026-10-12 | Implemented **Web Layer** in `order-service`: `RequestTimingInterceptor` (HandlerInterceptor), `@CurrentUser` annotation + `CurrentUserArgumentResolver`, `RestClient` bean — all wired via `WebMvcConfig` |
