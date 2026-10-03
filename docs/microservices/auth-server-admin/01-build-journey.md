# 01 — Build journey (phase by phase)

We built this in 7 phases. Each phase compiled and (where applicable) booted before moving on — the point was to isolate risk.

## Phase 0 — Profile scaffolding

**Goal:** add profile support with zero behaviour change.

**Files touched:**
- `application.properties` → added `spring.profiles.active=inmemory`
- `application-inmemory.properties` (new, empty placeholder)
- `application-jdbc.properties` (new, empty placeholder)
- `SecurityConfig.java` → `@Profile("inmemory")` on 4 beans:
  `registeredClientRepository`, `authorizationService`, `jwkSource`, `userDetailsService`

**Why annotate only 4?** Those 4 have JDBC twins coming later. The other beans in `SecurityConfig` (both `SecurityFilterChain` beans, `AuthorizationServerSettings`, `JwtDecoder`, `tokenCustomizer`) are shared across profiles, so no annotation.

**Verified:** booted `inmemory`, everything worked as before.

---

## Phase 1 — Dependencies

**File touched:** `auth-server/pom.xml`

Added 7 deps (all inheriting versions from Spring Boot 3.2.5 BOM):
- `spring-boot-starter-thymeleaf` — templating engine for `/admin` pages
- `spring-boot-starter-validation` — enables `@Valid` on form DTOs
- `spring-boot-starter-jdbc` — needed by `JdbcRegisteredClientRepository`
- `flyway-core` — migrations
- `flyway-mysql` — MySQL 8 support (Flyway 9+ split this out)
- `thymeleaf-extras-springsecurity6` — `sec:authorize` in templates
- `webjars/bootstrap` 5.3.3 + `webjars-locator-core` — CSS/JS via Maven

**Why webjars?** Delivers Bootstrap as a JAR that Spring serves at `/webjars/bootstrap/**`. Locator lets you skip the version in URLs (`/webjars/bootstrap/css/bootstrap.min.css`). No downloaded files in git.

**Verified:** compiled clean. Zero runtime effect until beans start using these.

---

## Phase 2 — Property files

**File touched:** `application-jdbc.properties` (filled in), `application-inmemory.properties` (`spring.flyway.enabled=false`)

```properties
# application-jdbc.properties (the key lines)
spring.datasource.url=jdbc:mysql://localhost:3309/authdb_jdbc?createDatabaseIfNotExist=true&…
spring.jpa.hibernate.ddl-auto=validate       # Hibernate NEVER mutates schema
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
```

**Why `createDatabaseIfNotExist=true`?** So you don't need to run `CREATE DATABASE authdb_jdbc` manually. MySQL Connector/J does it on first connect.

**Why `ddl-auto=validate`?** Prod discipline. Hibernate compares entity shape against DB shape and refuses to boot if they diverge. Schema evolution goes exclusively through Flyway migrations.

**Why `baseline-on-migrate=true`?** Safety net for cases where you point Flyway at a DB that already has tables from a previous experiment. Without this Flyway refuses to start unless the history table exists.

---

## Phase 3 — Flyway migrations

**Files created:** `src/main/resources/db/migration/V1..V6.sql`

```
V1  spring authz schema      ← copied verbatim from spring-security-oauth2-authorization-server jar
                              → oauth2_registered_client
                              → oauth2_authorization
                              → oauth2_authorization_consent
                              Only tweak: blob → longblob (MySQL 8 size limits)

V2  app_user                 id, username UNIQUE, password, email, enabled,
                              created_at, updated_at

V3  app_user_role            (user_id, role) PK, FK → app_user ON DELETE CASCADE

V4  signing_key              kid PK, public_key TEXT, private_key TEXT, active, created_at

V5  client_audit             actor, action, client_id, changed_at, diff_json
                              (table ready — nothing writes to it yet)

V6  seed                     admin user (BCrypt), demo-client, m2m-client
                              → mirrors the hardcoded InMemory beans exactly,
                                so external clients keep working after switching profiles
```

**Where the V1 SQL came from:** Spring ships canonical `.sql` files inside `spring-security-oauth2-authorization-server-1.2.4.jar` under `org/springframework/security/oauth2/server/authorization/`. Extracted with `jar xf`, concatenated into V1, changed `blob` → `longblob`.

**Why `longblob`?** Spring's JDBC service serializes tokens to `attributes` / `authorization_code_metadata` / etc. as `blob`. MySQL's `blob` is capped at 64KB; a fresh RSA-signed token pushes past that. `longblob` = 4GB, safe.

**Why fixed UUIDs in V6 (e.g. `'demo-client-uuid-0000000000000001'`)?** Deterministic seeds — same DB state on every fresh boot. Tests / demos become reproducible.

**Client secret format in V6:** `{bcrypt}$2a$10$…` — the `{bcrypt}` prefix tells `DelegatingPasswordEncoder` which algorithm to use. Same for the admin user password. (We hit a bug where the admin was missing the prefix — fixed by editing V6 and running `flyway repair`. See journey note below.)

---

## Phase 4 — Entities + repositories

**Files:**
- **Fixed** `AppUser.java` — was missing `@Entity`; now has `@Entity`, `@Table("app_user")`, timestamps, and `@ElementCollection` of role strings mapped to `app_user_role`
- **Fixed** `CustomUserDetails.java` — `getAuthorities()` now maps a `Set<String>` roles instead of a single string; `isEnabled()` reads the field
- **New** `SigningKeyEntity.java` — maps `signing_key` table
- **New** `AppUserRepository` — `JpaRepository<AppUser, Long>` + `findByUsername`
- **New** `SigningKeyRepository` — `JpaRepository<SigningKeyEntity, String>` + `findFirstByActiveTrue`

