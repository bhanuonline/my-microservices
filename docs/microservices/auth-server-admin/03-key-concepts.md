# 03 — Key concepts (interview depth)

Everything non-trivial in this build — the parts you'd need to defend.

## 1. Spring profiles

**What:** a named set of beans / config that Spring activates together.

**How set:**
- `spring.profiles.active=inmemory` in properties (default)
- `-Dspring-boot.run.profiles=jdbc` at the CLI (overrides)
- `SPRING_PROFILES_ACTIVE` env var (overrides both)

**How consumed:** `@Profile("jdbc")` on any `@Configuration` / `@Bean` / `@Component`.

**Rule:** if you have two beans of the same type gated on mutually exclusive profiles, exactly one is created per boot → no NoUniqueBeanDefinitionException. If both are gated on the *same* profile, you'll crash.

**Alternative:** `@Conditional(...)` for anything more complex than a profile flag (e.g. "activate only if property X = Y").

---

## 2. Filter chains + `@Order` + `securityMatcher`

**Multiple `SecurityFilterChain` beans coexist.** Spring iterates them in `@Order` and picks the first whose matcher matches the request.

```java
@Bean @Order(0)
SecurityFilterChain admin(HttpSecurity http) {
    http.securityMatcher("/admin/**")     // ← ONLY /admin/**
        .authorizeHttpRequests(...)...    // ← rules apply only inside this scope
    return http.build();
}
```

Without `securityMatcher`, a chain matches every request. That's why `@Order(0)` alone would break the OAuth server — the `/admin` chain would try to handle `/oauth2/token`.

**Common mistake:** forgetting Order 0 has HIGHER priority than Order 1 (lower number wins).

---

## 3. `JdbcRegisteredClientRepository`

Spring's out-of-box replacement for `InMemoryRegisteredClientRepository`. Backed by `JdbcTemplate`.

**Not a Spring Data JPA repo** — it's a Spring Security class. Consequences:
- No `findAll()`. If you want a list, hit the table directly via `JdbcTemplate`.
- No `delete()`. Same — raw SQL.

**Schema requirement:** exactly the three tables Spring ships DDL for (`oauth2_registered_client`, `oauth2_authorization`, `oauth2_authorization_consent`). Column names/types are fixed — deviate and it breaks at runtime.

**Serialization gotcha:** `client_settings` and `token_settings` are `varchar(2000)` JSON, produced by Spring's Jackson mixins (`RegisteredClientMixin`, `ClientSettingsMixin`, etc.). Include `@class` polymorphic hints. Never hand-write these — go through `RegisteredClient.Builder` and let `repo.save()` serialize.

---

## 4. `JdbcOAuth2AuthorizationService`

Persists **issued tokens** (auth codes, access tokens, refresh tokens, ID tokens). Runtime state, not config.

Why persist? So:
- Refresh tokens survive server restart
- You can look up "who has active sessions?" (via `oauth2_authorization` table)
- You can revoke tokens (delete rows)

Similarly, `JdbcOAuth2AuthorizationConsentService` persists user consent grants ("user X approved scopes Y for client Z").

---

## 5. `DelegatingPasswordEncoder`

**Problem it solves:** you can't hardcode "BCrypt" everywhere, because 5 years from now you might want to move to Argon2, and old hashes still need to verify.

**How:** hashes are stored with a `{prefix}`. The delegating encoder reads the prefix to pick the matching encoder.

```
{bcrypt}$2a$10$…          → BCryptPasswordEncoder
{argon2}$argon2id$…       → Argon2PasswordEncoder
{noop}plaintext           → NoOpPasswordEncoder  (dev only, never prod)
{pbkdf2}…                 → Pbkdf2PasswordEncoder
```

**On encode:** uses the default (`{bcrypt}` in Spring 5+); prepends the prefix automatically.

**On matches:** reads prefix, dispatches to the right encoder.

