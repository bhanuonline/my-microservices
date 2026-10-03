# Tier 3 — OAuth2 Depth

Turn the "hello world" into a working demonstration of:

- **Scope-based** method authorization (`SCOPE_read`, `SCOPE_write`)
- **Role-based** method authorization (`ROLE_ADMIN`)
- A custom **JwtAuthenticationConverter** that extracts both claims
- A diagnostic **`/api/me`** endpoint showing what the server actually sees

These are the resource-server patterns interviewers probe.

---

## 1. The two different "authority" concepts

Spring Security models permission with `GrantedAuthority`, a simple
`getAuthority(): String` interface. OAuth2 feeds two different things into
this abstraction:

```
┌──────────────────────────────────────────────────────────────────────────┐
│  Concept   Comes from              Prefix       Example                   │
│  ────────  ──────────────────────  ──────────   ───────────────────────   │
│  SCOPE     JWT `scope` claim       SCOPE_       SCOPE_read, SCOPE_write   │
│                                                 (what the TOKEN can do)   │
│                                                                            │
│  ROLE      JWT `roles` claim       ROLE_        ROLE_ADMIN, ROLE_USER     │
│            (or in-DB user record)               (who the USER is)         │
└──────────────────────────────────────────────────────────────────────────┘
```

Both end up as `GrantedAuthority` strings. The difference is **conceptual**:

- **Scope** = "what the delegated token is permitted to do on behalf of its
  owner." Tied to the OAuth2 token lifecycle.
- **Role** = "what the user is allowed to do, period." Tied to the user's
  identity.

Example:
> User Alice has `ROLE_ADMIN` in the database. She uses an OAuth2 client
> that only requested `SCOPE_read` when she authorized it. The resulting
> token can only READ things on her behalf — even though she's an admin —
> because SHE chose to delegate a limited slice of her power to the client.

Spring Security doesn't force this interpretation but the convention is
near-universal.

---

## 2. @PreAuthorize vs path-based rules

Two ways to enforce:

```
┌──────────────────────────────────────────────────────────────────────────┐
│  Path-based (in SecurityConfig)                                           │
│                                                                            │
│  http.authorizeHttpRequests(auth -> auth                                  │
│       .requestMatchers(POST, "/api/documents/**").hasAuthority("SCOPE_write")│
│       .requestMatchers(GET,  "/api/documents/**").hasAuthority("SCOPE_read") │
│       .anyRequest().authenticated());                                      │
│                                                                            │
│  Pros: centralised, visible when auditing security config                 │
│  Cons: policy lives far from code; URL + policy drift risk                │
│                                                                            │
│                                                                            │
│  Method-based (@PreAuthorize on controllers)                              │
│                                                                            │
│  @GetMapping("/documents")                                                │
│  @PreAuthorize("hasAuthority('SCOPE_read')")                              │
│  public List<Document> list() { ... }                                      │
│                                                                            │
│  Pros: policy next to code; refactor-safe; expression-rich               │
│         (hasAnyRole, principal fields, SpEL)                              │
│  Cons: scattered; need @EnableMethodSecurity to activate                   │
└──────────────────────────────────────────────────────────────────────────┘
```

We use **both** in this project — path-based for coarse rules (actuator
public, everything else authenticated) and method-based for fine-grained
scope/role gates. Interview point: *"method-level is better for business
rules; path-based is better for infra endpoints like actuator."*

---

## 3. @EnableMethodSecurity — the gate

Without this annotation, `@PreAuthorize` is silently ignored. One line in
`SecurityConfig`:

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity          // ← activates @PreAuthorize
public class SecurityConfig { ... }
```

Default settings enable `@PreAuthorize` and `@PostAuthorize` (both pre- and
post-method expression gates). To also allow legacy `@Secured`:
`@EnableMethodSecurity(securedEnabled = true)`.

---

## 4. The custom JwtAuthenticationConverter

Spring Security's default converter reads **only** the `scope` claim →
produces `SCOPE_*` authorities. Our auth-server also emits a `roles` claim
that the default converter ignores.

Fix: a `JwtAuthenticationConverter` that merges both.

```
JWT received by resource-server:
{
  "sub": "alice",
  "iss": "http://localhost:8095",
  "exp": 1234567890,
  "scope": "read write",         ← default converter → SCOPE_read, SCOPE_write
  "roles": ["ADMIN", "AUDITOR"]  ← ignored by default converter
}
                                        │
                                        │  SecurityConfig.jwtAuthenticationConverter()
                                        ▼
Authentication authorities:
  SCOPE_read            (from scope)
  SCOPE_write           (from scope)
  ROLE_ADMIN            (from roles, uppercased + prefixed)
  ROLE_AUDITOR          (from roles, uppercased + prefixed)
