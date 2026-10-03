# API Gateway — Canary Routing

Percentage-based traffic split between two versions of a downstream service via
Spring Cloud Gateway's built-in `Weight` predicate. Small % of users see v2
while metrics validate; ramp up if healthy, roll back instantly if not.

Last item from the original gateway roadmap (H). Combines with the earlier
Prometheus dashboard for live comparison of v1 vs v2 latency and error rate.

---

## 1. What canary routing actually is

```
Naïve deploy:
  Deploy v2 → 100% of traffic → discover bug → 100% of users affected

Canary deploy:
  Deploy v2 → 5% of traffic → watch metrics for 10 min
    ├─ metrics healthy → 25% → 50% → 100% (gradual ramp)
    └─ metrics bad     → 0% (roll back via config)

Key property: only a small % of users exposed to risk during validation.
```

Related patterns:
- **Blue-green** — full-traffic cutover (0→100). Faster to finish, bigger blast radius.
- **Feature flag** — per-user in-app code path selection. Overlaps with canary.
- **A/B test** — same mechanism as canary, different goal (measure user behavior, not deploy safety).

---

## 2. Spring Cloud Gateway's `Weight` predicate

```yaml
- id: user-v1
  predicates:
    - Path=/api/v1/users/**
    - Weight=userGroup,95     ← 95% of matching traffic
  uri: lb://user-service

- id: user-v2
  predicates:
    - Path=/api/v1/users/**
    - Weight=userGroup,5      ← 5% of matching traffic
  uri: lb://user-service-v2
```

Framework groups routes by name (`userGroup`), sums the weights, and does
**weighted random selection per request**. Ratio matters — `95:5` = `950:50` = `19:1`.

Runs a random number generator on every request. Per-request random,
NOT per-user sticky. Same user hitting twice may get v1 then v2. Extension
below covers deterministic per-user routing.

---

## 3. The three-route pattern we use

For a canary demo you actually need THREE routes serving the same path:

```
Route 1: user-service-v2-override   ← Path + Header=X-Canary,force-v2
                                       manual override, no weight
                                       forces v2 regardless of split

Route 2: user-service-v1             ← Path + Weight=userGroup,95
                                       normal traffic, 95%

Route 3: user-service-v2             ← Path + Weight=userGroup,5
                                       normal traffic, 5%
```

Predicate matching is first-match-wins in listed order. The override route
comes FIRST so a request with `X-Canary: force-v2` always hits it. Requests
without the header fall through to the weighted split.

The override route is essential for QA/manual testing — engineers can force
v2 to verify their changes without waiting for the 5% dice to land on them.

---

## 4. Design decisions

### 4a. Same JVM vs two backend pods

Two options for the v2 target:
- **Two real backends** (`user-service` + `user-service-v2` deployments)
- **Same backend, different path prefix** (`/api/v1/...` vs `/api/v2/...`)

We use the second — modify `user-service` to expose `/api/v2/users/me` alongside
the existing `/api/v1/users/me`. Gateway routes `/api/v1/users/**` between:
- v1: pass through as-is → hits existing `UserController` (`/api/v1/users/**`)
- v2: `RewritePath=/api/v1/users/(?<seg>.*), /api/v2/users/${seg}` → hits new `UserV2Controller`

Cleaner demo without spinning up a second JVM. In real prod, replace with two
actual deployments — the gateway config doesn't change.

### 4b. Response shape divergence

v2 returns a richer response with a `canaryVersion: "v2"` field. Clients (and
your `jq` verification) can see which version served them without any
gateway-added header:

```bash
curl ... | jq -r '.canaryVersion // "v1"'
# → v1  (default: v1 response doesn't include this field)
# → v2  (v2 response has "canaryVersion":"v2")
```

Real-world: canary responses SHOULD be backward-compatible with v1 clients.
v2 can add fields, not remove them, not change types. Breaking changes need
API versioning at a different level (Accept-Version header, etc.).

### 4c. Observability: distinct `route_id` labels

Since v1 and v2 are separate routes, Prometheus metrics naturally break down
by `route_id`:

```promql
# p99 latency per canary version
histogram_quantile(0.99,
  sum(rate(spring_cloud_gateway_requests_seconds_bucket{route_id=~"user-service-v.*"}[5m]))
  by (le, route_id))
```

Grafana dashboard "Canary rollout" row shows:
- **Traffic split** — v1 RPS vs v2 RPS live
- **Actual v2 share** — measured %, should hover near configured weight
- **v1 vs v2 p99 latency** — side-by-side, canary regression instantly visible
- **v1 vs v2 error rate** — 5xx per route

