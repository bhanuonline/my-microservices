# 00 — Architecture

## The idea: two profiles, one codebase

We didn't rewrite the auth-server. We layered a "production-shaped" path **next to** the existing "learning" path, and Spring picks one at boot time.

```
┌──────────────────────────────────────────────────────────────────────┐
│                       spring.profiles.active                         │
│                                                                      │
│                  ┌───────────┴───────────┐                           │
│                  ▼                       ▼                           │
│         profile: inmemory        profile: jdbc                       │
│         (default, day 1)         (what we built)                     │
│                                                                      │
│  Clients:  in-memory hardcoded   MySQL: oauth2_registered_client     │
│  Users:    in-memory User.with…  MySQL: app_user + app_user_role     │
│  Signing:  new RSA every boot    MySQL: signing_key (persisted)      │
│  Schema:   ddl-auto=update       Flyway V1..V6 migrations            │
│  UI:       none                  /admin (Thymeleaf + Bootstrap)      │
│  DB name:  authdb                authdb_jdbc                         │
└──────────────────────────────────────────────────────────────────────┘
```

Every bean that differs between profiles is gated by `@Profile("inmemory")` or `@Profile("jdbc")`. Any bean shared across both — like the OAuth2 filter chain — has no profile annotation.

**Why do it this way?**
- No lost learning code — the original in-memory setup still boots for study/demo.
- Real prod behaviour (persisted state, real DB, migrations, hashed secrets) is one flag away.
- Easy to A/B compare: run both on different ports simultaneously if you want.

---

## The layered picture

```
┌────────────────────────────────────────────────────────────────────┐
│  Browser (admin)                                                   │
│    ↓ HTTPS / HTTP + form login                                     │
└────────────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────▼──────────────────────────────────────┐
│  Spring Boot 3.2.5   Tomcat :8095                                  │
│                                                                    │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  Spring Security FilterChain(s) — order matters              │  │
│  │                                                              │  │
│  │  Order 0  /admin/**                (jdbc only)               │  │
│  │            hasRole(ADMIN) + formLogin + CSRF                 │  │
│  │                                                              │  │
│  │  Order 1  /oauth2/**, /.well-known/**, /connect/**           │  │
│  │            OAuth2 authorization-server default security      │  │
│  │                                                              │  │
│  │  Order 2  everything else                                    │  │
│  │            login/error/actuator/webjars = permitAll          │  │
│  │            anyRequest = authenticated                        │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                    │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  MVC controllers                                             │  │
│  │    /admin           AdminHomeController      (jdbc)          │  │
│  │    /admin/clients   AdminClientController    (jdbc)          │  │
│  │    /admin/users     AdminUserController      (jdbc)          │  │
│  │    /                ClientController         (both)          │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                    │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  Service layer                                               │  │
│  │    ClientAdminService — DTO ↔ RegisteredClient (jdbc)        │  │
│  │    UserAdminService   — DTO ↔ AppUser + BCrypt (jdbc)        │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                    │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  Persistence — three parallel worlds                         │  │
│  │                                                              │  │
│  │  A. Spring OAuth2 storage (JDBC repositories, no JPA):       │  │
│  │       JdbcRegisteredClientRepository                         │  │
│  │       JdbcOAuth2AuthorizationService                         │  │
│  │       JdbcOAuth2AuthorizationConsentService                  │  │
│  │       ↓ JdbcTemplate                                         │  │
│  │       oauth2_registered_client                               │  │
│  │       oauth2_authorization                                   │  │
│  │       oauth2_authorization_consent                           │  │
│  │                                                              │  │
│  │  B. App users (JPA):                                         │  │
│  │       AppUserRepository extends JpaRepository                │  │
│  │       ↓                                                      │  │
│  │       app_user + app_user_role                               │  │
│  │                                                              │  │
│  │  C. Signing key (JPA):                                       │  │
│  │       SigningKeyRepository extends JpaRepository             │  │
│  │       ↓                                                      │  │
│  │       signing_key                                            │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                    │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  Schema management                                           │  │
│  │    Flyway (jdbc profile only) → V1..V6                       │  │
│  │    Hibernate ddl-auto = validate (never mutates schema)      │  │
│  └──────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    MySQL :3309 / authdb_jdbc
```

