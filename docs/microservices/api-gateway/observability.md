# API Gateway — Prometheus + Grafana Observability

Every feature you built emits metrics. This wires them up to Prometheus and
gives you a Grafana dashboard covering the full stack: HTTP throughput,
latency percentiles, resilience state (CB / bulkhead), custom business
signals (idempotency, cache, audit, API-key auth, fallback), and JVM health.

Roadmap item **L** from the original gateway build plan.

---

## 1. What you get "for free" once Prometheus is on the classpath

```
────────────────────────────────────────────────────────────────────────────
Source                            Metric family                   Kind
────────────────────────────────────────────────────────────────────────────
Spring Cloud Gateway
  spring_cloud_gateway_requests_seconds_bucket                    Histogram
  spring_cloud_gateway_requests_seconds_count{route_id,status}    Counter
  spring_cloud_gateway_requests_seconds_max                       Gauge

Resilience4j Circuit Breaker
  resilience4j_circuitbreaker_state{name,state}                   Gauge (0/1)
  resilience4j_circuitbreaker_calls_total{name,kind}              Counter
    kind = successful | failed | not_permitted | ignored
  resilience4j_circuitbreaker_failure_rate{name}                  Gauge
  resilience4j_circuitbreaker_slow_call_rate{name}                Gauge
  resilience4j_circuitbreaker_buffered_calls{name,kind}           Gauge

Resilience4j Bulkhead
  resilience4j_bulkhead_available_concurrent_calls{name}          Gauge
  resilience4j_bulkhead_max_allowed_concurrent_calls{name}        Gauge

Reactor Netty
  reactor_netty_http_server_response_time_seconds                 Histogram
  reactor_netty_connection_provider_total_connections             Gauge

JVM (micrometer-core)
  jvm_memory_used_bytes{area,id}                                  Gauge
  jvm_gc_pause_seconds                                            Timer
  jvm_threads_states_threads{state}                               Gauge
  process_cpu_usage                                               Gauge

R2DBC pool
  r2dbc_pool_acquired_connections{name}                           Gauge
```

Zero code. Just adding `micrometer-registry-prometheus` + exposing the
`prometheus` actuator endpoint activates all of these.

---

## 2. Custom metrics we added (business signals)

```
────────────────────────────────────────────────────────────────────────────
Filter               Metric                                       Kind
────────────────────────────────────────────────────────────────────────────
IdempotencyKey       gateway.idempotency.result{outcome}          Counter
                     outcome = hit | miss | conflict | mismatch |
                               bypassed | store_error

ResponseCache        gateway.response_cache.result{outcome}       Counter
                     outcome = hit | miss | write | skip_streaming |
                               skip_size | store_error

RouteAudit           gateway.route_audit.persist{outcome}         Counter
                     outcome = success | failure

ApiKey auth          gateway.apikey.auth{outcome}                 Counter
                     outcome = success | invalid | disabled

Fallback             gateway.fallback.served{service,reason}      Counter
                     reason = circuit-breaker-open | bulkhead-full |
                              timeout | unknown

BodyLogging          gateway.body_logging.samples{outcome}        Counter
                     (reserved — wire-in parked)
```

Prometheus name conversion:
- `.` → `_`
- Counter suffix `_total` appended automatically
- So `gateway.idempotency.result` → `gateway_idempotency_result_total` at scrape time

---

## 3. Design decisions

### 3a. Micrometer facade (`GatewayMetrics`) vs scattered counters

Chose a single `@Component` that owns all metric names + tags. Advantages:
- One place to see the full custom metric surface
- Easy to rename without hunting through filters
- Filters call `metrics.idempotency("hit")` — no coupling to `MeterRegistry`

Counter creation via `Counter.builder(...).register(registry)` — Micrometer
memoizes by name+tag combo, so repeated calls return the same instance. Zero
allocation per invocation.

### 3b. Cardinality discipline (interview trap)

Tags must be **low cardinality**. Every unique combination = a new time
series in Prometheus. Millions of series → OOM on the Prometheus server.

