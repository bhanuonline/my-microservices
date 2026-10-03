# Multi-Service Observability — Rollout

The api-gateway had full observability wired ([logging-tracing-info.md](api-gateway/logging-tracing-info.md)).
This pass extends the same primitives — JSON logs, distributed tracing to
Zipkin, `/actuator/info` with build + git + feature flags, Prometheus
scraping — to every other service in the stack.

Before: only api-gateway emitted traces. Spans stopped at the gateway; any
work done in auth-server or user-service was an invisible gap in the trace
timeline.

After: end-to-end traces span `browser → gateway → user-service → …` with
each hop carrying the same `traceId`. Every service exposes JSON logs +
`/actuator/info` + `/actuator/prometheus` on the same shape.

---

## 1. Design decision — shared starter via `common-lib`

Every service already depends on `common-lib` (except `eureka-server` and
`auth-server`, which we handle separately). Adding the observability deps
there gives every consumer:

```
common-lib pom now transitively provides:
  spring-boot-starter-actuator             health / info / metrics infra
  micrometer-tracing-bridge-brave          W3C tracing → Zipkin
  zipkin-reporter-brave                    HTTP span exporter
  micrometer-registry-prometheus           /actuator/prometheus endpoint
  logstash-logback-encoder                 JSON log encoder
  spring-boot-autoconfigure                for the CorrelationIdAutoConfiguration
```

And ships two artifacts services can pick up automatically:

- **`CorrelationIdAutoConfiguration`** — registers the existing
  `CorrelationIdFilter` (servlet) as a `FilterRegistrationBean` at highest
  precedence, so every request gets `X-Correlation-Id` in headers + MDC
  before Spring Security runs.
- **`logback-observability.xml`** — a shared logback fragment consumers
  include with one line.

The auto-configuration is registered via:
```
common-lib/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```
Spring Boot 3's replacement for the old `spring.factories`.

---

## 2. What each service now has

```
┌───────────────────────────────────────────────────────────────────────────┐
│                    JSON  Tracing  /info  Prom  CorrId  build-info         │
│  api-gateway        ✅    ✅       ✅     ✅    ✅      ✅                  │
│  auth-server        ✅    ✅       ✅     ✅    ✅      ✅                  │
│  user-service       ✅    ✅       ✅     ✅    ✅      ✅                  │
│  product-service    ✅    ✅       ✅     ✅    ✅      ✅                  │
│  order-service      ✅    ✅       ✅     ✅    ✅      ✅                  │
│  eureka-server      ✅    ⬜        ✅     ✅    N/A     ✅                  │
└───────────────────────────────────────────────────────────────────────────┘
```

**Eureka-server intentionally skips tracing.** It's a control-plane service —
every service polls it every 30s for the registry. Tracing those polls would
spam Zipkin with useless spans. Structured logs + Prometheus metrics is
enough. Correlation-id filter is also skipped since Eureka doesn't consume
common-lib.

---

## 3. Per-service change summary

### `common-lib` (the shared piece)

```
common-lib/
├── pom.xml                                                   (+ 5 obs deps)
└── src/main/
    ├── java/com/example/common/logging/
    │   ├── CorrelationIdFilter.java                          (existing — reformatted)
    │   └── CorrelationIdAutoConfiguration.java               (NEW)
    └── resources/
        ├── logback-observability.xml                         (NEW — shared JSON+text config)
        └── META-INF/spring/
            └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
                                                              (NEW — registers CorrelationIdAutoConfiguration)
```

### `auth-server`

- **pom.xml** — added `common-lib` dep, `build-info` execution, `git-commit-id-maven-plugin`
- **application.properties** — added `management.tracing.*`, `management.zipkin.*`, `management.metrics.tags.application`, `management.info.*`, `management.endpoints.web.exposure.include=health,info,prometheus,metrics,env,loggers`, `info.app.*`
- **logback-spring.xml** — NEW, `<include resource="logback-observability.xml"/>` + Spring Security logger tuning per profile

### `user-service`, `product-service`, `order-service`

Same pattern:
- **pom.xml** — `build-info` + `git-commit-id-maven-plugin` executions
- **logback-spring.xml** — replaced verbose config with `<include resource="logback-observability.xml"/>`
- **application.yml** — added `management.info.*` sections, extended `exposure.include` with `prometheus,metrics,env,loggers`, added `management.metrics.tags.application` + histogram config, added top-level `info.app.*` block

