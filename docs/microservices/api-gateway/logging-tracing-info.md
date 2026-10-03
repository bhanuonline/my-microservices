# API Gateway — Logging, Tracing, /actuator/info

Three closely related observability primitives that were partially wired before
this pass. Now consolidated:

- **Logs** — structured JSON via Logstash encoder, MDC-enriched with `traceId` / `spanId` / `correlationId`. Local dev profile switches to human-readable text.
- **Tracing** — Micrometer Tracing + Brave → Zipkin at `:9411`. W3C `traceparent` propagates automatically to downstream services.
- **Info endpoint** — `/actuator/info` returns build version, git commit, JVM/OS, custom feature flags. Was empty; now populated.

Companion to [observability.md](observability.md) (metrics/Prometheus/Grafana).

---

## 1. What was already there vs what's new

```
┌──────────────────────────────────────────────────────────────────────────┐
│  Piece                          Before                After              │
│  ─────────────────────────    ──────────────       ──────────────       │
│  logback-spring.xml           minimal JSON        JSON + local profile   │
│                                                    + framework tuning    │
│                                                                          │
│  logging.pattern.level        set                  unchanged (works)     │
│                                                                          │
│  Micrometer Tracing dep       wired               unchanged              │
│  Zipkin reporter dep          wired               unchanged              │
│  Zipkin service               ✗ not running       ✅ docker-compose      │
│                                                                          │
│  CorrelationIdWebFilter       ✗ one line + buggy  ✅ readable, correct   │
│                                MDC pattern                                │
│                                                                          │
│  /actuator/info               ✗ empty {}          ✅ build + git + JVM   │
│                                                    + custom features     │
│                                                                          │
│  build-info.properties        ✗ not generated     ✅ Maven plugin execn  │
│  git.properties               ✗ not generated     ✅ git-commit-id plugin│
│                                                                          │
│  GatewayInfoContributor       ⬜ absent            ✅ feature flags       │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Logging

### JSON output shape

Every log line (default profile) emits:

```json
{
  "@timestamp": "2026-09-30T15:30:00.123Z",
  "@version": "1",
  "level": "INFO",
  "level_value": 20000,
  "logger_name": "com.example.apigateway.filter.IdempotencyKeyGatewayFilterFactory",
  "thread_name": "reactor-http-nio-3",
  "service": "api-gateway",
  "traceId": "3f9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c",
  "spanId": "1a2b3c4d5e6f7a8b",
  "correlationId": "d5c4b3a2-1234-5678-9abc-def012345678",
  "message": "Skipping idempotency cache for streaming response"
}
```

Ready for:
- **ELK** — Filebeat picks up stdout, Elasticsearch indexes JSON
- **Datadog / CloudWatch** — same, with their agents
- **Loki** — via Promtail

### MDC keys — how they get populated

```
traceId + spanId
  Set by Micrometer Tracing (via micrometer-tracing-bridge-brave).
  Populated automatically on every request. Propagated to downstream via
  W3C traceparent header.

correlationId
  Set by CorrelationIdWebFilter (this project). Reads/generates X-Correlation-Id
  header, publishes into Reactor Context, MDC picks it up at log-emission time.

All three thread-safe across Reactor's thread hops because Micrometer's
Context Propagation module lifts Reactor Context → MDC on demand.
```

### Local dev — text output

Default logs are JSON. That's hard to read manually. Switch to human-readable:

```bash
mvn -pl api-gateway spring-boot:run \
  -Dspring-boot.run.arguments="--spring.profiles.active=local"
```

Output changes to:
```
15:30:00.123 INFO  [3f9a8b...,1a2b3c...,d5c4b3...] c.e.a.filter.IdempotencyKeyGatewayFilterFactory - Skipping idempotency cache for streaming response
```

Same MDC keys inline, no jq needed.

### Framework noise

Default log level is INFO. logback-spring.xml pins some framework loggers:

```xml
<logger name="io.netty" level="WARN"/>          <!-- Netty INFO is chatty -->
<logger name="reactor.netty.http.client" level="INFO"/>
<logger name="io.r2dbc" level="INFO"/>
```

To debug a specific issue, temporarily override at runtime:
```bash
curl -X POST http://localhost:8080/actuator/loggers/org.springframework.cloud.gateway.filter.factory.RetryGatewayFilterFactory \
     -H "Content-Type: application/json" \
     -d '{"configuredLevel":"DEBUG"}'
