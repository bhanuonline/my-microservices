# Tier 4B — Tests + Dockerfile

Two production-readiness improvements:

1. **Tests** — unit test for the custom converter + integration tests for
   controller + security pipeline
2. **Dockerfile** — multi-stage with BuildKit cache mount, non-root user,
   health check, container-aware JVM flags

---

## 1. Test strategy

```
┌──────────────────────────────────────────────────────────────────────────┐
│  Test                            Scope              Cost     What it     │
│                                                              validates   │
│  ───────────────────────────    ──────────────    ────────  ───────────  │
│  CombinedAuthoritiesConverter   pure Java         ~ms       claim →      │
│    Test (unit)                  no Spring                   authority    │
│                                                             logic        │
│                                                                           │
│  ApiControllerTest              @SpringBootTest   ~1-2 s    end-to-end   │
│    (integration)                full context                HTTP + JWT   │
│                                 MockMvc + mock              auth +       │
│                                 JWTs                        @PreAuthorize│
└──────────────────────────────────────────────────────────────────────────┘
```

Two layers for a reason:
- **Unit tests** give fast feedback on specific logic (converter edge cases).
- **Integration tests** verify the wiring — that Spring Security, the
  converter, `@PreAuthorize` on the controller, and the HTTP pipeline all
  compose correctly.

If both pass but the service doesn't work in prod, the gap is **beyond
Spring** (JWKS network, auth-server configuration, etc.).

---

## 2. Unit test — the converter

`CombinedAuthoritiesConverterTest` directly instantiates the converter and
feeds it crafted JWTs. No Spring, no HTTP, no mocking.

```java
@Test
void bothScopeAndRolesClaims() {
    Jwt jwt = jwt()
        .claim("scope", "read")
        .claim("roles", List.of("ADMIN"))
        .build();

    List<String> auths = authNames(converter.convert(jwt));

    assertThat(auths).containsExactlyInAnyOrder("SCOPE_read", "ROLE_ADMIN");
}
```

Covered edge cases:
- scope-only claim
- roles-as-list claim
- roles-as-CSV-string claim
- roles-as-space-string claim
- `ROLE_X` passes through without double-prefix
- both scope + roles present
- empty / blank claims return empty authorities

Run time: milliseconds. Add a new case for every bug you find in the
converter. These tests prevent regressions.

---

## 3. Integration test — the HTTP pipeline

`ApiControllerTest` uses `@SpringBootTest` + `MockMvc` with Spring Security's
`jwt()` test post-processor:

```java
mvc.perform(get("/api/hello")
        .with(jwt().jwt(j -> j.subject("alice"))
                   .authorities(new SimpleGrantedAuthority("SCOPE_read"))))
    .andExpect(status().isOk())
    .andExpect(content().string("Hello, alice"));
```

### How `jwt()` works

- Skips signature validation entirely — tests don't need auth-server
- Installs a fake `JwtAuthenticationToken` into the security context
- Lets you specify arbitrary claims + authorities
- Fast — no JWKS fetch, no RSA validation

### Why `@SpringBootTest` not `@WebMvcTest`

