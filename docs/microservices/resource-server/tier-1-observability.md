# Tier 1 — Observability Rollout

Bring resource-server in line with every other service in the stack:
structured JSON logs, distributed tracing to Zipkin, Prometheus scrape
endpoint, populated `/actuator/info`. Zero business-logic changes — pure
infra consistency.

See also: [docs/microservices/observability-multi-service.md](../observability-multi-service.md)
for the across-the-stack view.

---

## 1. What "observability" means here

Three distinct telemetry streams:

```
┌────────────────────────────────────────────────────────────────────────┐
│  Stream       Example data                       Shipped to            │
│  ─────────   ─────────────────────────────────  ──────────────────    │
│  Logs         "GET /api/hello 200 42ms"          stdout → ELK/CloudWatch│
│               (plus traceId/spanId/correlationId                       │
│               as top-level fields in JSON)                             │
│                                                                        │
│  Metrics      p99 latency, request count,        /actuator/prometheus │
│               JVM heap, GC pause                 → Prometheus → Grafana│
│                                                                        │
│  Traces       Timeline of spans across services  /actuator/*          │
│               per request                        → Zipkin             │
└────────────────────────────────────────────────────────────────────────┘
```

Metadata (build version, git commit, feature flags) comes from a fourth
pseudo-stream: `/actuator/info`. Not "observability" strictly, but it
answers "which code is running right now?" during incidents.

---

## 2. Before vs after

```
┌──────────────────────────────────────────────────────────────────────────┐
│  Piece                        Before                 After               │
│  ──────────────────────────  ────────────────────   ──────────────────  │
│  Logs                        text, no MDC            JSON + traceId +    │
│                                                       spanId + corrId     │
│                                                                          │
│  Tracing deps                none                    micrometer + Brave  │
│                                                       (via common-lib)    │
│                                                                          │
│  Zipkin export               none                    localhost:9411      │
│                                                                          │
│  /actuator/info              {}                      build + git + app   │
│                                                       + java + os        │
│                                                                          │
│  Prometheus scrape           not exposed             /actuator/prometheus│
│                                                                          │
│  Correlation-ID filter       absent                  auto-wired from     │
│                                                       common-lib          │
│                                                                          │
│  logback config              empty / default         JSON + text profiles│
│                                                                          │
│  Config format               .properties             .yml (consistency   │
│                                                       with other services)│
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 3. How the pieces fit together

```
Browser                              resource-server :8096
  │                                    │
  │ GET /api/hello                     │
  │ Authorization: Bearer eyJhbG...    │
  │ X-Correlation-Id: cid-123          │   (if gateway forwarded one)
  ▼                                    ▼
                                 ┌─────────────────────────────────────────┐
                                 │ Servlet filter chain                    │
                                 │                                         │
                                 │  CorrelationIdFilter (from common-lib)  │
                                 │     reads X-Correlation-Id, or UUID()   │
                                 │     puts into MDC, echoes response hdr  │
                                 │                                         │
                                 │  Micrometer Tracing filter              │
                                 │     reads traceparent, or new trace     │
                                 │     puts traceId + spanId into MDC      │
                                 │                                         │
                                 │  Spring Security filter chain           │
                                 │     JWT validate → SecurityContext      │
                                 │                                         │
                                 │  Dispatcher → ApiController.hello()     │
                                 │     log.info("GET /api/hello sub={}")   │
                                 │                                         │
                                 │  Response written                       │
                                 └─────────────────────────────────────────┘
                                     │              │              │
                                     ▼              ▼              ▼
                              JSON log line  Prom scrape      Zipkin span
                              with MDC       (next 5s)        (batched)
                              fields         /actuator/        → :9411
                              → stdout       prometheus
```

Three telemetry streams; one request produces output in all three.

---

## 4. What common-lib brings transitively

`common-lib` is one dependency. Pulling it in replaces 5+ explicit deps:

```
<dependency>
    <groupId>com.example</groupId>
    <artifactId>common-lib</artifactId>
    <version>${project.version}</version>
</dependency>

   ↓ transitively adds:
spring-boot-starter-actuator            (health, info, metrics endpoints)
micrometer-tracing-bridge-brave          (W3C tracing)
zipkin-reporter-brave                    (ship spans to Zipkin)
micrometer-registry-prometheus           (/actuator/prometheus)
logstash-logback-encoder                 (JSON log format)
spring-boot-autoconfigure                (CorrelationIdAutoConfiguration)
```

Plus the pre-existing `CorrelationIdFilter` + its auto-config class, so
every service that depends on common-lib automatically registers the filter
with highest-precedence ordering.

---

## 5. The logback include trick

Instead of each service maintaining its own `logback-spring.xml`, common-lib
ships `logback-observability.xml` as a classpath resource. Each service's
file is 3 lines:

```xml
<!-- resource-server/src/main/resources/logback-spring.xml -->
<configuration>
    <include resource="logback-observability.xml"/>
</configuration>
```

`logback-observability.xml` provides:
- JSON appender (Logstash encoder) with MDC keys `traceId`, `spanId`, `correlationId`
- Text appender for the `local` profile (readable in a dev terminal)
- Profile-driven root logger: default → JSON, `local` → text
- Framework noise tuning (Netty WARN, Hibernate SQL INFO, etc.)

Updating any of these updates every service at once.

---

## 6. /actuator/info — four InfoContributor sources

```yaml
management:
  info:
    env: { enabled: true }     # reads top-level info.* from application.yml
    build: { enabled: true }   # reads META-INF/build-info.properties
    git: { mode: full, ...}    # reads META-INF/git.properties
    java: { enabled: true }    # JVM vendor + version
    os: { enabled: true }      # OS name + version
```

The `build-info.properties` + `git.properties` files are generated at
build time by two Maven plugins — **inherited** from the parent pom's
`pluginManagement`, so each service's pom just declares `<plugin>` without
version or configuration:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
        <plugin>
            <groupId>io.github.git-commit-id</groupId>
            <artifactId>git-commit-id-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

Expected `/actuator/info` output after `mvn clean install`:

```json
{
  "app": {
    "name": "Resource Server",
    "description": "Minimal OAuth2 resource server — reference implementation",
    "port": "8096"
  },
  "contact": { "docs": "docs/microservices/resource-server/README.md" },
  "build": {
    "artifact": "resource-server",
    "name": "resource-server",
    "time": "2026-10-01T...",
    "version": "1.0.0-SNAPSHOT",
    "group": "com.example"
  },
  "git": {
    "branch": "main",
    "commit": { "id": { "abbrev": "3f9a8b7", "full": "..." }, "time": "..." },
    "dirty": "false"
  },
  "java": { "vendor": "...", "version": "17.0.9", "runtime": {...} },
  "os":   { "name": "Mac OS X", "arch": "aarch64", "version": "14.0" }
}
```

Diagnostic gold during incidents — curl /actuator/info, verify version +
git SHA match what you expect to be running.

---

## 7. Prometheus scrape wiring

Prometheus (running in `api-gateway/docker-compose.observability.yml`) now
scrapes resource-server on port 8096:

```yaml
# api-gateway/observability/prometheus/prometheus.yml
scrape_configs:
  # ... other jobs ...
  - job_name: resource-server
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: [host.docker.internal:8096]
        labels: { service: resource-server, env: local }
```

Verify in Prometheus UI:

```
http://localhost:9090/targets
```

All six services should show state `UP`.

In Grafana, filter any JVM / HTTP panel by `application="resource-server"`
to see this service in isolation.

---

## 8. End-to-end verification

```bash
# 1. Rebuild (generates build-info.properties + git.properties)
cd /Users/bhanupratap/My/my-microservices
mvn -pl common-lib,resource-server clean install -DskipTests

ls resource-server/target/classes/META-INF/
# → build-info.properties  git.properties

# 2. Start observability stack
docker-compose -f api-gateway/docker-compose.observability.yml up -d

# 3. Start auth-server + resource-server
mvn -pl auth-server     spring-boot:run    # :9010
mvn -pl resource-server spring-boot:run    # :8096

# 4. /actuator/info populated
curl -s http://localhost:8096/actuator/info | jq
# → full payload with build + git + app + java + os

# 5. Prometheus can scrape
curl -s http://localhost:8096/actuator/prometheus | head -20
# → # HELP ... many metrics

