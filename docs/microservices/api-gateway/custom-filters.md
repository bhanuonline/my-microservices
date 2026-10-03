# API Gateway — Custom Filter Factories

Building your own filters (not just configuring built-ins). Two filters ship here:

- `AddTenantHeader` — pedagogical, reads JWT claim → injects header
- `RequestFingerprint` — production-shaped, hashes request → header (fast path + body-buffering slow path)

Together they teach the full `AbstractGatewayFilterFactory` API: config binding, both YAML forms, ordering, reactive request mutation, and body-buffering with `DataBufferUtils`.

---

## 1. The factory contract

```
┌──────────────────────────────────────────────────────────────────┐
│  Class name MUST end in `GatewayFilterFactory`                   │
│  → Spring strips the suffix to derive the YAML name              │
│    AddTenantHeaderGatewayFilterFactory → AddTenantHeader         │
│                                                                  │
│  extends AbstractGatewayFilterFactory<Config>                    │
│                                                                  │
│  Config           ← static inner class with getters/setters      │
│                     Populated by Spring from YAML args           │
│                                                                  │
│  apply(Config)    ← builds a fresh GatewayFilter per route       │
│                     that references this factory                 │
│                                                                  │
│  shortcutFieldOrder() → List<String>                             │
│                   ← optional: enables `Name=arg1,arg2` shortcut  │
│                                                                  │
│  @Component       ← Spring picks it up; one factory per JVM      │
└──────────────────────────────────────────────────────────────────┘
```

Two YAML forms map to the same factory:

```yaml
# Shortcut (positional, uses shortcutFieldOrder())
filters:
  - AddTenantHeader=X-Tenant-Id,tenant_id

# Named (map, self-documenting)
filters:
  - name: AddTenantHeader
    args:
      header-name: X-Tenant-Id
      claim-name: tenant_id
```

