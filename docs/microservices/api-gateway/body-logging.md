# API Gateway — Request/Response Body Logging

Structured audit log for every request through the gateway, including request
and response bodies — with **PII redaction**, **sampling**, and **size limits**.

Emits one JSON log line per request via SLF4J + Logstash encoder (already in
your pom), ready to ship to ELK/Datadog/CloudWatch.

---

## 1. Why NOT just enable framework debug logs

Naive approach — `logging.level.org.springframework.cloud.gateway: DEBUG` —
gives you route matching decisions and filter execution. It does NOT log
body content, because request/response bodies are reactive `DataBuffer`
streams, not strings.

What you want (per request):

```json
{
  "timestamp": "2026-09-29T14:30:00Z",
  "correlationId": "abc-123",
  "route": "user-service",
  "method": "POST",
  "path": "/api/v1/users",
  "requestBody": "{\"email\":\"j***@x.com\",\"password\":\"***REDACTED***\"}",
  "status": 201,
  "responseBody": "{\"id\":42,\"email\":\"j***@x.com\"}",
  "durationMs": 87,
  "clientIp": "10.0.0.5",
  "userAgent": "curl/7.87"
}
```

That's an audit log — actionable for debugging + compliance-friendly.

---

## 2. Architecture

```
                    ┌────────────────────────────────────────────────────────────────┐
                    │           API Gateway                                          │
                    │                                                                │
                    │  CorrelationIdWebFilter    (populates X-Correlation-Id + MDC)  │
                    │       │                                                        │
                    │       ▼                                                        │
                    │  BodyLoggingGlobalFilter    order = -20                        │
                    │                                                                │
                    │   ┌────────────────────────────────────────────────────────┐   │
                    │   │  PRE PHASE                                             │   │
                    │   │    1. Skip if excluded-path or sample-rate misses      │   │
                    │   │    2. Initialize AuditEvent                            │   │
                    │   │    3. Check Content-Type → text or binary              │   │
                    │   │    4. If text: DataBufferUtils.join(request body)      │   │
                    │   │       - read bytes, release buffer                     │   │
                    │   │       - truncate + redact                              │   │
                    │   │       - store in AuditEvent                            │   │
                    │   │       - decorate request → replay bytes downstream     │   │
                    │   └────────────────────────────────────────────────────────┘   │
                    │       │                                                        │
                    │       ▼                                                        │
                    │  chain.filter(mutatedExchange)                                 │
                    │       │                                                        │
                    │       ▼                                                        │
                    │  ┌─── POST (ServerHttpResponseDecorator.writeWith) ─────┐     │
                    │  │  1. Check response.enabled + status-classes filter   │     │
                    │  │  2. Check Content-Type → text or binary              │     │
                    │  │  3. If text: DataBufferUtils.join(response body)     │     │
                    │  │     - read bytes, release buffer                     │     │
                    │  │     - truncate + redact                              │     │
                    │  │     - store in AuditEvent                            │     │
                    │  │     - replay bytes to client                         │     │
                    │  └──────────────────────────────────────────────────────┘     │
                    │       │                                                        │
                    │       ▼                                                        │
                    │  doFinally → emit final audit log (JSON via SLF4J)             │
                    │                                                                │
                    │  ...remaining filters (RateLimiter, CB, Retry, etc.)          │
                    │                                                                │
                    └────────────────────────────────────────────────────────────────┘
```

---

## 3. Design decisions

### 3a. GlobalFilter over GatewayFilterFactory

- **GlobalFilter** — applies to every route automatically. No YAML wiring. Right for auditing.
- **GatewayFilterFactory** — per-route opt-in. Wrong default for auditing.

We picked GlobalFilter. You already built two GatewayFilterFactories in
`custom-filters.md` — this shows the other side of the coin.

### 3b. Sampling — the throughput protection

Three levers:

```
sample-rate       — 0.01 = 1% of requests logged
max-body-bytes    — bodies bigger than N are truncated
excluded-paths    — never logged (health checks, admin)
```

Dev: `sample=1.0, max-body=8KB`.
Prod: `sample=0.01, max-body=8KB, response.status-classes=[4xx,5xx]`.

Sampling happens BEFORE body buffering — a request that misses the sample
never touches DataBufferUtils.join.

### 3c. Redaction — two-layer defense