Safe tags:
```
outcome   = enum with 3-6 values
service   = enum matching your route ids (~5 values)
reason    = enum with 4 values
route_id  = your finite list of routes
status    = 2xx/4xx/5xx (~5 values)
```

Unsafe tags (never use):
```
user_id, correlation_id, request_id  → unbounded, one per request
timestamp, latency_ms                → continuous, effectively unbounded
raw path, query string               → unbounded permutations
```

The `GatewayMetrics` facade only uses enum-shaped values. Facade pattern
enforces this by convention.

### 3c. Pull vs push (why Prometheus)

- **Pull** (Prometheus): server owns scrape schedule, service discovery decides
  targets, no client-side buffering. Good for internal infra.
- **Push** (StatsD, OTLP): client pushes each metric; works through firewalls,
  survives ephemeral instances. Better for edge / mobile / serverless.

Micrometer supports both. Prometheus wins for a Kubernetes-style
service-oriented deployment. Swap to OTLP later = one dep change.

### 3d. Scrape interval

Prometheus default: 15s. Our config: **5s** for demo responsiveness — you
see graph movement fast when you generate load. Prod: 15s or 30s.

Trade-off: higher scrape rate = more Prometheus disk/RAM, tighter latency to
seeing changes.

### 3e. Dashboard strategy

Ships as JSON in the repo (`observability/grafana/dashboards/gateway-overview.json`).
Grafana loads it automatically via provisioning. No manual dashboard build
required after `docker-compose up`.

Five rows:
1. **Traffic** — total RPS, per-route RPS, status distribution, 5xx rate
2. **Latency** — p50/p95/p99 (all routes), p99 per route
3. **Resilience** — CB state, CB failure rate, bulkhead saturation, 429 rate
4. **Custom filters** — idempotency, cache, fallback, API-key auth, audit
5. **JVM** — heap, GC pause, CPU, threads

### 3f. Histogram vs summary for percentiles

Micrometer emits **histograms** (bucket counts). Prometheus computes quantiles
server-side via `histogram_quantile()`. Advantages:
- Aggregatable across pods (sum buckets first, then compute quantile)
- Server-side quantile choice — dashboard picks p95 or p99 without app change

Summary approach (client-side quantile computation) doesn't aggregate cleanly
across pods — never use for multi-instance services.

`management.metrics.distribution.percentiles-histogram` config enables the
bucket export. Without it: only mean + max, no quantiles possible.

---

## 4. Config

### `application.yml` changes

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, gateway, circuitbreakers, circuitbreakerevents,
                 metrics, refresh, env, prometheus       # added prometheus
  metrics:
    tags:
      application: ${spring.application.name}            # global tag for multi-service scrapes
    distribution:
      percentiles-histogram:
        http.server.requests: true                       # enable buckets
        spring.cloud.gateway.requests: true              # enable buckets for gateway
```

### `pom.xml` addition

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

---

## 5. Files added / changed

```
api-gateway/
├── pom.xml                                                    (+ micrometer-registry-prometheus)
├── docker-compose.observability.yml                           (NEW — Prometheus + Grafana)
├── observability/                                             (NEW dir tree)
│   ├── prometheus/
│   │   └── prometheus.yml                                     (scrape gateway every 5s)
│   └── grafana/
│       ├── provisioning/
│       │   ├── datasources/prometheus.yml                     (auto-wire datasource)
│       │   └── dashboards/dashboards.yml                      (auto-provision dashboards)
│       └── dashboards/
│           └── gateway-overview.json                          (5-row dashboard)
└── src/main/
    ├── java/com/example/apigateway/
    │   ├── metrics/                                           (NEW package)
    │   │   └── GatewayMetrics.java                            (NEW — Counter facade)
    │   ├── filter/
    │   │   └── IdempotencyKeyGatewayFilterFactory.java        (+ metrics.idempotency calls)
    │   ├── responsecache/
    │   │   └── ResponseCacheGlobalFilter.java                 (+ metrics.responseCache calls)
    │   ├── dynamicroutes/
    │   │   └── RouteAuditService.java                         (+ metrics.audit calls)
    │   ├── apikey/
    │   │   └── ApiKeyReactiveAuthenticationManager.java       (+ metrics.apiKeyAuth calls)
    │   └── controller/
    │       └── FallbackController.java                        (+ metrics.fallback + cache hit/miss)
    └── resources/
        └── application.yml                                    (+ prometheus exposure + tags + histogram)