Interviewer: *"how do you know when to roll back?"* Answer: alert when
`p99{route_id="user-service-v2"} / p99{route_id="user-service-v1"} > 1.5`
for 5 minutes.

### 4d. CanaryRoutingFilter — observational

The routing decision is entirely handled by `Weight` predicate (framework
built-in). Our `CanaryRoutingFilter` is **observational only**:

1. Read matched `Route` from exchange attribute
2. Detect if routeId contains `-v1` or `-v2`
3. Add `X-Canary-Route: v1 | v2 | v2-override` response header for client debug
4. Emit `gateway.canary.routed{version, reason}` counter for dashboard

Order = 10_001 (runs AFTER `RouteToRequestUrlFilter` which picks the route).
Zero impact on routing itself.

### 4e. Where the split value lives

Currently in `application.yml` — restart to change. Alternatives:

- **Dynamic via /admin/routes** — you already have this. `PUT /admin/routes/user-service-v2` with new weight. Change without restart.
- **@RefreshScope** — put weight in a properties bean, hot-reload via `/actuator/refresh`. Complex because `Weight` predicate parses at route-build time.
- **Feature flag service** (LaunchDarkly, Unleash) — external control, per-user targeting.

For the demo: static. For real prod: dynamic routes admin OR feature flag service.

### 4f. Rollback strategy — three speeds

```
Speed 1 (yaml + restart)         Speed 2 (dynamic route)          Speed 3 (kill switch)
─────────────────                 ────────────────                 ─────────────────
Edit application.yml              curl -X PUT ...                 Env var GATEWAY_CANARY=off
Change weight to 0                weight=0                        forces 100% v1 in filter
mvn spring-boot:run               refresh event                   instant
minutes                           seconds                         ~0 ms
```

Real prod: automate. Argo Rollouts, Flagger, or a custom controller watching
Grafana alerts and calling `/admin/routes` to drop weight to 0.

---

## 5. Config

```yaml
gateway:
  canary:
    enabled: true
    override-header: X-Canary
    override-value: force-v2
    response-header: X-Canary-Route

spring:
  cloud:
    gateway:
      routes:
        - id: user-service-v2-override
          uri: lb://user-service
          predicates:
            - Path=/api/v1/users/**
            - Header=X-Canary,force-v2
          filters:
            - RewritePath=/api/v1/users/(?<seg>.*), /api/v2/users/${seg}
            # ... other filters

        - id: user-service-v1
          uri: lb://user-service
          predicates:
            - Path=/api/v1/users/**
            - Weight=userGroup,95
          filters:
            # ... existing filters (rate limit, CB, retry, etc.)

        - id: user-service-v2
          uri: lb://user-service
          predicates:
            - Path=/api/v1/users/**
            - Weight=userGroup,5
          filters:
            - RewritePath=/api/v1/users/(?<seg>.*), /api/v2/users/${seg}
            # ... same resilience filters as v1
```

---

## 6. Files added / changed

```
api-gateway/
├── src/main/
│   ├── java/com/example/apigateway/
│   │   ├── canary/                                          (NEW package)
│   │   │   ├── CanaryProperties.java                        (NEW)
│   │   │   └── CanaryRoutingFilter.java                     (NEW — GlobalFilter, order 10001)
│   │   └── metrics/
│   │       └── GatewayMetrics.java                          (+ canaryRouted method)
│   └── resources/
│       └── application.yml                                  (3 canary routes + gateway.canary block)
├── observability/grafana/dashboards/
│   └── gateway-overview.json                                (+ Canary rollout row: 4 new panels)

user-service/
└── src/main/java/com/example/userservice/controller/
    └── UserV2Controller.java                                (NEW — /api/v2/users/**)

docs/microservices/api-gateway/
└── canary-routing.md                                        (this file)
```

Zero new dependencies. The `Weight` predicate ships with Spring Cloud Gateway.

---

## 7. Verification

### 7.1 Rebuild + start

```bash
mvn -pl user-service,api-gateway clean install

mvn -pl auth-server  spring-boot:run  # :9010
mvn -pl user-service spring-boot:run  # :8090
mvn -pl api-gateway  spring-boot:run  # :8080

TOKEN=$(curl -s -u admin:admin123 \
  -d "grant_type=client_credentials&scope=read" \
  http://localhost:9010/oauth2/token | jq -r .access_token)
```

### 7.2 Weight split — send 100 requests, expect ~95:5

```bash
for i in $(seq 1 100); do
  curl -s -H "Authorization: Bearer $TOKEN" \
    http://localhost:8080/api/v1/users/me \
    | jq -r '.canaryVersion // "v1"'
done | sort | uniq -c

# Typical output (random each run):
#   92 v1
#    8 v2
```