```
Layer 1: Field-name blocklist
  "password":"secret123"  →  "password":"***REDACTED***"
  "apiKey":"sk_live_..."  →  "apiKey":"***REDACTED***"

Layer 2: Value patterns (regex on any occurrence)
  card 4111111111111111  →  card ***REDACTED***
  ssn 123-45-6789        →  ssn ***REDACTED***
  sk_live_abc123         →  ***REDACTED***
```

Field patterns compiled once in `BodyRedactor` constructor with
`Pattern.MULTILINE + Pattern.quote(fieldName)`. Case-insensitive via
`(?i:...)` inline flag.

**Trade-off**: regex on JSON is imperfect — breaks on escaped quotes,
nested strings with matching field names. For strict correctness, parse the
JSON and redact by JSONPath. Slower + needs schema. Documented as
extension.

### 3d. Log sink — SLF4J via existing Logstash encoder

You already have `logstash-logback-encoder` in your pom. SLF4J at INFO with
a structured message → Logstash converts to JSON → ready for ELK/Datadog.

Alternative for compliance-grade audit trails: dedicated Kafka topic +
immutable retention. Extension parked.

### 3e. Binary content — never decode as UTF-8

If someone POSTs a JPEG, we log `<binary or non-text content, type=image/jpeg>`,
not gibberish or worse — an OOM from attempting to decode multi-MB image bytes.

Allowlist controlled by `loggable-content-types`. Anything else → binary marker.

### 3f. Response body — always log, or only errors?

`response.status-classes`:
- Empty → log ALL responses
- `[4xx, 5xx]` → only errors (huge cost reduction in prod)
- `[5xx]` → only server errors (cheapest)

For dev: log everything. For prod: log errors only + samples of success.

### 3g. Filter ordering: -20

```
CorrelationIdWebFilter      (no explicit order, WebFilter — runs before GlobalFilters)
BodyLoggingGlobalFilter     order = -20
RequestFingerprint          order = -1
IdempotencyKey              order = 0
AddTenantHeader             order = 0
Built-in filters            various
```

- Before `RequestFingerprint` (also body-consuming) — avoids duplicate joins
- Before auth filters — logs unauthenticated attempts too
- After `CorrelationIdWebFilter` — so `X-Correlation-Id` is in headers when we
  read it

---

## 4. Config

```yaml
gateway:
  body-logging:
    enabled: true
    sample-rate: 1.0                  # 0.0-1.0
    max-body-bytes: 8192
    excluded-paths:
      - /actuator/**
      - /fallback/**
      - /admin/**
    loggable-content-types:
      - application/json
      - application/xml
      - text/plain
      - text/html
      - application/x-www-form-urlencoded
    redact:
      field-names: [password, token, secret, apiKey, authorization, ssn, cvv, creditCard]
      patterns:
        - '\d{16}'                    # card-like
        - '\d{3}-\d{2}-\d{4}'         # US SSN
        - 'sk_live_[a-zA-Z0-9]+'      # our API keys
      replacement: '***REDACTED***'
    response:
      enabled: true
      status-classes: []              # empty = log all; prod: [4xx, 5xx]
```

Master switch `@ConditionalOnProperty` on `BodyLoggingGlobalFilter`,
`BodyRedactor`. Disable = both beans drop out of the context.

---

## 5. Files added / changed

```
api-gateway/
└── src/main/
    ├── java/com/example/apigateway/bodylogging/           (NEW package)
    │   ├── BodyLoggingProperties.java                     (NEW)
    │   ├── BodyRedactor.java                              (NEW — pre-compiled regex)
    │   ├── AuditEvent.java                                (NEW — DTO)
    │   └── BodyLoggingGlobalFilter.java                   (NEW — the filter)
    └── resources/
        └── application.yml                                (gateway.body-logging block)

docs/microservices/api-gateway/
└── body-logging.md                                        (this file)
```

No new dependencies — Reactor + Spring already present. Logstash encoder
already in pom from the initial setup.

---

## 6. Verification

```bash
mvn -pl api-gateway clean spring-boot:run

TOKEN=$(curl -s -u admin:admin123 \
  -d "grant_type=client_credentials&scope=read" \
  http://localhost:9010/oauth2/token | jq -r .access_token)
```

### 6.1 Normal GET — logged (no request body, response body logged)

```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/users/me
```