### `eureka-server`

- **pom.xml** — added `logstash-logback-encoder` + `micrometer-registry-prometheus` (NOT common-lib to keep this control-plane service light), `build-info` + `git-commit-id` executions
- **application.yml** — added `management.info.*`, `management.endpoints.exposure=health,info,prometheus,metrics`, `info.app.*`
- **logback-spring.xml** — NEW, self-contained JSON+text config with Eureka log tuning

### `api-gateway/observability/prometheus/prometheus.yml`

Enabled scrape jobs for all 5 services (auth-server, eureka-server, user-service, product-service, order-service) alongside api-gateway.

---

## 4. End-to-end trace flow

```
Browser
   │  ── GET /api/v1/users/me  ─────────────────────────────────────▶
   │
   ▼
┌──────────────────────────────────────────────────────────────────┐
│ api-gateway :8080                                                 │
│                                                                   │
│  CorrelationIdWebFilter → picks or generates X-Correlation-Id     │
│  Micrometer Tracing → creates traceId + rootSpanId                │
│                                                                   │
│  Log: {"traceId":"abc123","spanId":"span-a","correlationId":"cid-1",│
│        "logger_name":"...","service":"api-gateway", ...}          │
│                                                                   │
│  Outbound HTTP call carries:                                     │
│    traceparent: 00-abc123-span-a-01                              │
│    X-Correlation-Id: cid-1                                        │
└──────────────────────────────────────────────────────────────────┘
   │
   ▼
┌──────────────────────────────────────────────────────────────────┐
│ user-service :8090                                                │
│                                                                   │
│  Micrometer Tracing (from bridge-brave) reads traceparent →       │
│    same traceId, new childSpanId                                  │
│  CorrelationIdFilter (from common-lib) reads X-Correlation-Id →   │
│    same cid                                                       │
│                                                                   │
│  Log: {"traceId":"abc123","spanId":"span-b","correlationId":"cid-1",│
│        "logger_name":"...","service":"user-service", ...}         │
└──────────────────────────────────────────────────────────────────┘
   │
   ▼
Zipkin :9411 sees BOTH spans linked under trace abc123 → single timeline
ELK / Datadog can query {"traceId":"abc123"} → get every log line from every service for this request
```

This is what "distributed tracing" actually means. The gateway alone couldn't
show it before because spans died there.

---

## 5. Verification

### 5.1 Rebuild everything

```bash
cd /Users/bhanupratap/My/my-microservices
mvn -pl common-lib,api-gateway,auth-server,user-service,product-service,order-service,eureka-server clean install -DskipTests
```

Expected — for each service that got build-info + git-commit-id:
```
[INFO] --- git-commit-id-maven-plugin:7.0.0:revision (get-the-git-infos) ...
[INFO] --- spring-boot-maven-plugin:...:build-info (build-info) ...
```

And in `target/classes/META-INF/` for each service:
```
build-info.properties
git.properties
```

### 5.2 Boot the observability stack

```bash
docker-compose -f api-gateway/docker-compose.observability.yml up -d
docker ps | grep -E "prometheus|grafana|zipkin"
```

### 5.3 Boot every service

```bash
# In separate terminals:
mvn -pl eureka-server   spring-boot:run    # :8761
mvn -pl auth-server     spring-boot:run    # :9010 (spring.application.name=authservice)
mvn -pl user-service    spring-boot:run    # :8090
mvn -pl product-service spring-boot:run    # :8091
mvn -pl order-service   spring-boot:run    # :8092
mvn -pl api-gateway     spring-boot:run    # :8080
```

### 5.4 Verify /actuator/info per service

```bash
for p in 8761 9010 8090 8091 8092 8080; do
  echo "=== :$p ==="
  curl -s http://localhost:$p/actuator/info | jq '{app: .app.name, build: .build.version, git: .git.commit.id.abbrev}'
done
```

Every service should return non-empty values.

### 5.5 Verify Prometheus scrape targets

```bash
open http://localhost:9090/targets
```

All 6 jobs should show state `UP`. If any is `DOWN`:
- Service not running on the expected port
- `/actuator/prometheus` not exposed (check `management.endpoints.web.exposure.include`)

