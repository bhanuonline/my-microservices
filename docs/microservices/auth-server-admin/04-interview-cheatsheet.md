# 04 — Interview cheatsheet

Rapid-fire Q&A. Practice speaking these out loud.

## The 60-second project pitch

> "I extended a Spring Authorization Server from an in-memory learning setup to a production-shaped one, without deleting any of the original code. Spring profiles switch between them: `inmemory` keeps the original hardcoded clients and users; `jdbc` swaps to `JdbcRegisteredClientRepository`, DB-backed users with BCrypt-hashed passwords, a persisted RSA signing key, Flyway-managed schema, and a Thymeleaf/Bootstrap admin UI at `/admin` for managing clients and users through a browser. Both paths coexist and boot side-by-side."

---

## Q — Why profiles instead of just replacing the in-memory beans?

Two reasons. First, the project is a study repo — losing the original code loses the reference. Second, profiles let you A/B compare behaviour: boot both simultaneously on different ports if you want to see what breaks. Every profile-specific bean is annotated `@Profile("inmemory")` or `@Profile("jdbc")`, so Spring wires exactly one at a time.

## Q — Walk me through the filter chains.

Three chains, three orders.
- **Order 0**: `securityMatcher("/admin/**")` + `hasRole("ADMIN")` + formLogin + CSRF. Only exists on the `jdbc` profile.
- **Order 1**: default OAuth2 authorization-server chain — `/oauth2/**`, `/.well-known/**`, `/connect/**`. Shared.
- **Order 2**: catch-all — `/login`, `/error`, `/actuator/**`, `/webjars/**` are permitAll, everything else requires authentication. Shared.

Spring iterates in ascending order and stops at the first chain whose matcher matches. `securityMatcher` on Order 0 is critical — without it, the admin chain would try to handle `/oauth2/token` and break everything.

## Q — How does data get from MySQL to a JWT?

Two paths, two Spring components:

1. **`JdbcRegisteredClientRepository`** reads from `oauth2_registered_client` when a client authenticates at `/oauth2/token`. It rehydrates a `RegisteredClient` object from the row, including grant types, scopes, and the token settings JSON.

2. **`JWKSource`** (my custom bean) reads a PEM-encoded key from `signing_key` at startup, builds an `RSAKey` and wraps it in a `JWKSet`. The token endpoint uses this to sign, and `/oauth2/jwks` publishes the public half.

The token itself is created inline in the token filter — no DB call per token — but the *ability* to make it depends on both storage lookups.

## Q — Why persist the signing key?

Because `kid` (key id) is baked into every JWT header. Resource-servers cache JWKS and use `kid` to find the matching public key. If the auth-server regenerates the key on every boot, every previously-issued token has a `kid` that's no longer in JWKS → resource-servers reject them → every user is logged out. Persisting the key means `kid` is stable across restarts. Rotation becomes a deliberate operation (add new active row, keep old for grace period).

## Q — Explain BCrypt with the `{bcrypt}` prefix.

We use `DelegatingPasswordEncoder`. It looks at the prefix of a stored hash to pick the algorithm. `{bcrypt}$2a$10$xyz` → BCrypt. `{argon2}$argon2id$…` → Argon2. Without a prefix, it throws `IllegalArgumentException`. That let us hit a real bug during this build — the admin user was seeded as raw `$2a$10$…` without the prefix, so login POST returned 500. Fixed by prepending `{bcrypt}` in both the live DB and the V6 migration.

## Q — What's Flyway and why not just Hibernate `ddl-auto=update`?

Flyway = versioned SQL migrations run in order once each, tracked in a `flyway_schema_history` table. Hibernate `ddl-auto=update` is convenient in dev but silently mutates schema based on entity changes, which is dangerous in prod — you don't want a field rename to silently drop and recreate columns.

Our jdbc profile uses `ddl-auto=validate` (fail-fast on drift) + Flyway for actual schema changes. Migrations are `V1..V6.sql`. Iron rule: once applied, never edit — add a new `V<n+1>` instead. We broke that rule during this build and had to run `flyway:repair` to reconcile the checksum. Dev only.

## Q — Why did you use `@ElementCollection` for roles?

Roles are just strings — no identity, no attributes. A full `@ManyToMany` with a `Role` entity would need a `role(id, name)` table plus a join table, for no benefit. `@ElementCollection` maps `Set<String> roles` directly to `app_user_role(user_id, role)`. Value-owned lifecycle: delete a user, roles cascade away.

Also EAGER-fetched because `UserDetails.getAuthorities()` is called by Spring Security during authentication, before any transaction is open — LAZY would hit `LazyInitializationException`.

## Q — How does CSRF work in the admin UI?

Spring Security's `CsrfFilter` generates a token per session, stores it in `HttpSession`, and expects it back on every state-changing request (POST/PUT/DELETE). Thymeleaf's Spring integration auto-injects `<input name="_csrf">` into every `<form th:action="…">`, so the round-trip works without me writing it. The token check happens before controller invocation — mismatch → 403.