```

### CombinedAuthoritiesConverter (the implementation)

Lives inside `SecurityConfig` as a static nested class. Delegates the `scope`
part to Spring's built-in `JwtGrantedAuthoritiesConverter`, then appends
roles.

Key design choices:

- **Normalisation**: `"USER"` → `"ROLE_USER"`. Already-prefixed `"ROLE_X"`
  passes through unchanged (no double prefix).
- **Flexibility**: `roles` can be a `List` OR a comma-separated string OR a
  space-separated string. Different OAuth2 providers emit different formats.
- **Logs**: debug log at the end so you can diagnose "why doesn't my
  authority show up?" issues.

See source: `resource-server/src/main/java/com/example/resourceserver/SecurityConfig.java`.

---

## 5. The four endpoints

```
┌───────────────────────────────────────────────────────────────────────────┐
│  Method  Path         @PreAuthorize                 What it does           │
│  ──────  ───────────  ──────────────────────────   ─────────────────────   │
│  GET     /api/hello   hasAuthority('SCOPE_read')    "Hello, <sub>"         │
│  POST    /api/echo    hasAuthority('SCOPE_write')   echoes JSON body       │
│  GET     /api/admin   hasRole('ADMIN')              admin-only ping        │
│  GET     /api/me      (any auth)                    full JWT claim dump    │
└───────────────────────────────────────────────────────────────────────────┘
```

### /api/me — the diagnostic goldmine

```json
GET /api/me
Authorization: Bearer eyJhbG...

{
  "name": "alice",
  "authorities": [ "SCOPE_read", "SCOPE_write", "ROLE_ADMIN" ],
  "issuer": "http://localhost:8095",
  "issuedAt": "2026-10-01T10:30:00Z",
  "expiresAt": "2026-10-01T11:30:00Z",
  "tokenType": "JWT",
  "claims": {
    "sub": "alice",
    "iss": "http://localhost:8095",
    "scope": "read write",
    "roles": ["ADMIN"],
    "iat": 1727778600,
    "exp": 1727782200,
    "jti": "..."
  }
}
```

Why it matters:
- **Debugging** — "why am I getting 403?" → curl /api/me, inspect
  `authorities`. If `SCOPE_admin` is missing, token doesn't have the scope.
- **Teaching** — great demo for showing what a JWT contains.
- **Auditing** — hit /api/me from different clients → compare what each sees.

No sensitive-info risk — the client already issued the token.

---

## 6. Interview soundbites

| Question | Answer |
|---|---|
| What's the difference between a scope and a role? | Scope = what the TOKEN is permitted to do (OAuth2 concept, token-level grant). Role = who the USER is (identity-level grant). Both end up as `GrantedAuthority` in Spring. |
| Why `hasAuthority('SCOPE_read')` and not `hasRole('read')`? | `hasRole` adds a `ROLE_` prefix. Scopes convention uses `SCOPE_` prefix. Using `hasRole('read')` would check for `ROLE_read`, which isn't what we want. |
| Where do SCOPE_* and ROLE_* authorities come from? | A `JwtAuthenticationConverter` reads the JWT claims (`scope`, `roles`) and produces authorities. The default converter only handles `scope`. We added a custom one that handles both. |
| Why `@EnableMethodSecurity`? | Without it, `@PreAuthorize` annotations are silently ignored. One line to activate the aspect that intercepts method calls. |
| @PreAuthorize vs @Secured vs @RolesAllowed? | @PreAuthorize is the modern Spring Security annotation with SpEL (expression language). @Secured is the older simple-list variant. @RolesAllowed is the JSR-250 standard version. Prefer @PreAuthorize for new code. |
| Method-level vs path-based security? | Both are valid. Method-level is better for business rules (survives refactoring, lives next to code). Path-based is better for infra rules (actuator endpoints, static resources). Use both. |
| @PreAuthorize vs @PostAuthorize? | @PreAuthorize runs BEFORE the method — can use method args. @PostAuthorize runs AFTER — can use the return value in the SpEL expression (e.g. "returnObject.owner == authentication.name"). |
| What's the SpEL evaluation context? | `authentication` (the current principal), `principal`, and all method args by name. Also any beans via `@...` syntax. |
| How is JWKS different from introspection? | JWKS: resource-server fetches auth-server's public key, validates signature locally. Zero network calls per request. Can't revoke tokens pre-expiry. Introspection: resource-server POSTs each token to auth-server's /introspect endpoint. One network call per request. Instant revocation. Trade-off: latency vs revocability. |
| How often does Spring Security refetch JWKS? | On startup + every 5 min by default (via `NimbusReactiveJwtDecoder`'s cache). Also on-demand when a `kid` is unknown (handles key rotation). |

---

## 7. Verification

```bash
# Boot auth-server + resource-server
mvn -pl auth-server     spring-boot:run    # :9010
mvn -pl resource-server spring-boot:run    # :8096