```
(Requires the `loggers` actuator endpoint to be exposed — add `loggers` to `management.endpoints.web.exposure.include`.)

### CorrelationIdWebFilter — the reactive MDC fix

The old filter did:

```java
return chain.filter(exchange)
    .contextWrite(ctx -> ctx.put(MDC_KEY, correlationId))   // correct
    .doOnEach(signal -> MDC.put(MDC_KEY, correlationId))     // fragile
    .doFinally(sig -> MDC.remove(MDC_KEY));                  // fragile
```

Problem: `MDC.put()` sets ThreadLocal state. Reactor hops threads between
operators. `doOnEach` fires on whatever thread runs a signal — which may not
be the thread that formats the log line an instant later.

The fix: **rely on Reactor Context + Micrometer's context propagation**.
Spring Boot 3 with Micrometer wires this automatically. Just:

```java
return chain.filter(mutated)
    .contextWrite(ctx -> ctx.put(MDC_KEY, correlationId))
    // Belt-and-suspenders for libraries that DON'T use context propagation:
    .doOnSubscribe(sub -> MDC.put(MDC_KEY, correlationId))
    .doFinally(sig -> MDC.remove(MDC_KEY));
```

`doOnSubscribe` runs once per subscription (per request), not per signal — much
safer. And the context-write is the load-bearing part; MDC is fallback.

---

## 3. Distributed tracing

### The dep chain

```
micrometer-tracing-bridge-brave   → API implementation (Brave = Zipkin's tracer)
zipkin-reporter-brave              → sends spans to Zipkin over HTTP
```

Both were already in the pom. `spring-boot-starter-actuator` provides the
auto-config that ties them into WebFlux request handling.

### Config

```yaml
management:
  tracing:
    sampling:
      probability: 1.0     # 100% sampling — dev only. Prod: 0.05-0.10.
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans
```

### Where spans come from

Auto-instrumented — you don't create spans manually:

- **Incoming request** → root span on the gateway
- **`WebClient` / gateway proxying** → outbound HTTP span (child of root)
- **R2DBC queries** → DB span (child of whichever operation triggered them)
- **Redis calls via `ReactiveRedisTemplate`** → Redis span

Custom manual spans (if you want them):

```java
@Autowired Tracer tracer;

Span span = tracer.nextSpan().name("my-custom-operation").start();
try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
    // work
} finally {
    span.end();
}
```

Rarely needed — the auto spans usually cover it.

### W3C traceparent header

Every outgoing request from the gateway to a downstream service includes:

```
traceparent: 00-3f9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c-1a2b3c4d5e6f7a8b-01
             │  │                                │                │
             │  │                                │                └─ flags (01 = sampled)
             │  │                                └─ parent span id
             │  └─ trace id
             └─ version
```

Downstream services with the same Micrometer Tracing config extract this and
continue the trace. If a downstream service DOESN'T have tracing wired, its
work is invisible — you see a gap in the trace timeline.

**⚠ Currently only the gateway has tracing configured.** Downstream services
(auth-server, user-service, etc.) need the same deps + config for
end-to-end traces. See extension below.

### Viewing traces in Zipkin

```
http://localhost:9411
```

- Search by service name, operation, trace id, or tag
- Click a trace → timeline of all spans across services
- Copy `traceId` from any log line → paste into Zipkin to find that request's trace

---

## 4. /actuator/info

Was returning `{}` before this pass. Now returns:

```json
{
  "app": {
    "name": "API Gateway",
    "description": "Production-shaped Spring Cloud Gateway with resilience, admin, and observability",
    "encoding": "UTF-8"
  },
  "contact": {
    "team": "platform-eng",
    "docs": "docs/microservices/api-gateway/README.md"
  },
  "build": {
    "artifact": "api-gateway",
    "name": "api-gateway",
    "time": "2026-09-30T15:30:00.000Z",
    "version": "1.0.0-SNAPSHOT",
    "group": "com.example"
  },
  "git": {
    "branch": "main",
    "commit": {
      "id": {
        "full": "3f9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c",
        "abbrev": "3f9a8b7"
      },
      "time": "2026-09-30T14:00:00Z",
      "message": {
        "short": "feat: canary routing done"
      }
    },
    "dirty": "false"
  },
  "java": {
    "vendor": "Eclipse Adoptium",
    "version": "17.0.9",
    "runtime": {
      "name": "OpenJDK Runtime Environment",
      "version": "17.0.9+9"
    }
  },
  "os": {
    "name": "Mac OS X",
    "version": "14.0",
    "arch": "aarch64"
  },
  "features": {
    "rateLimit": true,
    "circuitBreaker": true,
    "retry": true,
    "timeout": true,
    "idempotency": true,
    "responseCache": true,
    "bodyLogging": true,
    "apiKey": true,
    "dynamicRoutes": true,
    "dynamicRoutesPubsub": true,
    "cors": true,
    "canary": true,
    "adminUi": "REACT"
  }
}
```

### How each section gets populated

```
app.*, contact.*
   info: block at top of application.yml + management.info.env.enabled=true