### 5.6 Verify distributed tracing end-to-end

```bash
TOKEN=$(curl -s -u admin:admin123 \
  -d "grant_type=client_credentials&scope=read" \
  http://localhost:9010/oauth2/token | jq -r .access_token)

# Trigger a full multi-hop request
curl -s -H "Authorization: Bearer $TOKEN" \
     -H "X-Correlation-Id: multi-svc-test-$(date +%s)" \
     http://localhost:8080/api/v1/users/me > /dev/null
```

Open Zipkin `http://localhost:9411`:
- Search: service = `api-gateway`, look for `GET /api/v1/users/me`
- Click the trace → timeline shows spans from `api-gateway` AND `user-service`
- Same `traceId` links them
- Total duration = sum of all spans

Search logs for the correlation id:
```bash
# If gateway logs to stdout:
mvn -pl api-gateway spring-boot:run 2>&1 | grep multi-svc-test
```

You should see the same `correlationId` in log lines from BOTH gateway AND user-service.

### 5.7 Grafana — per-service dashboards

Existing `gateway-overview` dashboard shows only api-gateway metrics. Prometheus is now scraping all 6 services — you can:
- Create a new dashboard using the built-in Grafana Spring Boot dashboard (https://grafana.com/grafana/dashboards/6756-jvm-micrometer/)
- Or filter existing panels by `application` tag: `jvm_memory_used_bytes{application="user-service"}`

---

## 6. Failure modes & mitigations

| Scenario | Behavior | Mitigation |
|---|---|---|
| Zipkin not running | Spans silently discarded, no error | `docker-compose -f api-gateway/docker-compose.observability.yml up -d` |
| Wrong Zipkin URL | Spans silently discarded (Brave reporter batches + drops on error) | Verify with `curl http://localhost:9411/health` |
| Service scrape target DOWN in Prometheus | Metrics from that service missing | Check service is running + `/actuator/prometheus` exposed |
| `build-info.properties` not generated | `/actuator/info.build.*` empty | Verify `<execution><id>build-info</id>...` in service's pom |
| `git.properties` not generated | `/actuator/info.git.*` empty | Requires `.git` directory. `failOnNoGitDirectory: false` prevents build failure but info.git stays empty |
| Correlation id absent in downstream logs | Filter not registered | Check service uses common-lib AND is servlet-based (`spring-boot-starter-web`, not `webflux`) |
| Traces missing spans from a service | That service missing tracing deps | Verify `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` on classpath (via common-lib or direct) |
| Log volume overwhelming in prod | 100% tracing sampling too much | Set `management.tracing.sampling.probability=0.1` (10%) or lower |
| Prometheus retention full | 1d retention default | Longer retention needs Thanos / remote-write; tune `--storage.tsdb.retention.time=` in compose |
| `application` tag missing from metrics | `management.metrics.tags.application` not set | Add per service — otherwise Prometheus can't distinguish JVM metrics across services |

---

## 7. Interview cheat-sheet

| Question | Answer |
|---|---|
| Why extract observability into a shared lib? | Avoids per-service dep duplication + config drift. All services get the same tracing/logging behaviour by depending on common-lib. |
| Why not Spring Boot Starter instead of common-lib? | Same mechanism (both use META-INF/spring/AutoConfiguration.imports). Just packaging convention. common-lib already existed; extending it kept the change small. |
| How does the CorrelationIdFilter get registered? | `@AutoConfiguration` class in common-lib listed in AutoConfiguration.imports → Spring Boot picks it up during context init → `FilterRegistrationBean` gets registered at HIGHEST_PRECEDENCE. |
| Why exclude Eureka from tracing? | Control-plane service — every service polls it every 30s for the registry. Tracing those polls = Zipkin flood, ~0 useful signal. |
| Servlet vs reactive CorrelationIdFilter? | Two implementations: `common.logging.CorrelationIdFilter` (servlet, auto-registered) and `apigateway.CorrelationIdWebFilter` (reactive, in api-gateway itself). Same header, same MDC key, different frameworks. |
| How do W3C traceparent headers propagate? | Micrometer Tracing's HTTP client instrumentation intercepts outbound calls (`WebClient`, `RestTemplate`, `RestClient`, Feign) and injects `traceparent` header. Downstream services extract it in their inbound instrumentation. |
| Where does /actuator/info.build come from? | `spring-boot-maven-plugin` `build-info` execution generates `META-INF/build-info.properties` at build time. `BuildInfoContributor` reads it at runtime. |
| Why generate git.properties? | Post-deploy sanity check: curl /actuator/info, verify git SHA matches what was deployed. Diagnostic gold during incidents. |
| Prod tracing sampling rate? | 1-10%. 100% (like dev) creates enormous span volume. `management.tracing.sampling.probability=0.05`. |
| Log correlation across services — how? | traceId (framework, W3C traceparent) + correlationId (our own, X-Correlation-Id). Both in MDC → both in every JSON log line → both queryable in ELK. |
| Prometheus scrape targets — service discovery vs static? | Static works for a fixed small stack. Kubernetes-native: use `kubernetes_sd_configs` to auto-discover pods. Consul: `consul_sd_configs`. |
| Cardinality risk from `application` tag? | Low — one value per service, ~6 total. Safe. Never tag with user_id / request_id / correlation_id — those explode cardinality. |
| Trace-metric linking (Prometheus exemplars)? | Extension. Attach traceId to histogram bucket samples → Grafana links slow request panels directly to their trace in Tempo/Zipkin. |

---

## 8. Common pitfalls

1. **Adding tracing deps to a service that doesn't have actuator** — nothing works. Fix: `spring-boot-starter-actuator` is required for tracing auto-config to fire. (common-lib now brings it.)
2. **Servlet filter registered before servlet init** — early registration works via `FilterRegistrationBean` at HIGHEST_PRECEDENCE. Native `@Component Filter` sometimes runs after Spring Security's filter chain wraps it.
3. **`application` tag missing** — Prometheus can't distinguish JVM metrics from different services. Set `management.metrics.tags.application=${spring.application.name}` per service.
4. **Zipkin dropping spans silently** — network issue between service and Zipkin. Enable Brave debug logs: `logging.level.zipkin2=DEBUG`.
5. **`build-info` execution not running** — must be inside `<executions>` block, not just plugin declaration. Common config mistake.
6. **git.properties missing after tarball build** — `.git` directory absent. `failOnNoGitDirectory: false` prevents build failure; info.git stays empty but everything else works.
7. **Reactive service using servlet CorrelationIdFilter** — won't be registered (guarded by `@ConditionalOnWebApplication(type = SERVLET)`). Reactive services need `WebFilter` implementation (api-gateway does this).
8. **traceId in span export ≠ traceId in log** — same string, but Zipkin displays as 32-char hex without dashes while some log formats include dashes. Standardize on hex.
9. **Downstream service missing tracing deps** — trace shows a gap where its work should be. Add `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` (or depend on common-lib).
10. **Exposing `/actuator/env` in prod** — leaks env vars including secrets. Restrict: `management.endpoint.env.show-values: NEVER`.

---

## 9. Extensions (parked)

- **OpenTelemetry instead of Brave** — swap `micrometer-tracing-bridge-brave` for `micrometer-tracing-bridge-otel` in common-lib. All services now export OTLP → any OTel backend (Tempo, Jaeger, Datadog, Honeycomb).
- **Grafana Tempo integration** — pull trace-metric exemplars into Grafana panels for direct link between p99 spike and its traces.
- **Kubernetes service discovery** — replace `static_configs` in prometheus.yml with `kubernetes_sd_configs` for auto-scraping.
- **Loki + Promtail** — ship JSON logs to Loki; query correlated with Prometheus metrics + Tempo traces in one Grafana UI.
- **Distributed correlation-id-first design** — every log line, every metric, every trace tagged with correlationId. Full request-scoped searchability.
- **Custom InfoContributor per service** — expose service-specific feature flags at `/actuator/info.features` (already done for api-gateway; extend to user-service, order-service, etc.).
- **PII scrubbing at logback filter level** — a Logback TurboFilter that redacts patterns before they hit the encoder. Already done for BodyLogging in the gateway; not for framework logs.
- **Async logging appender** — wrap LogstashEncoder with `AsyncAppender` for high-throughput scenarios. Prevents log I/O from stalling request threads.
- **Log-based alerts via Loki** — `count_over_time({service="user-service"} |= "ERROR" [5m]) > 10`. Great for catching spikes not visible in metrics.