**On unknown/absent prefix → throws:** `IllegalArgumentException: There is no PasswordEncoder mapped for the id "null"`. This is the exact 500 we hit when V6 seeded a raw BCrypt hash without the `{bcrypt}` prefix.

---

## 6. Flyway migrations

**Contract:** migration files are named `V<version>__<description>.sql`. Flyway runs them in version order once each, tracks in `flyway_schema_history` (checksum + description + timestamp).

**Immutable-history rule:** once a migration is applied, DO NOT edit the file. If you do:
- Next boot Flyway compares the file's checksum against history
- Mismatch → `FlywayValidateException` → boot fails
- Fixes: (a) revert the file, (b) run `flyway:repair` to update the recorded checksum (dev only), (c) add `V<n+1>__fix_thing.sql` (correct prod approach)

**Baseline vs. migrate:** if you point Flyway at an existing non-empty DB with no history table, it refuses to start. `spring.flyway.baseline-on-migrate=true` says "treat what's already there as version 0, start migrating from V1 on top". Useful for dev; risky for prod (silently assumes existing schema is correct).

**Alternative: Liquibase** — more powerful (rollback, DB-agnostic changesets in XML/YAML/JSON), heavier.

---

## 7. Hibernate `ddl-auto` modes

```
none       Hibernate does nothing to schema
validate   Compare entity metadata to DB; fail-fast if drift; DON'T modify
update     Add missing columns/tables; keep existing (never drops)
create     DROP + CREATE at boot (loses all data)
create-drop create at boot, drop at shutdown (in-memory / tests)
```

**Prod rule:** `validate`. Schema is Flyway's job. If entities and DB disagree, boot must fail, not silently drift.

**Our inmemory profile uses `update`** because the original code had no migrations — Hibernate lazily creates the (unused) tables in `authdb`.

---

## 8. JWT signing key persistence

**JWT verification chain:** resource-server fetches JWKS from auth-server (`GET /oauth2/jwks`) → caches it → for each incoming token, reads the `kid` from the JWT header → looks up the matching key → verifies signature.

**Regenerating the key on every boot means:**
- All previously-issued tokens have a `kid` that's no longer in JWKS
- Resource-servers reject them → every user logged out
- Refresh tokens still work only if the refresh operation returns a new token signed with the new key (not always)

**Persisting the key (our approach):**
- Same `kid` across restarts
- Old tokens keep verifying
- Rotate deliberately (add a new active row, mark old inactive after grace period)

**Prod hardening we didn't do:**
- Store key in HSM / KMS / Vault, not DB
- Support multiple active keys in JWKSet for zero-downtime rotation
- Encrypt the private_key column at rest

---

## 9. `@ElementCollection` vs `@ManyToMany`

For our `roles` collection:

```java
@ElementCollection(fetch = EAGER)
@CollectionTable(name = "app_user_role", joinColumns = @JoinColumn(name = "user_id"))
@Column(name = "role")
private Set<String> roles;
```

- **`@ElementCollection`**: the "many" side is a value type (String, Embeddable) — no identity, no independent lifecycle. Table has just `(user_id, role)`.
- **`@ManyToMany`**: the "many" side is an `@Entity` with its own PK — join table `(user_id, role_id)` plus a `role(id, name)` table.

Roles here are plain strings with no attributes → `@ElementCollection` is the right choice. Adds/removes cascade automatically because they're value-owned.

**EAGER for roles:** because Spring Security's `getAuthorities()` is called during authentication, before any transaction boundary — LAZY would throw `LazyInitializationException`.

---

## 10. Bootstrap via WebJar

**WebJar** = client-side library (CSS, JS, fonts) repackaged as a Maven artifact.

```
Add dependency:
  org.webjars:bootstrap:5.3.3

Files land at:
  META-INF/resources/webjars/bootstrap/5.3.3/**

Spring Boot auto-maps:
  /webjars/**  →  META-INF/resources/webjars/**

Serve to browser:
  <link rel="stylesheet"
        th:href="@{/webjars/bootstrap/css/bootstrap.min.css}">
        (no version in URL — webjars-locator resolves it)
```