build.*
   spring-boot-maven-plugin `build-info` execution → META-INF/build-info.properties
   BuildInfoContributor bean auto-registers when the file is present

git.*
   git-commit-id-maven-plugin `revision` execution → META-INF/git.properties
   GitInfoContributor bean auto-registers when the file is present
   mode: full (branch, time, dirty) vs simple (SHA only)

java.*, os.*
   Built-in InfoContributor beans, enabled via management.info.java.enabled=true / os.enabled=true

features.*
   GatewayInfoContributor (this project) — reads @Value on each feature flag
```

### Feature flags snapshot — why it matters

`/actuator/info.features` shows which gateway layers are ACTUALLY enabled at
runtime. Useful for:

- **Incident response**: someone flipped `gateway.canary.enabled=false` → info endpoint proves it
- **Env drift detection**: compare dev vs staging vs prod `/actuator/info.features` outputs
- **Post-deploy sanity check**: after a deploy, curl `/actuator/info` and diff against expected state
- **Documentation**: interviewers can see the feature list at a glance

---

## 5. Config changes

### `pom.xml`

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <executions>
                <execution>
                    <id>build-info</id>
                    <goals>
                        <goal>build-info</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>

        <plugin>
            <groupId>io.github.git-commit-id</groupId>
            <artifactId>git-commit-id-maven-plugin</artifactId>
            <version>7.0.0</version>
            <executions>
                <execution>
                    <id>get-the-git-infos</id>
                    <goals>
                        <goal>revision</goal>
                    </goals>
                    <phase>initialize</phase>
                </execution>
            </executions>
            <configuration>
                <failOnNoGitDirectory>false</failOnNoGitDirectory>
                <generateGitPropertiesFile>true</generateGitPropertiesFile>
                <includeOnlyProperties>
                    <includeOnlyProperty>^git.branch$</includeOnlyProperty>
                    <includeOnlyProperty>^git.commit.id$</includeOnlyProperty>
                    <includeOnlyProperty>^git.commit.id.abbrev$</includeOnlyProperty>
                    <includeOnlyProperty>^git.commit.time$</includeOnlyProperty>
                    <includeOnlyProperty>^git.commit.message.short$</includeOnlyProperty>
                    <includeOnlyProperty>^git.dirty$</includeOnlyProperty>
                </includeOnlyProperties>
            </configuration>
        </plugin>
    </plugins>
</build>
```

### `application.yml`

```yaml
info:
  app:
    name: API Gateway
    description: Production-shaped Spring Cloud Gateway with resilience, admin, and observability
  contact:
    team: platform-eng
    docs: docs/microservices/api-gateway/README.md

management:
  info:
    env:   { enabled: true }
    build: { enabled: true }
    git:   { mode: full, enabled: true }
    java:  { enabled: true }
    os:    { enabled: true }
  endpoint:
    info:
      env:
        enabled: true
```

### `docker-compose.observability.yml`

Added Zipkin service:
```yaml
zipkin:
  image: openzipkin/zipkin:latest
  container_name: gateway-zipkin
  ports: ["9411:9411"]
  environment:
    - STORAGE_TYPE=mem
    - MEM_STORE_MAX_SPANS=1000000
```