# 6. Make a request with a correlation id
TOKEN=$(curl -s -u admin:admin123 \
  -d "grant_type=client_credentials&scope=read" \
  http://localhost:9010/oauth2/token | jq -r .access_token)

CORR="cid-$(date +%s)"
curl -s -H "Authorization: Bearer $TOKEN" \
     -H "X-Correlation-Id: $CORR" \
     http://localhost:8096/api/hello
# → "Hello, admin"

# 7. Response echoes the correlation id
curl -i -H "Authorization: Bearer $TOKEN" \
     -H "X-Correlation-Id: $CORR" \
     http://localhost:8096/api/hello 2>&1 | grep -i correlation
# → X-Correlation-Id: cid-...

# 8. The log line in resource-server stdout contains the correlation id
#    (switch to local profile for readable text format)
mvn -pl resource-server spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"
# then grep for the id

# 9. Zipkin shows a trace
open http://localhost:9411
# Search: service=resource-server
# → trace with 1+ span for /api/hello
```

---

## 9. Interview soundbites

| Question | Answer |
|---|---|
| Why structured JSON logs? | ELK / Datadog / CloudWatch ingest JSON natively. Grep-friendly for humans via `jq`. MDC keys become top-level fields → queryable like any other dimension. |
| What's in MDC and how? | `traceId`, `spanId` from Micrometer Tracing. `correlationId` from `CorrelationIdFilter` (common-lib). All thread-local on servlet apps; survives Reactor hops via context-propagation module on reactive apps. |
| Why common-lib? | One place to version observability deps. Change `micrometer-tracing-bridge-brave` → `-otel` in common-lib and every service migrates. No per-service pom edits. |
| Why the `include` trick in logback? | Same reason — one XML definition shared across services. Services only add service-specific loggers (e.g. `org.springframework.security=DEBUG` in dev). |
| /actuator/info — who reads it? | Humans during incidents (`curl /actuator/info` → confirm which version is running). Also CI pipelines doing smoke tests after deploy. Also Grafana via proxy datasource for a "service inventory" panel. |
| Why both build-info AND git-info? | build-info = Maven version + build time. git-info = branch + SHA + commit time + dirty flag. Together they answer "what source produced the artifact that is running now?" |
| Prometheus scrape interval? | 5s in dev (we chose aggressive for demo responsiveness). 15–30s in prod. |
| Zipkin sampling rate? | 100% in dev. 5–10% in prod. `management.tracing.sampling.probability` controls it. |
| Does CorrelationIdFilter work on reactive services? | No — this is the servlet one. Reactive services (api-gateway) use `CorrelationIdWebFilter` which does the same thing with `WebFilter` + Reactor Context. Both auto-wired. |

---

## 10. Common pitfalls

1. **Forgetting to add `common-lib` dep** — logs stay text-format, no tracing. Services look fine but produce no telemetry.
2. **Wrong Zipkin URL** — spans silently discarded (reporter batches + swallows network errors). Verify `/actuator/health` has `zipkin` component UP.
3. **100% sampling in prod** — Zipkin ingests millions of spans/day. Scale back to 1–10%.
4. **`management.endpoints.web.exposure.include` missing `prometheus`** — scrape job returns 404. Common after copy-pasting from an older service's config.
5. **Build-info not regenerated** — if you only run `mvn spring-boot:run` without a prior `mvn install`, `build-info.properties` is stale. CI should always `clean install` first.
6. **Correlation ID lost across async boundaries** — servlet filter restores MDC after chain.doFilter but NOT across @Async methods. Need `TaskDecorator` for ThreadPoolTaskExecutor. Parked.
7. **Mixing .properties and .yml** — Spring reads both but it's confusing. Pick one per service. We standardised on .yml.

---

## 11. Extensions parked

- **Loki + Promtail** — ship JSON logs to Loki, query alongside Prometheus metrics in Grafana. Same single tool for both signals.
- **OpenTelemetry replacement for Brave** — swap one dep in common-lib, same code emits OTLP → any OTel backend (Tempo, Jaeger, Datadog, Honeycomb).
- **Grafana Tempo** — trace storage; metric exemplars link latency spikes directly to the slow trace.
- **Micrometer custom metrics** — if you wire real business logic in, add counters like `resource.hello.called` to show real traffic patterns.
- **SpanTag the authenticated principal** — enrich trace spans with `user=alice` tag so Zipkin shows who caused a slow trace. Needs a custom `HandlerInterceptor`.