Gateway log line:
```
audit correlationId=<id> route=user-service method=GET path=/api/v1/users/me
      query=null status=200 durationMs=45 clientIp=127.0.0.1
      requestBody=null responseBody={"id":1,"email":"..."}
```

### 6.2 POST with sensitive fields — redacted

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"email":"user@x.com","password":"secret123","apiKey":"abc"}' \
  http://localhost:8080/api/v1/users
```

Log shows:
```
requestBody={"email":"user@x.com","password":"***REDACTED***","apiKey":"***REDACTED***"}
```

### 6.3 Card number pattern — masked anywhere

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"note":"payment on card 4111111111111111 done"}' \
  http://localhost:8080/api/v1/users
```

Log:
```
requestBody={"note":"payment on card ***REDACTED*** done"}
```

### 6.4 Binary body — not decoded

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: image/jpeg" \
  --data-binary @image.jpg \
  http://localhost:8080/api/v1/users
```

Log:
```
requestBody=<binary or non-text content, type=image/jpeg>
```

### 6.5 Excluded path — no audit log

```bash
curl http://localhost:8080/actuator/health
```

No `audit ...` log line emitted (path matched `/actuator/**`).

### 6.6 Sampling — half of requests logged

```bash
mvn -pl api-gateway spring-boot:run \
  -Dspring-boot.run.arguments="--gateway.body-logging.sample-rate=0.5"

# Hit 100 times
for i in $(seq 1 100); do
  curl -s -H "Authorization: Bearer $TOKEN" \
    http://localhost:8080/api/v1/users/me > /dev/null
done

# Roughly 50 audit lines
grep -c "^.*audit " gateway.log
# → ~50
```

### 6.7 Errors-only mode

```bash
mvn -pl api-gateway spring-boot:run \
  -Dspring-boot.run.arguments="--gateway.body-logging.response.status-classes=4xx,5xx"

# Success — request body logged, response body NOT logged
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/users/me
# Log: responseBody=null (skipped due to status class filter)

# Error — both logged
curl http://localhost:8080/api/v1/users/me
# 401 → responseBody={"error":...} logged
```

### 6.8 Truncation

```bash
mvn -pl api-gateway spring-boot:run \
  -Dspring-boot.run.arguments="--gateway.body-logging.max-body-bytes=100"

# Send a body larger than 100 bytes
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"data":"'"$(head -c 500 /dev/urandom | base64)"'"}' \
  http://localhost:8080/api/v1/users

# Log: requestBody={"data":"aBc...[first 100 chars]...[TRUNCATED]"
```

### 6.9 Disable entirely

```bash
mvn -pl api-gateway spring-boot:run \
  -Dspring-boot.run.arguments="--gateway.body-logging.enabled=false"
# No audit lines emitted. BodyLoggingGlobalFilter + BodyRedactor beans absent.
```

---

## 7. Failure modes & mitigations

| Scenario | Behavior | Mitigation |
|---|---|---|
| 10MB request body | Buffered → OOM risk | Content-Type check skips binary; `max-body-bytes` truncates text |
| Streaming response (SSE, chunked download) | Buffering breaks streaming | Content-Type check skips (SSE is `text/event-stream`; downloads are binary) |
| Downstream returns malformed JSON | Redactor regex may not match | `.replaceAll(...)` returns input unchanged — safe |
| High RPS + sample-rate=1.0 | Doubled memory + log volume | Drop sample-rate in prod; use response.status-classes filter |
| Adversarial regex input | Catastrophic backtracking possible | Simple patterns (no nested quantifiers); add regex timeout wrapper (extension) |
| Password in nested JSON: `{"user":{"password":"..."}}` | ✓ Matched (field patterns are location-agnostic) | Works |
| Pretty-printed JSON with newlines | Field patterns use MULTILINE — mostly works | For strict correctness: JSON parser (extension) |
| Password in header (`Authorization: Bearer ...`) | Not captured — we only log body | Headers not logged; if you add them, apply redaction too |
| Filter runs before CorrelationIdWebFilter | correlationId=null in logs | Order -20 vs CorrelationId's WebFilter position (WebFilter runs before GlobalFilter chain) |
| Log SLF4J appender is synchronous | Blocks under high RPS | Use `AsyncAppender` in logback config (extension) |

---

## 8. Interview cheat-sheet

| Question | Answer |
|---|---|
| Why not framework debug logs? | They log route decisions, not body content. Bodies are reactive DataBuffer streams. |
| How do you buffer a reactive body? | `DataBufferUtils.join()` → single DataBuffer → `.read(byte[])` → `DataBufferUtils.release()` |
| Why release the buffer? | Netty pooled ref-counted buffers. Forget = leak = OOM. |
| How does the body reach downstream after you consumed it? | `ServerHttpRequestDecorator` overriding `getBody()` to return a fresh buffer wrapping saved bytes. |
| Same for response body? | `ServerHttpResponseDecorator` overriding `writeWith(Publisher<DataBuffer>)`. Called by the framework right before writing to the wire. |
| How do you handle PII? | Field-name blocklist for known-bad names + regex patterns for structured leaks. Pre-compiled in constructor. |
| Why not JSON-parse for redaction? | Correctness > perf trade-off. Regex handles 95% of cases; JSON parse needs schema + per-endpoint config. Documented. |
| Binary content? | Content-Type allowlist. Everything else logged as `<binary content>` — never decoded. |
| Sampling? | Random per-request coin flip against `sample-rate`. Sample check runs BEFORE body buffering — misses cost near zero. |
| Why GlobalFilter instead of GatewayFilterFactory? | Auditing applies to every route by default. Factory would be per-route opt-in (wrong default). |
| Where do logs go? | SLF4J → Logstash encoder → JSON stdout → ELK/Datadog. For compliance immutability: separate Kafka sink. |
| Why order -20? | After CorrelationIdWebFilter (traceId available), before body-consuming filters like RequestFingerprint. |
| Streaming responses? | Skip via Content-Type check — SSE is `text/event-stream`, downloads are `application/octet-stream` etc. |
| Regex DoS risk? | Bounded patterns (no nested quantifiers). Add regex timeout wrapper in real production. |
| Correlation across services? | X-Correlation-Id captured in event; downstream services log it too → linkable in ELK by that ID. |

---

## 9. Common pitfalls (interview probes)

1. **Forgetting `DataBufferUtils.release()`** — Netty pooled buffer leak → OOM under load.
2. **Blocking log write in reactive chain** — SLF4J is generally fast enough, but `AsyncAppender` recommended at scale.
3. **Not decorating BOTH request AND response** — you need `ServerHttpRequestDecorator` for req and `ServerHttpResponseDecorator` for resp.
4. **Wrong filter order** — before CorrelationIdWebFilter = no correlationId in logs.
5. **Buffering streaming responses** — breaks SSE/downloads. Content-Type check saves you.
6. **Regex without MULTILINE** — misses fields on multi-line pretty-printed JSON.
7. **Only redacting body values, not headers** — `Authorization: Bearer ...` in header dump leaks. This build doesn't log headers; if you add, apply redaction there too.
8. **`sample-rate: 1.0` in prod** — audit volume can dwarf app logs; costs money on managed observability platforms.
9. **Content-Type mismatch (client sends `text/plain` for JSON)** — you'll try to redact non-JSON as if it were JSON. Redactor is safe (no matches = no change) but no privacy protection either. Enforce Content-Type at API contract level.

---

## 10. Extensions (parked)

- **JSON-path based redaction** — parse JSON, redact by path (`$.user.password`). More correct, more complex.
- **Header redaction + logging** — capture `requestHeaders` in AuditEvent, strip `Authorization`, `Cookie`, `Set-Cookie` before logging.
- **AsyncAppender** — wrap Logstash encoder for high-throughput.
- **Kafka sink** — dedicated audit topic with immutable retention for compliance.
- **Per-route override** — `gateway.body-logging.routes.<id>.sample-rate: 1.0` for surgical debug logging.
- **Duration histogram** — Micrometer timer for gateway→downstream latency (companion to observability item L).
- **Cross-service correlation** — inject audit-event ID into downstream calls so audit logs join in ELK.
- **Retention policy** — separate log level for audit vs framework logs → separate indices.
- **Redaction unit tests** — cover nested fields, escaped quotes, multi-line JSON, non-JSON content.
- **Regex timeout wrapper** — bound each redaction step to N ms to protect against catastrophic backtracking.
- **Selective body logging by principal** — log ALL requests for principals matching a debug allowlist; sample the rest.