---

## 6. Files changed

```
api-gateway/
├── pom.xml                                                  (+ build-info exec + git-commit-id plugin)
├── docker-compose.observability.yml                         (+ zipkin service)
└── src/main/
    ├── java/com/example/apigateway/
    │   ├── CorrelationIdWebFilter.java                       (rewritten — readable + correct MDC)
    │   └── metrics/
    │       └── GatewayInfoContributor.java                   (NEW — features flags at /actuator/info)
    └── resources/
        ├── application.yml                                   (+ info.* block + management.info.*)
        └── logback-spring.xml                                (+ local profile + framework noise tuning)

docs/microservices/api-gateway/
└── logging-tracing-info.md                                   (this file)
```

---

## 7. Verification

```bash
# 1. Rebuild — should generate META-INF/build-info.properties + git.properties
cd /Users/bhanupratap/My/my-microservices/api-gateway
mvn clean install
ls target/classes/META-INF/
# → build-info.properties  git.properties

# 2. Start observability stack (now includes Zipkin)
docker-compose -f docker-compose.observability.yml up -d
docker ps | grep -E "prometheus|grafana|zipkin"

# 3. Boot the gateway (default profile → JSON logs)
mvn -pl api-gateway spring-boot:run

# 4. /actuator/info — full payload
curl -s http://localhost:8080/actuator/info | jq

# → app, contact, build, git, java, os, features all populated

# 5. Feature flag snapshot only
curl -s http://localhost:8080/actuator/info | jq .features

# 6. Send a request → check log line has traceId, spanId, correlationId
TOKEN=$(curl -s -u admin:admin123 \
  -d "grant_type=client_credentials&scope=read" \
  http://localhost:9010/oauth2/token | jq -r .access_token)

curl -s -H "Authorization: Bearer $TOKEN" \
     -H "X-Correlation-Id: manual-test-$(date +%s)" \
     http://localhost:8080/api/v1/users/me > /dev/null

# Watch gateway stdout — find the log line:
# {"@timestamp":"...","traceId":"...","spanId":"...","correlationId":"manual-test-1727..."}

# 7. Zipkin UI — should show a trace for the request above
open http://localhost:9411

# In Zipkin search:
#  - Service: api-gateway
#  - Look for GET /api/v1/users/me
#  - Click → span timeline

# 8. Verify the traceId in Zipkin matches the log line
# → same value → distributed tracing works end-to-end

# 9. Switch to local (text) logs
mvn -pl api-gateway spring-boot:run \
  -Dspring-boot.run.arguments="--spring.profiles.active=local"
# → Log format changes:
# 15:30:00.123 INFO  [traceId,spanId,cid] logger - message
```

---

## 8. Interview cheat-sheet

| Question | Answer |
|---|---|
| Why structured JSON logs? | ELK / Datadog / CloudWatch ingest JSON natively. Grep-friendly for humans too via `jq`. |
| How does MDC work in reactive code? | Direct `MDC.put()` is fragile — Reactor hops threads, MDC is ThreadLocal. Use Reactor Context (`contextWrite`) + Micrometer Context Propagation which lifts to MDC at log-emission time. |
| What's the correlationId vs traceId? | traceId is framework-managed (Micrometer Tracing / Zipkin), spans multiple services. correlationId is our own — set by CorrelationIdWebFilter, returned in response header so clients can correlate their request with our logs. Redundant with traceId in some sense, but survives independent of tracing config. |
| How do you propagate trace context between services? | W3C `traceparent` header, injected by Micrometer Tracing on every outgoing HTTP call. Downstream services read it and continue the trace. |
| Sampling rate in prod? | 1-10% typically. 100% (like dev) creates enormous span volume. Adjust `management.tracing.sampling.probability`. |
| Zipkin vs Jaeger vs Tempo? | Zipkin is the OG (Twitter, 2012). Jaeger is Uber's; also mature. Grafana Tempo is newer, tightly integrated with Prometheus / Grafana. Micrometer Tracing supports all three via bridges. Same client code. |
| Where does /actuator/info get its data? | Multiple InfoContributor beans: `BuildInfoContributor` (build-info.properties), `GitInfoContributor` (git.properties), `EnvironmentInfoContributor` (info.* config), `JavaInfoContributor`, `OsInfoContributor`, and any custom bean implementing InfoContributor. |
| Why generate build-info at build time? | You want to see WHICH version is deployed. In prod, curl `/actuator/info` returns the exact version + git SHA + build time. Diagnostic gold during incidents. |
| Framework log noise strategy? | Package-level overrides in logback-spring.xml (io.netty=WARN, r2dbc=INFO). Dynamic per-logger via `POST /actuator/loggers/{name}` at runtime. |
| Correlation id — response header or MDC? | Both. Response header lets the CLIENT correlate their request with your logs. MDC lets YOUR logs include the id automatically. |
| Runtime log level change? | `POST /actuator/loggers/com.example.foo` with `{"configuredLevel":"DEBUG"}`. Instant, no restart. Requires exposing the `loggers` actuator endpoint. |
| Trace-metric linking? | Prometheus exemplars — attach a traceId to each histogram bucket sample. Grafana → click a slow request → jump to its trace. Requires Grafana Tempo datasource + exemplars-enabled Prometheus. |