**Why `@ElementCollection` and not a separate `Role` entity?** Roles are just strings. A `Role` entity would be a table of unique names with a join table — 2 extra classes to enforce nothing. `@ElementCollection` maps directly to `app_user_role(user_id, role)` and is simpler.

**Journey note:** we also had to **delete `OAuthClient.java`** — it had `@Entity` but no matching table. On `inmemory` profile Hibernate silently created `oauth_client` with `ddl-auto=update`. On `jdbc` with `ddl-auto=validate`, boot failed. Removing the unused stub fixed it.

---

## Phase 5 — JdbcSecurityConfig

**File:** `config/JdbcSecurityConfig.java` (new, ~190 lines, all `@Profile("jdbc")`)

Beans wired:
```
RegisteredClientRepository          JdbcRegisteredClientRepository
OAuth2AuthorizationService          JdbcOAuth2AuthorizationService
OAuth2AuthorizationConsentService   JdbcOAuth2AuthorizationConsentService
UserDetailsService                  Loads via AppUserRepository → CustomUserDetails
PasswordEncoder                     PasswordEncoderFactories.createDelegatingPasswordEncoder()
JWKSource<SecurityContext>          Reads signing_key row; generates + persists if empty
SecurityFilterChain @Order(0)       securityMatcher("/admin/**") + hasRole(ADMIN)
```

**Why `@Order(0)` + `securityMatcher`?** Order 0 = higher priority than Order 1 (OAuth) and Order 2 (default). But `securityMatcher("/admin/**")` limits it to admin URLs — so it doesn't steal `/oauth2/token`. Without the matcher an Order-0 chain would grab every request. Elegant isolation.

**How the signing-key bootstrap works:**
```
1. jwkSource() bean invoked at startup
2. keys.findFirstByActiveTrue()
   → present?  parse PEM → build RSAKey → JWKSet → done
   → absent?   generateKeyPair(RSA 2048)
               encode as PEM strings
               SigningKeyEntity(kid=UUID, public, private, active=true)
               save via repository
               continue with the just-created key
```

Since the method returns `JWKSource`, subsequent boots reuse the same `kid` — resource-servers that cached the JWKS keep verifying old tokens.

---

## Phase 6 — Admin UI

**Files:** 5 templates, 3 controllers, 2 services, 2 DTOs, 1 edit to SecurityConfig.

```
controller/admin/
  AdminHomeController        GET /admin  → dashboard
  AdminClientController      CRUD on /admin/clients/**
  AdminUserController        CRUD on /admin/users/**

service/admin/
  ClientAdminService         DTO ↔ RegisteredClient.Builder
                             list via raw JdbcTemplate (Jdbc repo has no findAll)
                             delete via raw JdbcTemplate (Jdbc repo has no delete)
  UserAdminService           DTO ↔ AppUser (BCrypts on save)

dto/
  ClientForm                 flat, form-friendly view of RegisteredClient
                             + setScopesCsv() to parse comma-separated input
  UserForm                   flat, form-friendly view of AppUser

templates/admin/
  layout.html                nav fragment (Bootstrap navbar + logout form)
  dashboard.html             2 cards linking to Clients / Users
  clients/list.html          table + New/Edit/Delete
  clients/form.html          checkboxes for grants/scopes/auth-methods,
                             textareas for redirect URIs
  users/list.html            table with roles as badges
  users/form.html            role checkboxes + enabled toggle
```

**Edit to `SecurityConfig.java`:** added `/webjars/**` to permitAll on the default (Order 2) chain — otherwise Bootstrap CSS wouldn't load on the /login page.

**Why hardcode the list of grant types / auth methods / roles?** Spring's `AuthorizationGrantType` and friends have no `values()` — you build them from strings. For learning, a fixed list is clearer than reflection.

**Blank-password-on-edit UX:** both `ClientAdminService.save()` and `UserAdminService.save()` treat a blank password field on edit as "keep existing". That way you don't accidentally overwrite a secret when only changing scopes.

---

## Post-Phase 6 fixes (real-world debugging)

Three issues hit during the first jdbc boot:

**1. `Schema-validation: missing table [oauth_client]`**
Cause: `OAuthClient.java` had `@Entity` but no migration created the table. Fix: deleted the unused stub file.

**2. HTTP 500 on POST /login: "There is no PasswordEncoder mapped for the id null"**
Cause: seeded admin password had no `{bcrypt}` prefix; `DelegatingPasswordEncoder` couldn't dispatch. Fix:
- ran `UPDATE app_user SET password = CONCAT('{bcrypt}', password)` against the live DB
- edited V6 so fresh DBs work out of the box
- ran `mvn flyway:repair` to reconcile the checksum

**3. HTTP 500 on GET /: "No primary or single unique constructor found for OAuth2AuthorizedClient"**
Cause: legacy `ClientController.home(OAuth2AuthorizedClient authorizedClient)` — Spring tried to bind that param from query string. After login, Spring redirects to `/`, hit this crash. Fix: rewrote `ClientController` to `return "redirect:/admin"`.

---

## Rule learned the hard way

> **Never edit a Flyway migration that has already been applied to a live DB.**
> Add a new `V7__fix_x.sql` instead. Editing means `flyway_schema_history.checksum` no longer matches the file, and Flyway refuses to boot. Our workaround (`flyway:repair`) was only OK because it's a dev-only learning DB.