---

## Filter chain ordering — the piece most people get wrong

Spring Security consults filter chains **in `@Order` sequence** and stops at the first one whose `securityMatcher` matches the request. So the **order of the beans decides which chain handles what.**

```
Request: GET /admin/clients
   │
   ├─ Order 0  match /admin/**  ? YES → use this chain, DONE
   │              → require ADMIN role, formLogin, CSRF
   │
   ├─ (never reached)
   └─ (never reached)

Request: POST /oauth2/token
   │
   ├─ Order 0  match /admin/**  ? no
   ├─ Order 1  matcher = OAuth2 endpoints ? YES → use this chain, DONE
   │              → client_secret_basic auth, no CSRF
   │
   └─ (never reached)

Request: GET /login
   │
   ├─ Order 0  match /admin/**  ? no
   ├─ Order 1  OAuth2 endpoints ? no
   ├─ Order 2  matcher = anyRequest ? YES → use this chain, DONE
   │              → /login is permitAll, render login page
```

**Trick that made this clean:** `securityMatcher("/admin/**")` on the Order 0 chain. Without it, an `@Order(0)` chain would try to handle every request and steal `/oauth2/token`, breaking the whole server.

---

## Why "one bean per profile" instead of "if" checks

```java
// BAD
@Bean
RegisteredClientRepository repo(Env env, JdbcTemplate jt) {
    if (env.acceptsProfiles(Profiles.of("jdbc")))
        return new JdbcRegisteredClientRepository(jt);
    return new InMemoryRegisteredClientRepository(demoClient, m2mClient);
}
```

```java
// GOOD
@Bean @Profile("inmemory")
RegisteredClientRepository inmem(...) { ... }

@Bean @Profile("jdbc")
RegisteredClientRepository jdbc(JdbcTemplate jt) { return new JdbcRegisteredClientRepository(jt); }
```

Second form is idiomatic Spring, easier to grep, and lets each factory declare *only* the dependencies it actually uses (JDBC bean doesn't need to know about hardcoded demo clients).

---

## The signing key story (why persistence matters)

```
inmemory profile                     jdbc profile
────────────────                     ─────────────
Every boot:                          First boot:
  generate 2048-bit RSA                signing_key table empty
  wrap in JWKSet                       generate 2048-bit RSA
  serve via /oauth2/jwks               persist as PEM (public + private, kid)
  kid = random UUID                    wrap in JWKSet
                                       serve via /oauth2/jwks
Restart:                             Subsequent boots:
  DIFFERENT kid                        load PEM from DB
  all previously-issued tokens        SAME kid
  become invalid (resource-server     issued tokens keep working
  can't find matching JWK)             clients don't need to re-auth
```

That's why real deployments never regenerate keys per boot — you'd log every user out on every deploy.

---

## Where the "production-shaped" gains show up

| Feature | inmemory (learning) | jdbc (prod-shaped) |
|---|---|---|
| Client survives restart | ❌ | ✔ |
| User survives restart | ❌ | ✔ |
| RSA key stable across restarts | ❌ | ✔ |
| Passwords hashed | plaintext `{noop}` | BCrypt `{bcrypt}$2a$10$...` |
| Client secrets hashed | plaintext `{noop}` | BCrypt |
| Add/edit clients at runtime | ❌ (code change) | ✔ (UI) |
| Add/edit users at runtime | ❌ (code change) | ✔ (UI) |
| Schema managed | Hibernate auto-DDL | Flyway migrations, `validate` |
| Hardcoded secrets in repo | yes | no (BCrypt only) |
| Audit table | ❌ | `client_audit` (unused yet) |