The exact ratio varies (random per request), but with 100 samples you'll see
~90-98 v1 and ~2-10 v2. Send 1000 requests for a tighter fit.

### 7.3 Override header forces v2 every time

```bash
for i in $(seq 1 10); do
  curl -s -H "Authorization: Bearer $TOKEN" \
    -H "X-Canary: force-v2" \
    http://localhost:8080/api/v1/users/me \
    | jq -r '.canaryVersion // "v1"'
done

# → v2 (all 10)
```

### 7.4 Response header shows the route

```bash
curl -i -H "Authorization: Bearer $TOKEN" \
  -H "X-Canary: force-v2" \
  http://localhost:8080/api/v1/users/me 2>&1 | grep -i X-Canary
# X-Canary-Route: v2-override
```

Normal traffic (no override):
```bash
curl -i -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/users/me 2>&1 | grep -i X-Canary
# X-Canary-Route: v1   (or v2 if you got lucky)
```

### 7.5 Response bodies differ

```bash
# v1
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/users/me | jq
# { ... existing v1 response ... }

# v2
curl -s -H "Authorization: Bearer $TOKEN" \
  -H "X-Canary: force-v2" \
  http://localhost:8080/api/v1/users/me | jq
# {
#   "id": 1,
#   "name": "Alice",
#   "email": "alice@example.com",
#   "canaryVersion": "v2",
#   "features": ["dark-mode", "beta-search"],
#   "note": "You are seeing the v2 canary response"
# }
```

### 7.6 Prometheus metrics

```bash
curl -s http://localhost:8080/actuator/prometheus | grep -E "canary_routed"

# gateway_canary_routed_total{reason="weight-split",version="v1",...} 92.0
# gateway_canary_routed_total{reason="weight-split",version="v2",...} 8.0
# gateway_canary_routed_total{reason="header-override",version="v2",...} 10.0
```

### 7.7 Grafana dashboard

```bash
open http://localhost:3000
# API Gateway → API Gateway — Overview
# Scroll to "Canary rollout" row (below custom filters, above JVM)
#
# Panels:
#  - Traffic split — v1 vs v2 RPS  (two series climbing)
#  - Actual v2 share (last 5m)     (should hover ~5%)
#  - v1 vs v2 p99 latency          (side by side)
#  - v1 vs v2 error rate           (side by side)
```

Generate load:
```bash
for i in $(seq 1 500); do
  curl -s -H "Authorization: Bearer $TOKEN" \
    http://localhost:8080/api/v1/users/me > /dev/null
done
```

Refresh Grafana — actual v2 share panel should settle near 5%. Latency panel
shows both routes with comparable p99 (they hit the same JVM so latency should
be similar; real prod would show different pods with different perf).

### 7.8 Simulate a canary regression

Introduce artificial latency in `UserV2Controller.me()`:

```java
@GetMapping("/me")
public Map<String, Object> me() throws InterruptedException {
    Thread.sleep(500);   // deliberately slow
    return Map.of(...);
}
```

Restart user-service. Generate load. Grafana:
- v1 p99 stays ~10ms
- v2 p99 climbs to ~500ms

Real production: alert on `p99{route_id="user-service-v2"} > p99{route_id="user-service-v1"} * 2 for 5m` → automated rollback.

Remove the sleep to reset.

### 7.9 Ramp the canary

Edit `application.yml` to change weights from 95:5 to 75:25:

```yaml
- id: user-service-v1
  predicates:
    - Path=/api/v1/users/**
    - Weight=userGroup,75   # was 95

- id: user-service-v2
  predicates:
    - Path=/api/v1/users/**
    - Weight=userGroup,25   # was 5
```

Restart. Send traffic. Grafana "actual v2 share" panel climbs to ~25%.

To go fully to v2 without deleting the v1 route, set v1 weight to 0.

---

## 8. Interview cheat-sheet