`@WebMvcTest` loads only the web slice. The auto-configured `JwtDecoder`
(from Spring Boot's resource-server auto-config) is absent, so our custom
`JwtAuthenticationConverter` isn't bound into the filter chain. `jwt()`
post-processor still works for sliced tests, but we also want to exercise
`CombinedAuthoritiesConverter` end-to-end — which requires the full context.

Trade-off: slower boot (~1-2s per test class) but higher confidence.

### Test coverage matrix

```
┌──────────────────────────────────────────────────────────────────────────┐
│  Endpoint      No auth   Wrong scope   Right scope   Right role          │
│  ───────────   ───────   ───────────   ───────────   ──────────────      │
│  GET /hello    401       403           200           -                   │
│  POST /echo    401       403           200           -                   │
│  GET /admin    401       403           -             200                 │
│  GET /me       401       (any auth OK) 200           -                   │
└──────────────────────────────────────────────────────────────────────────┘
```

9 test methods cover the full matrix.

### TestPropertySource — the issuer-uri hack

```java
@TestPropertySource(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:0",
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:0/jwks"
})
```

Why? Spring Security's auto-config tries to resolve the issuer-uri's JWKS
endpoint at boot. If it points at a real `http://localhost:8095` that isn't
running, the test context fails to start. Pointing at port `:0` means Spring
constructs the decoder lazily — but since `jwt()` post-processor skips
decoding entirely, we never actually call JWKS. Port `:0` is a safe stub.

Alternative: WireMock. Overkill for this project but worth knowing.

---

## 4. Running tests

```bash
# All tests
cd /Users/bhanupratap/My/my-microservices
mvn -pl infra/resource-server test

# Expected:
#   Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
#     8 x CombinedAuthoritiesConverterTest (fast)
#     9 x ApiControllerTest (slower; full context)

# Specific test
mvn -pl infra/resource-server test -Dtest=CombinedAuthoritiesConverterTest

# With more verbose output
mvn -pl infra/resource-server test -Dmaven.surefire.debug=true
```

---

## 5. Dockerfile — the production shape

The old Dockerfile worked but had three issues:

```
┌──────────────────────────────────────────────────────────────────────────┐
│  Issue                          Fix                                       │
│  ─────────────────────────     ─────────────────────────────────────     │
│  COPY . . + mvn clean           Multi-stage with cached dep resolution:   │
│  → re-downloads deps every       1. COPY only pom files                   │
│  build                           2. mvn dependency:go-offline  (cached)   │
│                                  3. COPY source                            │
│                                  4. mvn package                            │
│                                                                            │
│  Runs as root                   USER app (non-root)                       │
│                                                                            │
│  No health check                HEALTHCHECK → /actuator/health            │
│                                                                            │
│  No container-aware JVM flags   JAVA_OPTS with                            │
│                                 UseContainerSupport + MaxRAMPercentage=75 │
└──────────────────────────────────────────────────────────────────────────┘
```

### BuildKit cache mount

```dockerfile
RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl infra/resource-server -am -DskipTests dependency:go-offline || true
```

The `--mount=type=cache` directive persists `~/.m2` **between builds** on the
Docker host. First build: downloads ~200MB of Maven deps. Second build: 0
MB — reuses the cache.

Without this, every `docker build` re-downloads everything.

Requires BuildKit (default in Docker Desktop ≥ 23 and Docker CE ≥ 23).
If someone's on older Docker, add `DOCKER_BUILDKIT=1 docker build ...`.

### Why non-root

```dockerfile
RUN groupadd --system app && useradd --system --gid app --home /app app
USER app
```

If the JVM is ever exploited (RCE via deserialisation, Log4Shell-style
vuln, etc.), the attacker only has `app` user privileges — not root.
Can't modify the container filesystem outside /app, can't `kill 1`, etc.

Standard container-security hygiene.

### Container-aware JVM

```dockerfile
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"
```

- **`UseContainerSupport`** — enabled by default on Java 17 but explicit is better. Tells the JVM to read cgroup limits instead of host memory/CPU.
- **`MaxRAMPercentage=75`** — JVM heap caps at 75% of container memory. Leaves 25% for native allocations (direct byte buffers, metaspace, Netty off-heap, etc.). Without this: OOMKilled surprises.
- **`java.security.egd=file:/dev/./urandom`** — speeds up startup on Linux (SecureRandom defaults to `/dev/random` which blocks waiting for entropy).

### Health check

```dockerfile
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=3 \
    CMD wget --quiet --spider http://localhost:8096/actuator/health || exit 1
```

Docker + K8s both honour this. Container is marked `unhealthy` if
`/actuator/health` returns non-200 three times in a row after a 30-second
startup grace period.

---

## 6. Building + running the image

```bash
# Build (from repo root — context is the entire reactor)
docker build -t resource-server:local -f resource-server/Dockerfile .

# Image size — should be ~250 MB (JRE 17 + fat jar + app shell)
docker images | grep resource-server

# Run
docker run --rm -p 8096:8096 \
    -e SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=http://host.docker.internal:8095 \
    resource-server:local

# Health check status
docker inspect --format='{{.State.Health.Status}}' <container_id>
# → healthy (after ~30s)
```

For multi-container local dev, add it to the existing observability compose
or wire a top-level docker-compose.yml.

---

## 7. Interview soundbites

| Question | Answer |
|---|---|
| Why `@SpringBootTest` + MockMvc for security tests? | Full context exercises the real `JwtAuthenticationConverter`, security filter chain, controller, `@PreAuthorize` interceptor. Catches wiring bugs. Slower than slice tests (~1-2s) but higher confidence. |
| Why not slice tests (`@WebMvcTest`)? | Faster but misses auto-config beans. Our custom converter wouldn't be exercised. Use slice tests for pure controller logic; use `@SpringBootTest` for anything involving Spring Security. |
| How does `jwt()` post-processor avoid real validation? | Installs a fake `JwtAuthenticationToken` directly into `SecurityContextHolder`. Short-circuits the entire JWT decode/validate pipeline. Tests only exercise the AUTHORIZATION side. |
| What's wrong with the old Dockerfile? | COPY . . re-downloads all Maven deps on every build. Runs as root. No health check. No JVM container-awareness. BuildKit cache mount + non-root + healthcheck fix all four. |
| Why `MaxRAMPercentage=75` not a fixed `-Xmx`? | `-Xmx` is static — need to update when container memory changes. `MaxRAMPercentage` scales with cgroup limits automatically. In K8s where you tune `resources.limits.memory`, JVM follows. |
| JVM startup slow in container? | Linux `/dev/random` can block waiting for entropy. `-Djava.security.egd=file:/dev/./urandom` uses non-blocking source. Saves 10-30s on cold start. |
| Why `UseContainerSupport` matters? | Without it, the JVM reads HOST memory/CPU, not container limits. In K8s with a 512MB limit on a 32GB host, JVM would try to allocate 24GB heap → OOMKilled. |
| What tests would you add next? | 1. Integration test using WireMock to serve fake JWKS → verifies full decode pipeline. 2. Load test with k6 → p99 under load. 3. ArchUnit → no controller in wrong package. |
| How do you test Spring Security specifically? | Three options: (a) `jwt()` post-processor — fast, skips crypto. (b) `@WithMockUser` — synthetic principal, no JWT at all. (c) Real JWT + WireMock JWKS — slower but exercises the full chain. Use the one matching the layer you're verifying. |

---

## 8. Common pitfalls

1. **Forgetting `spring-security-test` dep in `<scope>test</scope>`** — `jwt()` post-processor missing from classpath, tests fail to compile.
2. **`@WebMvcTest` with security** — Spring Security auto-config isn't loaded. Tests pass with "always 200" or "always 403" depending on default config. Use `@SpringBootTest`.
3. **Real issuer-uri in tests** — context startup fails if auth-server isn't running. Use `TestPropertySource` to stub it OR WireMock.
4. **Running Docker without BuildKit** — `--mount=type=cache` silently ignored → cold deps every build. Set `DOCKER_BUILDKIT=1` or upgrade Docker.
5. **COPY all source then run mvn** — invalidates cache on every code change. Copy pom first, resolve, THEN copy source.
6. **Running as root in container** — security risk + some ops teams forbid it in production clusters. Non-root is standard practice.
7. **Missing `--start-period`** on health check — container marked unhealthy during slow Spring Boot startup, orchestrator kills it. 30s is a safe floor.
8. **Hardcoded `-Xmx` in `JAVA_OPTS`** — doesn't scale with container limits. Use `MaxRAMPercentage` instead.

---

## 9. Extensions parked

- **Testcontainers** — spin up a real Postgres / Redis / WireMock inside tests. Overkill for resource-server (no DB) but useful once data is added.
- **WireMock for JWKS** — serve a static JWKS from a @BeforeAll-started WireMock server, generate matching JWTs with a known key. Exercises the full decode pipeline including signature validation.
- **ArchUnit** — static architecture tests. Rule: `controller.*` can only depend on `security.*` + `domain.*`, never on `config.*`. Prevents bad coupling via lint.
- **Mutation testing** (PIT) — mutate the code, re-run tests, verify they catch the mutation. Measures test quality beyond line coverage.
- **k6 / Gatling load test** — rate limiter, p99 under concurrent load, token-fetch pool exhaustion. Not applicable here (resource-server has no business logic) but worth having at the project level.
- **Docker image signing** — cosign/notation. Verifies the image you deployed is the one you built.
- **Scanned base image** — swap `eclipse-temurin:17-jre` for a scanned/minimized image (distroless, Chainguard, etc.). Smaller attack surface.
- **Dockerfile `.dockerignore`** — avoid copying `.git`, `target/`, IDE files into build context. Faster builds + no sensitive-file leakage.