---

## 9. Common pitfalls (interview probes)

1. **MDC in reactive code without context propagation** — logs miss traceId/correlationId. Fix: Micrometer Context Propagation module (auto-wired in Spring Boot 3).
2. **Zipkin endpoint set but Zipkin not running** — spans silently discarded. Check with a curl at http://localhost:9411/health.
3. **100% sampling in prod** — Zipkin ingests millions of spans/day. p99 sampling latency degrades. Use 1-10%.
4. **`/actuator/info` returns `{}`** — no InfoContributor beans have anything to say. Fix: enable build/git properties generation.
5. **build-info.properties not generated** — missing `<execution><id>build-info</id></execution>` in spring-boot-maven-plugin config. Check `target/classes/META-INF/`.
6. **git.properties absent when building from tarball** — no .git directory. `failOnNoGitDirectory: false` prevents build breakage.
7. **Exposing `/actuator/env` in prod** — leaks all environment variables including secrets. Restrict via `management.endpoint.env.show-values: NEVER`.
8. **Feature flag drift silently** — `/actuator/info.features` gives you a diff-able snapshot. Automate a per-env comparison in CI.
9. **CorrelationIdWebFilter not first** — if run after Spring Security, unauthenticated requests miss the id in their 401 logs. Fix: `@Order(HIGHEST_PRECEDENCE + 10)`.
10. **Missing `management.info.env.enabled=true`** — top-level `info:` block silently ignored. Common trap.

---

## 10. Extensions (parked)

- **Replicate to all services** — auth-server, user-service, product-service etc. need the same deps + config for end-to-end traces. Currently only api-gateway has tracing wired.
- **Grafana Tempo integration** — swap Zipkin for Tempo (Grafana-native). Add Tempo datasource. Enable Prometheus exemplars so metric panels link directly to traces.
- **Sampling by header** — `X-Trace: yes` forces 100% sampling for one request even in prod. Great for debugging specific issues without turning on firehose.
- **OpenTelemetry instead of Brave** — swap `micrometer-tracing-bridge-brave` for `micrometer-tracing-bridge-otel`. OTel is the CNCF standard; more backends supported.
- **Log correlation across services** — inject `X-Correlation-Id` into TokenRelay so downstream services see it AND emit it in their logs. Same MDC key everywhere = single search query in ELK.
- **PII scrubbing at log level** — logback filter that redacts email/card numbers before they hit the JSON encoder (already done at the BodyLogging level but not for framework logs).
- **Structured log query alerts** — Loki + LogQL alerts on "error rate per correlationId" (someone getting spammed with errors → possible attack).
- **Trace-based SLO monitoring** — Grafana Tempo can compute latency % based on traces, not just aggregated histograms. Better tail-latency signal.
- **Loggers actuator endpoint** — expose `loggers` in `management.endpoints.web.exposure.include` to allow runtime log level changes via POST.
- **Kafka appender** — instead of stdout → filebeat, publish logs directly to a Kafka topic. Replaces the sidecar shipper.