| Question | Answer |
|---|---|
| How does canary routing work? | Percentage-based traffic split between two backend versions. Small % validates; ramp up if healthy. |
| Spring Cloud Gateway impl? | Built-in `Weight` predicate. Groups routes by name, weighted random selection per request. |
| Random vs sticky? | `Weight` is per-request random. For per-user stickiness: deterministic hash of user ID modulo 100 → weight bucket. Users always see same version. |
| Rollback strategy? | Fastest: kill-switch env var. Faster: dynamic route weight update. Slowest: yaml + restart. Real prod: automated via Argo Rollouts / Flagger. |
| How do you know canary is failing? | Grafana panels break down metrics by `route_id`. Compare v1 vs v2 p99 latency + error rate. Alert when v2 significantly worse. |
| Blue-green vs canary? | Blue-green: full-traffic cutover, faster to complete, bigger blast radius. Canary: gradual, smaller blast radius, longer to complete. |
| Feature flag vs canary? | Feature flag = per-user in-app code path (in-process). Canary = per-request traffic routing (infra). Combine for maximum control. |
| A/B test vs canary? | Same mechanism (traffic split), different goal. A/B = measure user behavior. Canary = deploy safety. |
| How long to bake at each stage? | Depends on traffic + confidence. Common: 5% for 15min → 25% for 30min → 50% for 1hr → 100%. Automate with metric-based gates. |
| Sticky routing impl? | Hash user ID (JWT sub) to 0-99. If < current canary %, route to v2. Consistent per user. Requires custom filter. |
| Override header for QA? | Yes — critical for manual testing. Send `X-Canary: force-v2` → routes to v2 regardless of split. Also useful for smoke tests. |
| Metric-driven rollback? | Compare v1 vs v2 latency/error rate. Trigger rollback (weight=0) when v2 significantly worse. Automate with Prometheus alerts + admin API call. |
| What about schema-breaking changes? | Canary assumes v2 is backward-compat for callers. Breaking changes need API versioning (Accept-Version header) at a different layer. |
| How to canary a stateful service? | Hard — session affinity issues. Use header-based routing (users tagged as "canary users") instead of random split. Or shadow traffic. |
| Shadow traffic (dark launch)? | Related pattern: mirror requests to v2 without returning its response to client. v2 gets real load without user impact. Spring Cloud Gateway supports via `MirrorRequest` extension. |

---

## 9. Common pitfalls (interview probes)

1. **Two routes matching → order matters** — Spring Cloud Gateway iterates routes in order. Override route MUST be first for the `Header=X-Canary` to win over weighted routes with matching Path.
2. **`Weight` normalizes to 100** — `95:5` and `950:50` are the same. Documentation-wise, prefer readable numbers.
3. **Per-request random ≠ per-user stickiness** — user hits v1 first request, v2 second. Confusing UX. Real prod uses hash-based routing.
4. **Session state on v1 vs v2** — user starts a wizard on v1, next click goes to v2 which doesn't have their session. Sticky routing is essential for stateful flows.
5. **Metric tags for `route_id`** — Spring Cloud Gateway auto-adds this label. Match with `route_id=~"user-service-v.*"` for regex across canary routes.
6. **Schema changes** — v2 must be additive. Removing/renaming fields breaks v1 clients receiving v2 responses.
7. **RewritePath capture groups** — `(?<seg>.*)` + `${seg}` — Spring Cloud Gateway uses this specific syntax, not backreferences like `$1`.
8. **CircuitBreaker separate per route** — v1 and v2 have separate CB stats (different `route_id` in metrics but same CB `name` if you reuse `userCB` — deliberate: shared circuit state so v2 failures don't only affect v2 route).
9. **Cache pollution** — response cache stores by URL. v1 GET response cached; next canary v2 for same URL would still get v1 cached response until TTL. Include version in cache key OR disable cache during canary.

---

## 10. Extensions (parked)

- **Sticky routing** — deterministic per-user routing. Custom filter:
  ```java
  String userId = jwt.getSubject();
  int bucket = Math.abs(userId.hashCode()) % 100;
  boolean toV2 = bucket < canaryPercentage;   // stable per user
  ```
- **Metric-driven auto-rollback** — Prometheus alert → webhook → PUT /admin/routes with weight=0 for v2.
- **Argo Rollouts / Flagger integration** — declarative canary controller that manages weights based on metric analysis. Kubernetes-native.
- **Shadow traffic (dark launch)** — mirror requests to v2 without waiting for/returning its response. v2 gets real load, users see v1. Spring Cloud Gateway needs custom filter for this.
- **Per-user targeting** — canary specific user IDs (internal beta testers). Filter reads JWT + config allowlist.
- **Geographic canary** — route by client region (Cloudflare header, geolocation) to specific canaries.
- **Multi-version canary** — v1 (70%) + v2 (20%) + v3 (10%) simultaneously. Just add more Weight-tagged routes with the same group name.
- **Dynamic weight adjustment** — expose weight as a `@RefreshScope` property, edit + `/actuator/refresh` to shift traffic without restart.
- **Automated ramp** — script that reads Grafana dashboards, waits for green metrics, calls admin API to bump v2 weight. Real "continuous canary".
- **Kill-switch env var** — `GATEWAY_CANARY_ENABLED=false` short-circuits the filter to force 100% v1. Fastest possible rollback.
