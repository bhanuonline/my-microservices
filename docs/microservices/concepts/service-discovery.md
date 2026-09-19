# Service discovery — Eureka + `lb://`

## The problem it solves

<!-- Fill in: why hard-coded URLs break at scale. Bring the visual from Problem #5. -->

## How it works

```
<!-- ASCII diagram of registration + lookup flow -->
```

## How it's wired in THIS project

- **Registry:** `eureka-server` module, port 8761
- **Clients:** every service pom has `spring-cloud-starter-netflix-eureka-client`
- **Config key:** `eureka.client.service-url.defaultZone`
- **Load balancer:** `spring-cloud-starter-loadbalancer` (used by Feign + gateway)
- **URI scheme:** `lb://service-name` — resolves via LB, which asks Eureka

Example (order-service → product-service):
- `order-service/src/main/java/com/example/orderservice/client/ProductClient.java`
- `@FeignClient(name = "product-service")` — no url → Eureka lookup

## How to observe it running

```bash
# Eureka dashboard
open http://localhost:8761

# Registered instances as JSON
curl -s http://localhost:8761/eureka/apps -H "Accept: application/json" | jq
```

## Common failure modes

- Service not registered — check its `spring.application.name` is set, Eureka URL is correct.
- Wrong name in Feign client — case-insensitive match against `spring.application.name`.
- Client cache stale — services refresh registry every 30s by default; instance changes take a moment to propagate.

## Interview talking points

- Client-side vs server-side load balancing (Eureka + LB = client-side).
- Heartbeats, instance eviction, self-preservation mode.
- Why Netflix Eureka fell out of favor (compared to Consul, k8s-native discovery).
- CAP tradeoff: Eureka chooses AP (available + partition-tolerant, may serve stale registry).