Both produce a `Config` with `headerName=X-Tenant-Id, claimName=tenant_id`. Config field names bind via kebab-case in YAML → camelCase in Java (Spring's relaxed binding).

---

## 2. Filter execution model

```
Request ──▶
              ┌───────────────────────────────────────────────────┐
              │ filter(exchange, chain)                           │
              │                                                   │
              │  ┌─ PRE PHASE ────────────────────────────────┐   │
              │  │  read JWT, hash body, mutate headers, etc. │   │
              │  └────────────────────────────────────────────┘   │
              │                                                   │
              │  return chain.filter(mutatedExchange)             │
              │           .then(Mono.fromRunnable(() -> { ... }));│
              │                                                   │
              │  ┌─ POST PHASE (after downstream response) ────┐  │
              │  │  add response headers, log timing, metrics  │  │
              │  └─────────────────────────────────────────────┘  │
              └───────────────────────────────────────────────────┘
                                     ▼
                                 Response
```

**Two hard rules**:
1. Return `Mono<Void>` — never block. Blocking work → `.subscribeOn(Schedulers.boundedElastic())`.
2. `ServerHttpRequest` is immutable. Use `exchange.mutate().request(r -> r.header(...)).build()` — this creates a NEW exchange with a decorated request.

---

## 3. Filter ordering

Filters run in **YAML order** for pre-phase and **reverse order** for post-phase (stack unwinding). Spring Cloud Gateway also lets you assign an explicit numeric order via `OrderedGatewayFilter`.

```
Route filters (order in YAML):
  1. RequestRateLimiter        (built-in)
  2. CircuitBreaker            (built-in)  ─────┐
  3. Retry                     (built-in)       │ CB wraps Retry ✓
  4. AddTenantHeader           (custom, order 0)│
  5. RequestFingerprint        (custom, order -1) ← runs BEFORE anything body-modifying
  6. TokenRelay                (default-filter, applies last)
```

Lower `OrderedGatewayFilter` order = earlier execution in pre-phase. `RequestFingerprint` needs order -1 so it reads/replays the body before any downstream filter consumes it.

---

## 4. The two filters explained

### `AddTenantHeader` — reading auth context

```java
exchange.getPrincipal()
        .cast(Authentication.class)
        .filter(auth -> auth instanceof JwtAuthenticationToken)
        .map(auth -> ((JwtAuthenticationToken) auth).getToken())
        .map(Jwt::getClaims)
        .flatMap(claims -> {
            Object value = claims.get(config.getClaimName());
            if (value == null) return chain.filter(exchange);
            var mutated = exchange.mutate()
                    .request(r -> r.header(config.getHeaderName(), value.toString()))
                    .build();
            return chain.filter(mutated);
        })
        .switchIfEmpty(chain.filter(exchange));
```

Design notes:
- **`getPrincipal()`** — reactive, returns `Mono<Principal>` (empty on unauthenticated routes)
- **`switchIfEmpty`** — the "no auth" fallback; without it, public routes would silently drop
- **`flatMap`** — not `map`, because we return another `Mono<Void>` from `chain.filter(...)`
- **Fail-open on missing claim** — if `tenant_id` isn't in the JWT, we pass through unchanged. Interviewers may prefer fail-closed (return 403); depends on your threat model.

### `RequestFingerprint` — buffering the request body

The body is a **`Flux<DataBuffer>`** — chunks of bytes streamed from the client. Read once, then gone.

```
Client body ──▶ Netty ──▶ Flux<DataBuffer>  ──consume once──▶ downstream
                              │
                              │  we need it TWICE:
                              │    1. to hash for the fingerprint
                              │    2. to forward downstream
                              ▼
                        Solution:
                          a. DataBufferUtils.join(...) → single DataBuffer
                          b. Read into byte[]
                          c. Release the buffer (ref-counted!)
                          d. Wrap ServerHttpRequestDecorator that
                             re-emits the bytes as a fresh DataBuffer
```

Code path:
```java
return DataBufferUtils.join(exchange.getRequest().getBody())
    .defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
    .flatMap(buffer -> {
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        DataBufferUtils.release(buffer);          // ← CRITICAL: Netty ref-counted

        String fp = hash(fingerprintInput(exchange, new String(bytes, UTF_8)), config.getAlgorithm());

        ServerHttpRequest decorated = new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override public Flux<DataBuffer> getBody() {
                DataBuffer replayed = exchange.getResponse().bufferFactory().wrap(bytes);
                return Flux.just(replayed);
            }
        };
        var mutated = exchange.mutate().request(decorated).build();
        mutated.getRequest().mutate().header(config.getHeaderName(), fp).build();
        return chain.filter(mutated);
    });
```

**Interview gold** — the three things that trip people up:
1. **Body is one-shot**. Must decorate to replay.
2. **`DataBufferUtils.release()`** is mandatory. Skip it → memory leak (Netty allocates pooled buffers with ref counts).
3. **Fast path** for `include-body=false` avoids joining the flux entirely — huge perf win when body isn't needed.

---

## 5. Config schema

```yaml
# AddTenantHeader
- AddTenantHeader=X-Tenant-Id,tenant_id
# OR
- name: AddTenantHeader
  args:
    header-name: X-Tenant-Id
    claim-name: tenant_id

# RequestFingerprint
- name: RequestFingerprint
  args:
    header-name: X-Request-Fingerprint
    include-body: false        # true = buffer body into hash (slower, richer)
    algorithm: SHA-256          # any MessageDigest algo: SHA-256, SHA-1, MD5
```

Per-route defaults:
- `user-service` — `RequestFingerprint` with `include-body: false` (GETs, no meaningful body)
- `order-service` — `RequestFingerprint` with `include-body: true` (POSTs benefit from body hash for dedup/audit)

---

## 6. Files added / changed

```
api-gateway/
└── src/main/
    ├── java/com/example/apigateway/filter/
    │   ├── AddTenantHeaderGatewayFilterFactory.java           (NEW)
    │   └── RequestFingerprintGatewayFilterFactory.java        (NEW)
    └── resources/
        └── application.yml                                    (filters added to user + order routes)
```

No new dependencies — everything used is already in `spring-cloud-starter-gateway` and Reactor.

---

## 7. Running & verifying

You need a downstream endpoint that echoes headers. Quick option — add this to `user-service`:

```java
@GetMapping("/api/v1/users/echo")
public Mono<Map<String, String>> echo(@RequestHeader Map<String, String> headers) {
    return Mono.just(headers);
}
```

Then:

```bash
# 1. Get a JWT
TOKEN=$(curl -s -u admin:admin123 \
  -d "grant_type=client_credentials&scope=read" \
  http://localhost:9010/oauth2/token | jq -r .access_token)

# 2. Decode to see what claims you actually have
echo "$TOKEN" | cut -d. -f2 | base64 -d 2>/dev/null | jq
# Look for tenant_id — if missing, AddTenantHeader silently passes through.
# Add it in auth-server → JwtCustomizer or CustomClaimsMapper.

# 3. Hit the echo endpoint
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/users/echo | jq

# Expected headers echoed back:
# {
#   "x-tenant-id": "acme-corp",              ← from AddTenantHeader
#   "x-request-fingerprint": "3f2a9c8b...",  ← from RequestFingerprint
#   "x-correlation-id": "e7c4-...",          ← from CorrelationIdWebFilter
#   "authorization": "Bearer eyJ..."         ← from TokenRelay
# }
```

Test body-inclusive fingerprint:
```bash
# Send two identical POST bodies — fingerprints should match exactly
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"item":"widget","qty":1}' \
  http://localhost:8080/api/v1/orders/echo | jq '.["x-request-fingerprint"]'

curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"item":"widget","qty":1}' \
  http://localhost:8080/api/v1/orders/echo | jq '.["x-request-fingerprint"]'
# Should print the same hash twice

# Change the body → different hash
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"item":"widget","qty":2}' \
  http://localhost:8080/api/v1/orders/echo | jq '.["x-request-fingerprint"]'
# Different hash
```

---

## 8. Failure modes & mitigations

| Scenario | Behavior | Mitigation |
|---|---|---|
| JWT missing `tenant_id` claim | Passes through, no header set | Add claim in auth-server, OR change filter to fail-closed |
| Public route (no JWT) | `getPrincipal()` empty → `switchIfEmpty` skips mutation | Intended — public routes get no tenant header |
| Body larger than memory limit | Reactor Netty throws `DataBufferLimitException` | Set `spring.codec.max-in-memory-size: 1MB` and handle exception globally |
| Body already consumed by upstream filter | Empty body in `join()` → empty hash | Ensure `RequestFingerprint` order (-1) is lower than any body-modifying filter |
| Missing `DataBufferUtils.release()` | Memory leak, eventual OOM | Present in code — never remove |
| Non-existent MessageDigest algorithm | `IllegalStateException` at request time | Validate in `apply()` at boot instead of per-request |

---

## 9. Interview cheat-sheet

| Question | Answer |
|---|---|
| How does Spring discover a custom Gateway filter? | Bean of type `AbstractGatewayFilterFactory` — class suffix `GatewayFilterFactory` stripped for YAML name |
| Shortcut vs named YAML form? | Shortcut = positional args driven by `shortcutFieldOrder()`. Named = map, better for many params. |
| How does config binding work? | Base class instantiates `Config`, Spring binds YAML args using relaxed kebab→camelCase |
| Why is `ServerHttpRequest` immutable? | Reactive contract — enables safe decoration and chaining without shared mutable state |
| How to read the request body? | `DataBufferUtils.join(request.getBody())` → single buffer → `.read(bytes)` → release |
| What happens if I forget `DataBufferUtils.release()`? | Netty pooled buffer leak → OOM under load |
| How to replay the body downstream after reading? | `ServerHttpRequestDecorator` overriding `getBody()` to return fresh `DataBuffer` wrapping the saved bytes |
| Why `OrderedGatewayFilter`? | Explicit order determines pre-phase execution sequence; needed when a filter must run before body-modifying filters |
| Custom `GatewayFilterFactory` vs `GlobalFilter`? | Factory = opt-in per route via YAML, per-instance config. GlobalFilter = applies to ALL routes, no YAML. |
| How to unit-test a filter? | Instantiate factory → `apply(config)` → build `MockServerHttpRequest`/`MockServerWebExchange` → `StepVerifier.create(filter.filter(exchange, chain))` |
| How to make the filter block on external I/O? | Wrap blocking work in `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` — never block directly on the request thread |

---

## 10. Extensions (parked)

- **`@ConditionalOnProperty` on the `@Component`** — toggle the whole filter class on/off via `gateway.filters.tenant.enabled`. Currently always registered (dormant until a route references it).
- **Response fingerprint** — mirror filter that hashes the downstream response body (post-phase, decorate `ServerHttpResponse`).
- **`GlobalFilter` version** — apply fingerprinting to every route without YAML wiring.
- **Idempotency-Key filter** — use `RequestFingerprint`'s hash as the natural idempotency key when the client doesn't supply one, then dedupe in Redis.
- **Unit tests** — with `StepVerifier` and `MockServerWebExchange`, verify header injection and body replay round-trip.
- **Fail-closed mode on `AddTenantHeader`** — config knob `required: true` → return 403 when claim is missing.