**Why bother?**
- Version-locked with pom.xml
- Works offline
- No files checked into git
- Bumping = `<version>5.3.4</version>` — done

**Alternative:** CDN link (`<link href="https://cdn.jsdelivr.net/…">`). Fewer bytes shipped, needs internet.

---

## 11. Thymeleaf fragments

```
layout.html
  <nav th:fragment="nav"> ...bootstrap navbar... </nav>

dashboard.html
  <div th:replace="~{admin/layout :: nav}"></div>
       └──────────────────────┘   └──┘
       template location             fragment name
```

`th:replace` swaps the current element for the fragment. `th:insert` inserts the fragment inside the current element.

**Why not use a template inheritance library?** Thymeleaf Layout Dialect gives you `layout:decorate` for real inheritance. We skipped it — one extra dep for a small learning app.

**CSRF token auto-injection:** Thymeleaf's Spring integration adds a hidden `<input name="_csrf">` to every `<form th:action="…">` automatically. That's why our POST forms work without us writing the token by hand.

---

## 12. Data binding — how a form maps to a DTO

Spring's `WebDataBinder` reads request parameters and calls setters on the target object:

```
Request              DTO setter                      Result
─────────            ──────────                      ──────
clientId=my-app      setClientId("my-app")           form.clientId = "my-app"
grantTypes=code      setGrantTypes(...)              form.grantTypes = {"code"}
grantTypes=refresh   (repeated param → collection)    form.grantTypes = {"code","refresh"}
requireProofKey=on   setRequireProofKey(true)        HTML checkbox convention
                     (Spring converts "on" → true)
scopesCsv=a,b,c      setScopesCsv("a,b,c")           we defined this — parses to Set
```

**Validation with `@Valid`:** runs after binding, before controller method body. Errors accumulate in `BindingResult`. If you don't declare a `BindingResult` parameter right after the `@ModelAttribute` param, Spring throws MethodArgumentNotValidException → 400.

---

## 13. Why the OAuth `/login` and Admin `/login` are the same page

Only ONE login form exists — Spring's default (`DefaultLoginPageGeneratingFilter`).

- Order 2 chain (default) enables it via `formLogin(Customizer.withDefaults())` — form points to `POST /login` handled by that chain.
- Order 0 chain (admin) *also* enables `formLogin()` — but since the request `/login` isn't `/admin/**`, this chain never sees it. The login form lives in Order 2.
- Order 1 (OAuth) redirects HTML requests to `/login` via `LoginUrlAuthenticationEntryPoint` — same URL, same chain-2 form.

So logging in on `/login` sets the same `Authentication` in the session, and all subsequent requests (admin or OAuth interactive flows) see the same authenticated user.

---

## 14. The transaction boundary problem for JWKSource

Our `jwkSource()` method calls `keys.findFirstByActiveTrue()` (may return empty) then `keys.save(entity)` (may write). We annotated it `@Transactional` because:

- Called during bean init, no @Transactional service in between
- Without a transaction, the `save()` might commit but the surrounding "generate" logic could crash before you check post-conditions
- Simplest fix: wrap in `@Transactional` so JPA gets its Session properly opened

**Interview point:** always ask "does this DB call have a transaction context?" — Spring won't give you one for free unless you ask.

---

## 15. Feature flags (added as a first-class pattern)

**What:** every new feature ships behind a boolean in `application-<profile>.properties` so it can be toggled without redeploying code changes.

**How:**
```java
@ConfigurationProperties("features")
@Data
public class FeatureFlags {
    private RefreshTokenRotation refreshTokenRotation = new RefreshTokenRotation();
    // Add nested @Data class per new feature.
}
```