For the OAuth token endpoint, Spring internally disables CSRF (clients don't have sessions). For actuator we explicitly disable it because we hit it from health-checks.

## Q — What happens end-to-end when I click Save on a new-client form?

1. Browser POSTs `/admin/clients` with form fields + CSRF token.
2. Order 0 chain matches, `CsrfFilter` validates the token, `AuthorizationFilter` checks `hasRole('ADMIN')`.
3. Spring's `WebDataBinder` populates a `ClientForm` DTO (multi-value params like `grantTypes=code&grantTypes=refresh` become a `Set<String>`).
4. `@Valid` triggers Bean Validation; errors flow into `BindingResult`.
5. Controller calls `ClientAdminService.save(form)`.
6. Service BCrypts the plaintext secret, builds a `RegisteredClient` via `RegisteredClient.Builder`, calls `repo.save(rc)`.
7. `JdbcRegisteredClientRepository` serializes `ClientSettings` and `TokenSettings` via Spring's Jackson mixins (with `@class` polymorphic hints), issues an INSERT.
8. Controller returns `redirect:/admin/clients`, browser GETs the list, sees the new row.

## Q — What's a WebJar?

A client-side library (Bootstrap, jQuery, whatever) packaged as a Maven JAR under `META-INF/resources/webjars/**`. Spring Boot auto-serves it at `/webjars/**`. Adding Bootstrap = one `<dependency>` in `pom.xml`; no downloaded files in git, version pinned by Maven. `webjars-locator-core` resolves the version at runtime so URLs don't need to include it.

## Q — Why did you keep the original in-memory beans?

- Loses no learning material — original code stays as a reference.
- Debugging: if the JDBC path breaks, boot inmemory to isolate whether it's a DB issue vs. an OAuth logic issue.
- Onboarding: someone reading the repo can see the simplest form before the persisted form.
- Interview: I can talk to both.

## Q — What did you *not* build that you would in production?

- Store private RSA key in KMS/HSM/Vault, not the DB.
- Support multiple active keys in JWKSet for zero-downtime rotation.
- Client secret rotation with grace period.
- Populate the `client_audit` table on every create/update/delete.
- Rate limiting on `/login` and `/oauth2/token`.
- HTTPS termination (fine on localhost, mandatory in prod).
- User self-registration + email verification.
- CORS config for SPA clients.

## Q — Where's the biggest weakness in what you built?

Storing the private RSA key in a DB column, base64-encoded, unencrypted. Anyone with `SELECT` on `signing_key` can forge tokens. For learning it's fine; for prod it needs a KMS or at least column-level encryption. I'd move it behind an interface (`SigningKeyStore`) so the DB variant is one impl among several.

---

## Buzzword checklist — say these correctly

| Term | One-line definition |
|---|---|
| OAuth 2.1 | Authorization framework — grant types, scopes, tokens |
| OpenID Connect (OIDC) | Identity layer over OAuth 2 — adds ID tokens with user claims |
| JWT | JSON Web Token — signed, self-contained bearer token |
| JWKS | JSON Web Key Set — public keys published at `/oauth2/jwks` for verification |
| `kid` | Key ID in JWT header — tells verifier which JWK to use |
| `client_credentials` grant | Machine-to-machine; no user involved; token identifies the client |
| `authorization_code` grant | User in browser; two-step (code → token); can add PKCE |
| PKCE | Proof Key for Code Exchange — mitigates code interception for public clients |
| BCrypt | Adaptive hashing (cost factor 10 = 2^10 rounds) |
| Delegating password encoder | `{algorithm}hash` prefix picks the verifier |
| `securityMatcher` | Limits a `SecurityFilterChain` to a URL pattern |
| Filter chain order | Ascending; first matching chain wins |
| CSRF | Cross-site request forgery — form token per session |
| Flyway | Versioned SQL migrations, immutable history |
| `ddl-auto=validate` | Hibernate checks entities vs. schema; fails on drift; never mutates |
| `@ElementCollection` | JPA mapping for a collection of value types (not entities) |
| `RegisteredClient` | Spring Auth Server's client model |
| `JWKSource<SecurityContext>` | Spring interface returning the JWKSet to sign/verify JWTs |
| WebJar | Client-side library packaged as a Maven artifact |
| Thymeleaf fragment | Reusable template chunk included via `th:replace` / `th:insert` |

---

## Red-flag questions to interview *back*

If you're being interviewed on this, ask them:

- "Where does your prod auth-server store its signing key? KMS? Vault? DB?"
- "How do you rotate client secrets without breaking live traffic?"
- "How do you audit who changed which OAuth client, and when?"
- "What's your Flyway history strategy — squash old migrations, or keep V1 forever?"

Signals you're thinking beyond the tutorial.