docs/microservices/api-gateway/
└── observability.md                                           (this file)
```

---

## 6. Quick start

```bash
# 1. Rebuild the gateway with the new dep
cd api-gateway
mvn clean install

# 2. Start Prometheus + Grafana
docker-compose -f docker-compose.observability.yml up -d

# 3. Boot the gateway + auth-server + Redis + user-service as usual
mvn -pl auth-server  spring-boot:run
mvn -pl api-gateway  spring-boot:run
mvn -pl user-service spring-boot:run

# 4. Verify scrape endpoint on gateway
curl -s http://localhost:8080/actuator/prometheus | head -30

# 5. Prometheus UI
open http://localhost:9090
#    Status → Targets → api-gateway should show "UP", scrape < 5s ago

# 6. Grafana
open http://localhost:3000
#    Login: admin / admin, skip password change
#    Dashboards → API Gateway → API Gateway — Overview
```

---

## 7. Generate load + watch charts move

```bash
TOKEN=$(curl -s -u admin:admin123 \
  -d "grant_type=client_credentials&scope=read" \
  http://localhost:9010/oauth2/token | jq -r .access_token)

# 100 requests to hit rate limits / retries / cache
for i in {1..100}; do
  curl -s -H "Authorization: Bearer $TOKEN" \
    http://localhost:8080/api/v1/users/me > /dev/null
done

# Trigger idempotency hit/miss
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: metrics-demo-1" \
  -d '{"item":"x"}' http://localhost:8080/api/v1/orders

curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: metrics-demo-1" \
  -d '{"item":"x"}' http://localhost:8080/api/v1/orders

# Trigger a fallback (stop a downstream, hit its route repeatedly)
# Ctrl-C the user-service, then:
for i in {1..15}; do
  curl -s -o /dev/null -w "%{http_code} " \
    -H "Authorization: Bearer $TOKEN" \
    http://localhost:8080/api/v1/users/me
done

# Refresh Grafana — RPS, CB state, fallback reason panels all update.
```

---

## 8. Sample PromQL (interview-worthy)

Query directly in Prometheus UI (`:9090`):

```promql
# Requests per second per route
rate(spring_cloud_gateway_requests_seconds_count[1m])

# p99 latency per route (5-min window)
histogram_quantile(0.99,
  sum(rate(spring_cloud_gateway_requests_seconds_bucket[5m]))
  by (le, route_id))

# Error rate (5xx / total) per route
sum(rate(spring_cloud_gateway_requests_seconds_count{status=~"5.."}[5m])) by (route_id)
  /
sum(rate(spring_cloud_gateway_requests_seconds_count[5m])) by (route_id)

# Circuit breakers currently OPEN
resilience4j_circuitbreaker_state{state="open"} == 1

# Idempotency hit ratio (cache effectiveness signal)
sum(rate(gateway_idempotency_result_total{outcome="hit"}[5m]))
  /
sum(rate(gateway_idempotency_result_total[5m]))

# Bulkhead saturation (0 = empty, 1 = full)
1 - (resilience4j_bulkhead_available_concurrent_calls
     / resilience4j_bulkhead_max_allowed_concurrent_calls)

# 429s per minute per route
sum(rate(spring_cloud_gateway_requests_seconds_count{status="429"}[1m])) by (route_id)

# Fallback reason breakdown (spike detector)
sum(rate(gateway_fallback_served_total[5m])) by (reason)

# API key auth failure rate
sum(rate(gateway_apikey_auth_total{outcome="invalid"}[5m]))
  /
sum(rate(gateway_apikey_auth_total[5m]))
```

---

## 9. Sample alert rules (Alertmanager)

Not built here, but ready to drop into a Prometheus rules file:

```yaml
groups:
  - name: gateway-alerts
    rules:
      - alert: CircuitBreakerOpen
        expr: resilience4j_circuitbreaker_state{state="open"} == 1
        for: 5m
        annotations:
          summary: "CB {{ $labels.name }} has been OPEN for 5+ minutes"

      - alert: HighErrorRate
        expr: |
          sum(rate(spring_cloud_gateway_requests_seconds_count{status=~"5.."}[5m])) by (route_id)
          /
          sum(rate(spring_cloud_gateway_requests_seconds_count[5m])) by (route_id)
          > 0.05
        for: 5m
        annotations:
          summary: "Route {{ $labels.route_id }} 5xx rate > 5%"

      - alert: BulkheadSaturated
        expr: |
          resilience4j_bulkhead_available_concurrent_calls
          / resilience4j_bulkhead_max_allowed_concurrent_calls
          < 0.1
        for: 5m
        annotations:
          summary: "Bulkhead {{ $labels.name }} < 10% capacity"

      - alert: HighFallbackRate
        expr: sum(rate(gateway_fallback_served_total[5m])) > 1
        for: 5m
        annotations:
          summary: "Fallback served > 1/sec for 5 min ({{ $labels.reason }})"

      - alert: IdempotencyStoreErrors
        expr: sum(rate(gateway_idempotency_result_total{outcome="store_error"}[5m])) > 0
        for: 2m
        annotations:
          summary: "Idempotency store (Redis) is failing"
```

---

## 10. Failure modes & mitigations

| Scenario | Behavior | Mitigation |
|---|---|---|
| Prometheus can't reach gateway | `Targets` shows DOWN; graphs go blank | Check `host.docker.internal:8080` reachable from container (`docker exec -it gateway-prometheus wget -q -O- http://host.docker.internal:8080/actuator/prometheus`) |
| Grafana loads but no data | Datasource misconfigured | Grafana UI: Datasources → Prometheus → Test |
| Dashboard blank on first open | No traffic yet | Generate requests; Grafana refreshes every 5s |
| High cardinality → Prometheus OOM | Some counter tagged with user_id or timestamp | Audit `GatewayMetrics`; drop the bad tag |
| `histogram_quantile` returns NaN | Histogram buckets not enabled | Verify `management.metrics.distribution.percentiles-histogram` config |
| Memory / disk keeps growing | Retention too long | `--storage.tsdb.retention.time=1d` in docker-compose (already set) |
| Data disappears on Prometheus restart | Volume not mounted | Check `gateway-prometheus-data` volume in compose (already set) |
| Grafana admin/admin doesn't work | First-login password change loop | We set `GF_AUTH_ANONYMOUS_ENABLED=false` and admin creds via env; if still stuck, delete `gateway-grafana-data` volume and restart |
| No custom metrics in Prometheus | Filters never triggered — need at least one request | Generate load; the counters register lazily on first call |

---

## 11. Interview cheat-sheet

| Question | Answer |
|---|---|
| Why Prometheus? | Pull-based, mature ecosystem, dead simple for internal services. Micrometer bridges to it with zero code. |
| Push vs pull? | Pull = server-driven, LB-integrated, simple. Push = client-driven, firewall-friendly, needed for ephemeral instances. |
| Why Micrometer? | Vendor-neutral facade — same code emits to Prometheus / OTLP / Datadog / CloudWatch. Ships with Spring Boot Actuator. |
| Difference between Counter, Gauge, Timer? | Counter = monotonic (event counts). Gauge = current value, up/down (queue size, connections). Timer = histogram + count of durations. |
| Why `_total` suffix on Prometheus counter names? | Prometheus convention. Micrometer exposition auto-appends. Query `foo_total`, use `rate(foo_total[5m])` for per-second. |
| Query for p99? | `histogram_quantile(0.99, sum(rate(spring_cloud_gateway_requests_seconds_bucket[5m])) by (le, route_id))`. `by (le, ...)` required — quantile is computed across buckets. |
| Why aggregate before quantile? | Computing quantile per pod then averaging is mathematically wrong. Must sum buckets, THEN compute quantile. |
| High-cardinality risk? | Every unique tag combination = new time series. Millions of series → Prometheus OOM. Only use small enum-shaped tags. |
| How to alert on CB open? | `resilience4j_circuitbreaker_state{state="open"} == 1 for 5m` in Alertmanager. |
| Scraping through a load balancer? | Don't. Prometheus scrapes each pod directly via service discovery (Kubernetes, Consul). LB round-robin would jumble series. |
| Long-term storage? | Local TSDB: days. Long-term: remote-write to Thanos / Cortex / Mimir / Grafana Cloud. |
| Metrics vs logs vs traces? | Metrics = pre-aggregated, cheap to query, fixed dimensions. Logs = per-event, high volume, expensive to aggregate. Traces = request path, sampled. Three pillars, different questions. |
| Custom metric perf impact? | Counter increment = one atomic long. Nanoseconds. Timer with histogram = a few μs for buckets. Free at any realistic scale. |
| Why the `application` global tag? | Distinguishes multiple Spring Boot apps sharing the same Prometheus. Without it, `jvm_memory_used_bytes` from gateway and auth-server look identical. |
| How does Micrometer avoid re-creating meters? | Internal ConcurrentHashMap keyed by name+tags. Repeated `Counter.builder(...).register(reg)` returns the same instance. |

---

## 12. Common pitfalls (interview probes)

1. **Tagging with high-cardinality values** — `user_id`, `correlation_id`, `timestamp` all bomb the TSDB. Millions of series. Only use finite enums.
2. **Forgetting `rate()` on counters** — raw counter monotonically increases. `rate(foo_total[5m])` for per-second; `increase(foo_total[1h])` for absolute count in a window.
3. **`histogram_quantile` without `sum by (le)`** — computes quantile per time series then averages. Wrong answer. Must aggregate buckets first.
4. **Not enabling `percentiles-histogram`** — histograms need buckets. Without config, you get only Timer.totalTime + count. No quantiles possible.
5. **Scraping via load balancer** — each scrape hits a different pod → jumbled series. Scrape pods directly.
6. **Not adding `application` global tag** — multiple Spring apps look identical in Prometheus.
7. **Grafana panel with mixed units** — bytes + milliseconds on one axis is nonsense. Two panels or two Y-axes.
8. **Metric name style** — Prometheus community: `snake_case`. Micrometer accepts `dot.case` and converts. Stick to one style per project.

---

## 13. Extensions (parked)

- **OpenTelemetry export** — swap `micrometer-registry-prometheus` for `micrometer-registry-otlp`. Same custom metric code. Ship to any OTel backend.
- **Grafana alerts** — inline in the dashboard JSON, or via Alertmanager rules file loaded by Prometheus.
- **Loki + Tempo** — logs and traces alongside metrics in Grafana. Full observability stack.
- **Distributed tracing** — you already have Zipkin wired. Adding a Grafana Tempo datasource unifies traces + metrics via metric-linked exemplars.
- **SLO panels** — define SLIs (success rate, latency), plot burn rate vs error budget. Google SRE style.
- **Business metrics** — order creation rate, payment success rate, admin action rate. Beyond infra.
- **Anomaly detection** — Grafana ML plugin, or export to Prometheus Anomaly Detection tools.
- **Multi-region view** — federated Prometheus for cross-region aggregation.
- **Cost of high-cardinality demo** — deliberately add a bad tag, watch memory grow, remove it. Great for teams learning what NOT to do.
- **User-defined dashboards** — set `allowUiUpdates: true` in provisioning; users can edit + save; regenerate JSON.
- **Downstream service scraping** — uncomment the auth-server / user-service sections in `prometheus.yml` after those services expose their own `/actuator/prometheus`.