Wired via `@EnableConfigurationProperties(FeatureFlags.class)` in `FeaturesConfig`. Injected wherever needed (controllers, services, templates).

**Templates consume it directly:**
```html
<div th:if="${features.refreshTokenRotation.enabled}"> ... </div>
```

**Design rules:**
1. **Default OFF.** Every flag defaults to `false` in the Java class so a new deployment can't accidentally activate a half-tested feature.
2. **Log at boot** — `FeaturesConfig#logFeatureFlags` prints the resolved state on startup. Removes the "is the flag on or not?" mystery.
3. **Fail closed** — service code that reads a flag *and it's off* must do nothing / behave as if the feature never existed. Never partial behaviour.
4. **DB values untouched when flag is off** — flipping a feature off should not corrupt existing per-row state. E.g. clients with `reuseRefreshTokens=false` in the DB keep that setting even if the admin UI hides the toggle.

**Why properties, not a DB table?**
- Config change = ops flow (redeploy or spring-cloud-config refresh). Fits deployment discipline.
- DB-backed flag stores (LaunchDarkly, Unleash) are their own subsystem — separate project.
- Environment override at boot: `-Dfeatures.refresh-token-rotation.enabled=false` = kill switch on one node.

**Interview point:** "Feature flags let you decouple deploy from release. Ship code dark, flip flag when ready, rollback = flag flip not rollback deploy."

---

## 16. Refresh token rotation

**What:** each `/oauth2/token` refresh call returns a NEW refresh token; the old one becomes invalid.

**Why it matters:** breach detection. If attacker steals your refresh token and uses it, your next legitimate refresh call will 401 — you know something is wrong, you can force re-login.

**How it's wired:**
- Spring exposes `TokenSettings.reuseRefreshTokens(boolean)` per client.
- Our `ClientAdminService.save()` sets it based on `ClientForm.rotateRefreshTokens` when the flag is on.
- Feature flag: `features.refresh-token-rotation.enabled`.
  - OFF → service leaves `reuseRefreshTokens` at Spring's default (true, reuse forever).
  - ON → service reads the form checkbox (or `default-for-new` for new clients).

**Verification (Postman):**
```
1. Get initial pair (auth-code flow via browser gives access + refresh tokens).
2. POST /oauth2/token grant_type=refresh_token refresh_token=OLD
     → 200 { access_token, refresh_token: NEW } (different from OLD)
3. POST /oauth2/token grant_type=refresh_token refresh_token=OLD
     → 400 invalid_grant  (OLD is dead)
4. POST /oauth2/token grant_type=refresh_token refresh_token=NEW
     → 200 { access_token, refresh_token: NEWEST }
```

**Trade-off:**
- ROTATE: safer, but any race condition between client threads sharing a refresh token = accidental logout.
- REUSE: simpler, one refresh works forever, but stolen token = silent long-lived breach.

---

## 17. What we didn't build (be honest about scope)

- **Multi-tenant isolation** — one auth server, one DB, one universe.
- **Client secret rotation** — no history table, no expiry-then-rotate flow.
- **Audit trail** — `client_audit` table exists but no service writes to it. Wire it up in `ClientAdminService.save()` and `.delete()` with the current user's name.
- **PKCE enforcement on public clients** — admin can toggle it per-client but there's no policy enforcing "if authMethod=NONE then PKCE=required".
- **Key rotation UI** — kid is stable; no way to rotate without SQL.
- **Rate limiting on /login and /oauth2/token** — no lockout on brute force.
- **HTTPS** — everything is plaintext HTTP:8095 on localhost.
- **CORS for browser SPAs** — no `CorsConfigurationSource` bean; only the default Spring CORS filter is present.
- **User self-registration** — admin has to create users, no signup page.

Interview line: "The goal was to lift the auth-server from throw-away demo to something a real team could iterate on. Persistence, hashed secrets, migrations, and an admin UI cover the delta. Multi-tenancy, key rotation, and audit are the next lifts."