# Fetch a token with scope=read
TOKEN_READ=$(curl -s -u admin:admin123 \
  -d "grant_type=client_credentials&scope=read" \
  http://localhost:9010/oauth2/token | jq -r .access_token)

# GET /api/hello works
curl -i -H "Authorization: Bearer $TOKEN_READ" http://localhost:8096/api/hello
# → 200 Hello, admin

# POST /api/echo fails (no SCOPE_write)
curl -i -X POST -H "Authorization: Bearer $TOKEN_READ" \
     -H "Content-Type: application/json" -d '{"msg":"hi"}' \
     http://localhost:8096/api/echo
# → 403 Forbidden

# Fetch a token with scope=write
TOKEN_WRITE=$(curl -s -u admin:admin123 \
  -d "grant_type=client_credentials&scope=write" \
  http://localhost:9010/oauth2/token | jq -r .access_token)

# POST /api/echo works
curl -s -X POST -H "Authorization: Bearer $TOKEN_WRITE" \
     -H "Content-Type: application/json" -d '{"msg":"hi"}' \
     http://localhost:8096/api/echo | jq
# → 200 { echoedBy, timestamp, payload }

# No auth → 401
curl -i http://localhost:8096/api/hello
# → 401 Unauthorized

# Diagnostic: what does the server see?
curl -s -H "Authorization: Bearer $TOKEN_READ" http://localhost:8096/api/me | jq
# → full dump of principal + authorities + raw JWT claims
```

For `/api/admin` to work, your auth-server token needs a `roles` claim
containing `ADMIN` (or `ROLE_ADMIN`). If auth-server doesn't currently emit
one, add it in auth-server's `OAuth2TokenCustomizer`:

```java
@Bean
OAuth2TokenCustomizer<JwtEncodingContext> rolesCustomizer() {
    return ctx -> {
        if (ctx.getPrincipal().getName().equals("admin")) {
            ctx.getClaims().claim("roles", List.of("ADMIN"));
        }
    };
}
```

---

## 8. Common pitfalls

1. **Scope mismatch** — client requests `scope=read` but tries to call
   `/api/echo` (needs SCOPE_write). Fix: request both: `scope=read write`.
2. **`hasRole('ADMIN')` vs `hasAuthority('ADMIN')`** — the first checks for
   `ROLE_ADMIN`; the second for exactly `ADMIN`. If the converter produced
   `ROLE_ADMIN`, use `hasRole('ADMIN')`.
3. **Forgetting `@EnableMethodSecurity`** — `@PreAuthorize` silently
   ignored. 403? 200 for everyone? Compiles clean. Easy to miss.
4. **Default converter ignores custom claims** — if auth-server emits
   `roles` but you don't register a custom `JwtAuthenticationConverter`,
   those authorities don't exist in Spring.
5. **Claim naming inconsistency** — some providers use `scp` not `scope`,
   or `authorities` not `roles`. `JwtGrantedAuthoritiesConverter.setAuthoritiesClaimName()`.
6. **Case sensitivity** — authorities are case-sensitive strings in Spring.
   `SCOPE_Read` ≠ `SCOPE_read`. Our converter normalises to upper-case for
   roles but preserves scope case (matches OAuth2 spec).
7. **Expression language typos** — `@PreAuthorize("hasRole('ADMIN'")`
   (missing paren) compiles. Fails silently at runtime. Prefer named
   constants / annotation `@HasAdminRole` wrappers in bigger projects.
8. **Testing with wrong authority string** — MockMvc `.with(jwt().authorities(...))`.
   Pass `new SimpleGrantedAuthority("SCOPE_read")`, not `"read"`.

---

## 9. Extensions parked

- **@PostAuthorize for row-level security** — `@PostAuthorize("returnObject.owner == authentication.name")`.
- **Custom permission evaluator** — `@PreAuthorize("hasPermission(#id, 'Document', 'read')")`. Great when permissions depend on DB state.
- **Opaque-token mode** — swap JWKS for `/introspect` endpoint via `spring.security.oauth2.resourceserver.opaquetoken.*`. Alternative when you need instant token revocation.
- **Scope-based path matcher** — `authorizeHttpRequests` with path-level scopes for coarse rules, @PreAuthorize for method-level. Mixed strategy.
- **Multi-tenant isolation** — tenant_id claim in JWT, custom AuthorizationManager that filters data by tenant. Needs @PreFilter / @PostFilter.
