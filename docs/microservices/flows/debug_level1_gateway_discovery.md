# Debug Level 1 — Gateway + Service Discovery (the simplest possible flow)

**Difficulty:** ⭐ (easiest)
**Time to master:** 30 minutes
**Systems involved:** 4 (client, gateway, product-service, Eureka)
**Prerequisite:** none — start here

**What you'll be able to explain after this:**
- What service discovery is and why hardcoded URLs are bad
- What the API gateway actually does in one request
- How reactive gateway calls a servlet downstream
- How trace IDs propagate across services

Everything else in the project builds on these concepts.

---

## Table of contents

1. [What this flow is](#1-what-this-flow-is)
2. [Architecture — the 4 boxes](#2-architecture--the-4-boxes)
3. [Pre-flight — what must be up](#3-pre-flight--what-must-be-up)
4. [The request — step by step](#4-the-request--step-by-step)
5. [Patterns exercised](#5-patterns-exercised)
6. [Failure scenarios](#6-failure-scenarios)
7. [Interview questions this flow answers](#7-interview-questions)
8. [Debugging cheat sheet](#8-debugging-cheat-sheet)
9. [What you learned — how it shows up in Levels 2 & 3](#9-what-you-learned)

---

## 1. What this flow is

**Business scenario:** a client asks "show me all products". The gateway routes the request to the product-service, which returns the list.

**In one sentence:** `GET /api/v1/products` on the gateway → looked up in Eureka → forwarded to product-service → response returned.

No writes. No async. No Kafka. No auth. **Just discovery + routing.**

---

## 2. Architecture — the 4 boxes

```
   ┌────────────┐
   │  Client    │  curl / Postman
   │            │
   └─────┬──────┘
         │  1. GET http://localhost:8080/api/v1/products
         │     Header: X-Correlation-Id: level1-demo-001
         ▼
   ┌────────────────────────────┐
   │  api-gateway :8080         │
   │  (reactive, Netty)         │
   │                            │      2. asks Eureka
   │  Route matches:            │────────────────────────▶ ┌────────────────┐
   │    Path=/api/v1/products/**│  "who is product-service?" │  Eureka :8761  │
   │    uri: lb://product-svc   │◀────────────────────────  │                │
   │                            │  "here are the instances"  │  Registry      │
   │  LoadBalancer picks one    │                            └────────────────┘
   └─────────────┬──────────────┘
                 │  3. forwards HTTP to chosen instance
                 │     GET http://<ip>:8082/api/v1/products
                 │     with X-Correlation-Id preserved
                 ▼
   ┌────────────────────────────┐
   │  product-service :8082     │
   │  (servlet, Tomcat)         │
   │                            │
   │  ProductController         │
   │    ↓                       │
   │  ProductService            │
   │    ↓                       │
   │  ProductRepository ────────┼──▶ ┌────────────────┐
   │                            │    │  MySQL         │
   │  4. returns List<Product>  │    │  mysql-product │
   │                            │    │  :3308         │
   └────────────────────────────┘    └────────────────┘

   Meanwhile every hop is captured by:
   ┌────────────────────────────┐
   │  Zipkin :9411              │  ← receives spans from gateway + product-svc
   │  (trace timeline)          │  ← same traceId across both services
   └────────────────────────────┘
```

**Only 4 things:** client, gateway, Eureka, product-service. (Plus MySQL and Zipkin as passive supporting cast.)

---

## 3. Pre-flight — what must be up

Run:

```bash
./status.sh
```

For Level 1 you only need:

- ✅ **mysql-product** container HEALTHY
- ✅ **zipkin** container HEALTHY
- ✅ **eureka-server** (8761) UP
- ✅ **api-gateway** (8080) UP
- ✅ **product-service** (8082) UP
- ✅ Eureka registrations include `PRODUCT-SERVICE` and `API-GATEWAY`

You can ignore auth-server, order-service, payment-service, notification, user-service. Not needed for this flow.

**If gateway or product-service are missing:** see [../01-startup-runbook.md](../01-startup-runbook.md) Phase 2.

---

## 4. The request — step by step

### 4a. Fire it

```bash
curl -v http://localhost:8080/api/v1/products \
  -H "X-Correlation-Id: level1-demo-001"
```

The `-v` (verbose) shows the HTTP request/response headers so you can see the correlation ID going in and out.

**Expected response:**
- **First-ever call:** may return `[]` (empty array) if product DB is empty
- **After creating products:** JSON array of ProductResponse objects
- **HTTP status:** `200 OK`
- **Response header:** should include `X-Correlation-Id: level1-demo-001` (echoed back)

### 4b. What each service should log

Look at the terminal running each service. Search for `level1-demo-001` (your correlation ID).

**In gateway's terminal (order of log lines):**

```
DEBUG ... reactor.netty.http.server.HttpServer : Handling request
INFO  [api-gateway,<traceId>,<spanId1>,level1-demo-001] ... routed to lb://product-service
DEBUG ... o.s.c.g.f.factory.RewritePathGatewayFilterFactory : Rewriting path
```

Key things:
- `traceId` is auto-generated (long hex)
- `correlationId` = your header value
- Route matched — `lb://product-service`

**In product-service's terminal:**

```
INFO  [product-service,<SAME traceId>,<spanId2>,level1-demo-001] c.e.p.controller.ProductController : GET /api/v1/products
DEBUG ... org.hibernate.SQL : select p1_0.id, p1_0.name, ... from products p1_0
```

Key things:
- Same `traceId` as gateway = **trace propagation working** ✅
- New `spanId` = new hop
- Same `correlationId` = **MDC propagation working** ✅

### 4c. See the trace in Zipkin

1. Open http://localhost:9411
2. Click "Run Query" (default filters)
3. Look for a trace named `get /api/v1/products` — it'll be the most recent one
4. Click it → see the timeline

Expected shape:

```
   api-gateway     GET /api/v1/products          |███████████| 45ms total
   api-gateway     → HTTP GET product-service    |█████     | 20ms
   product-service   GET /api/v1/products        |████    |    18ms
   product-service   Hibernate select            |██ |         5ms
```

**Two service names, one trace ID, connected timeline.** This is distributed tracing in action.

### 4d. Prove it went through the gateway (not direct)

Compare these two calls:

```bash
# Via gateway
curl -s http://localhost:8080/api/v1/products -w "\nHTTP: %{http_code}\n"

# Direct to product-service
curl -s http://localhost:8082/api/v1/products -w "\nHTTP: %{http_code}\n"
```

Both return the same data. But the gateway version:
- Went through Netty (reactive) at 8080
- Was routed via Eureka lookup
- Produced a longer trace in Zipkin (2 services, not 1)

**Turn OFF product-service** (Ctrl+C in its terminal). Try both again:
- Direct call (`:8082`) → **connection refused** (expected)
- Gateway call (`:8080`) → **503 Service Unavailable** with message like `Unable to find instance for product-service`

That 503 with that specific message = **proof the gateway asked Eureka, Eureka said nobody's registered, gateway correctly failed.** This is discovery working correctly.

Restart product-service after this test.

---

## 5. Patterns exercised

Only 4 patterns in Level 1 — every other pattern in the project is a layer on top.

### 5.1 Service Discovery (Eureka registration)

**File:** `product-service/src/main/resources/application.yml`

```yaml
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
    register-with-eureka: true
    fetch-registry: true
```

**What happens:** on startup, product-service posts its own address to Eureka. Every 30 seconds it heartbeats. If it dies, Eureka evicts it after ~90s.

**Verify manually:**
```bash
curl -s http://localhost:8761/eureka/apps -H "Accept: application/json" | jq '.applications.application[].name'
```
Should include `PRODUCT-SERVICE`.

**Interview beat:** *"Services register themselves at startup. Callers discover them dynamically. No hardcoded IPs anywhere. This enables horizontal scaling — add instances, they auto-register, load balancer includes them automatically."*

### 5.2 Load Balancing (`lb://` URI scheme)

**File:** `api-gateway/src/main/resources/application.yml`

```yaml
routes:
  - id: product-service
    uri: lb://product-service         # ← lb:// is the magic
    predicates:
      - Path=/api/v1/products/**
```

**What `lb://` does:**
1. Spring Cloud LoadBalancer sees the scheme
2. Extracts the service name: `product-service`
3. Asks Eureka: "give me the instance list"
4. Picks one (round-robin by default)
5. Rewrites the URL: `http://<actual-ip>:8082/api/v1/products`
6. Forwards the request

**Interview beat:** *"Client-side load balancing. The gateway (client of product-service) picks which instance to hit, no external LB needed. Round-robin by default; can plug in weighted, response-time-based, etc."*

### 5.3 API Gateway routing

**File:** `api-gateway/src/main/resources/application.yml`

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: product-service
          uri: lb://product-service
          predicates:
            - Path=/api/v1/products/**   # ← what path to match
```

**What happens:** any request path starting with `/api/v1/products/` matches this route. Gateway sends it via `lb://` to product-service.

You can view the live route table:
```bash
curl -s http://localhost:8080/actuator/gateway/routes | jq
```

**Interview beat:** *"Gateway is one entry point for clients. Cross-cutting concerns (auth, rate limit, routing) live here. Reactive Netty because gateways are I/O-bound — thousands of concurrent connections, cheap threads."*

### 5.4 Distributed tracing (Micrometer + Zipkin)

**Config (in every service):**
```yaml
management:
  tracing:
    sampling:
      probability: 1.0
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans
```

**How it works:**
1. Gateway generates a `traceId` if the request doesn't have one
2. Adds header `traceparent: 00-<traceId>-<spanId>-01` to the outgoing request
3. Product-service reads the header, uses same `traceId`, creates a new `spanId` for its work
4. Both services send span data to Zipkin
5. Zipkin joins them by `traceId` into a timeline

**Log pattern that makes it visible:**
```yaml
logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-},%X{correlationId:-}]"
```

The `%X{traceId}` pulls from SLF4J's MDC (Mapped Diagnostic Context) — the tracing library puts trace info there.

**Interview beat:** *"Grepping 8 service logs by timestamp is impossible. Trace IDs solve it. Zipkin visualizes the timeline — you see exactly which service is slow."*

---

## 6. Failure scenarios

### 6.1 Product-service down

**Trigger:** Ctrl+C in product-service terminal.

**Test:**
```bash
curl -v http://localhost:8080/api/v1/products
```

**Expected:** HTTP 503 with body containing `Unable to find instance for product-service` or `LoadBalancer does not contain an instance`.

**What happened:**
1. Gateway matched the path
2. Asked LoadBalancer for a `product-service` instance
3. LoadBalancer asked Eureka
4. Eureka said "no instances registered right now"
5. Gateway returned 503 to client

**Interview beat:** *"Gateway fails fast when downstream isn't available. No 30-second timeout, no thread waiting. Client gets a clear error and can retry."*

### 6.2 Eureka down

**Trigger:** Ctrl+C in eureka-server terminal.

**Wait 30-60 seconds** (client-side Eureka cache stays fresh for a while), then:

```bash
curl -v http://localhost:8080/api/v1/products
```

**Expected:**
- **For a while:** still works (gateway has cached Eureka registry)
- **Eventually:** 503 as cache expires and services can't re-register

**Interview beat:** *"Eureka is AP (in CAP): favors Availability over Consistency. Clients have local cache so a brief Eureka outage doesn't take down the fleet. But sustained outage → cache decays."*

Restart eureka-server after this test. Wait 30-60s for services to re-register.

---

## 7. Interview questions

### Q1: "Walk me through what happens when a request hits your API gateway."

**Your answer:**

1. Client sends `GET /api/v1/products` to gateway at :8080
2. Gateway (reactive, Netty) receives it
3. Gateway matches the path against configured routes → finds `lb://product-service`
4. LoadBalancer asks Eureka for `product-service` instances
5. LoadBalancer picks one instance (round-robin)
6. Gateway forwards the request to that instance
7. Product-service handles it, returns response
8. Response flows back through gateway to client
9. All hops traced with the same trace ID → visible in Zipkin

Bonus points: mention `TokenRelay` filter for JWT propagation, `default-filters` for cross-cutting, actuator endpoint for live route inspection.

### Q2: "Why not hardcode `http://product-service:8082` in every caller?"

**Your answer:**
- **Scaling:** hardcoded = one instance only. Discovery = N instances, LB round-robin.
- **Deployment:** IPs change on redeploy. Discovery hides that from callers.
- **Environments:** dev vs staging vs prod all use the same code — different registry contents.
- **Failure isolation:** dead instance is auto-evicted from registry, LB stops sending to it.

### Q3: "What is the difference between the gateway and the resource-server / downstream service?"

**Your answer:**
- **Gateway:** reactive, Netty, thin. Only routes + cross-cutting concerns (auth, rate limit, tracing). No business logic.
- **Downstream:** servlet or reactive. Owns business logic + data. Usually one bounded context per service.

Gateway is like a hotel receptionist — directs you to the right room. It doesn't cook your food.

### Q4: "How do you know which service is slow if a request takes 5 seconds?"

**Your answer:** distributed tracing. Open Zipkin, find the trace, see the timeline. Each span is a bar — the longest bar is your bottleneck. Also expose Micrometer metrics to Prometheus for aggregated latency.

### Q5: "What if Eureka goes down?"

**Your answer:** short-term OK (clients cache the registry). Long-term degraded (new services can't register, existing entries expire). Real prod: run Eureka in a peer cluster (multiple nodes) so single-node failure doesn't kill discovery. Or use a k8s-native discovery model where kube-DNS handles it.

---

## 8. Debugging cheat sheet

| Symptom | Cause | Fix |
|---|---|---|
| Gateway returns 404 for `/api/v1/products` | Route not matched | `curl :8080/actuator/gateway/routes` — check the path predicate matches your URL exactly |
| Gateway returns 503 | No instances in Eureka | Check product-service is up + registered: `curl :8761/eureka/apps` |
| Gateway hangs / long timeout | product-service accepting connection but not responding | Check product-service logs; probably stuck in DB or something |
| No trace in Zipkin | Sampling or endpoint wrong | Check `management.tracing.sampling.probability: 1.0`, check `management.zipkin.tracing.endpoint` |
| Trace shows only gateway span, no product-service | product-service missing tracing bridge dep | Check product-service pom has `micrometer-tracing-bridge-brave` |
| Correlation ID missing in product-service logs | CorrelationIdFilter not registered | Check `LoggingConfig.java` in product-service |
| `Connection refused` when hitting `:8082` directly but works via gateway | product-service crashed | Check its terminal — probably an error you missed |
| Gateway logs `NoSuchElementException: Route not found` | You changed yml but didn't restart gateway | Ctrl+C, `./mvnw spring-boot:run` |

### Peek at everything at once

```bash
# Is the route table live?
curl -s http://localhost:8080/actuator/gateway/routes | jq '.[] | {id: .route_id, uri: .uri}'

# Is product-service registered?
curl -s http://localhost:8761/eureka/apps -H "Accept: application/json" | \
  jq '.applications.application[] | select(.name=="PRODUCT-SERVICE") | .instance[0] | {status, hostName, port}'

# Recent Zipkin traces for this service
curl -s 'http://localhost:9411/api/v2/traces?serviceName=api-gateway&limit=3&lookback=600000' | jq '.[0][0] | {name, duration}'
```

---

## 9. What you learned

Everything you saw here shows up again — with more layers — in Level 2 and Level 3.

**Concepts that will reappear:**

| Level 1 concept | Where it reappears |
|---|---|
| Eureka `lb://` discovery | Level 3: order-service → product-service Feign call |
| Gateway routing | Every subsequent flow — always goes through gateway |
| Distributed tracing | Level 2: across Kafka. Level 3: across saga + Kafka |
| Correlation IDs | Every subsequent flow |
| `X-Correlation-Id` header propagation | Level 3: still travels through the whole saga |

**What Level 2 adds on top:**
- Actual DB write (JPA transaction)
- Async publish to Kafka (StreamBridge)
- Outbox pattern (dual-write problem solution)
- Kafka consumer (functional model)

**What Level 3 adds on top of Level 2:**
- Feign client (typed HTTP inter-service call)
- Resilience4j (circuit breaker, retry, bulkhead)
- Saga orchestration + compensation
- Idempotent consumer (dedup table)
- State machine

If you're solid on Level 1, Level 2 is one concept at a time — not overwhelming.

---

## Practice routine — 30 minutes to Level 1 mastery

1. **Fire the happy request** (5 min): `curl :8080/api/v1/products` — read the response, read gateway logs, read product-service logs. Match trace IDs by eye.
2. **See the trace in Zipkin** (5 min): open UI, click the trace, understand the timeline.
3. **Failure 1: kill product-service** (5 min): retry, get 503, understand why.
4. **Restart product-service** (2 min): wait for Eureka to re-register (~30s), retry, works again.
5. **Failure 2: kill Eureka briefly** (10 min): observe cached-then-failed behavior.
6. **Explain the flow out loud** (3 min): pretend you're in the interview. If you fumble, re-read section 4b.

Do this once now, once tomorrow, once next week. Then you own Level 1.

**When you're comfortable → go to `debug_level2_user_registration.md` (to be created).**
