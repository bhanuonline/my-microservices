# Auth-Server Admin UI — study docs

What we built in this project: a production-shaped OAuth2 Authorization Server with a Thymeleaf/Bootstrap admin UI for managing clients and users — layered on top of the original in-memory learning setup **without deleting any of it** (Spring profile switch).

## Read in this order

| # | Doc | Time | What you'll learn |
|---|---|---|---|
| 1 | [00-architecture.md](00-architecture.md) | 10 min | Big picture — profiles, layers, filter chains, filter-chain ordering |
| 2 | [01-build-journey.md](01-build-journey.md) | 15 min | Phase-by-phase what was added, why, how it plugs in |
| 3 | [02-end-to-end-flow.md](02-end-to-end-flow.md) | 10 min | A single login → dashboard → create client request, filter-by-filter |
| 4 | [03-key-concepts.md](03-key-concepts.md) | 15 min | Every non-trivial thing you'd need to defend in an interview |
| 5 | [04-interview-cheatsheet.md](04-interview-cheatsheet.md) | 5 min | Rapid-fire Q&A + gotchas |
| 6 | [postman/README.md](postman/README.md) | 5 min | Import + run the Postman collection (25 requests) |
| 7 | [05-features.md](05-features.md) | evolving | Features built on top + feature-flag conventions |

## TL;DR — what exists on disk

```
auth-server/
├── src/main/java/com/example/auth/
│   ├── AuthServerApplication.java
│   ├── config/
│   │   ├── SecurityConfig.java            ← existing (Order 1 + 2 chains, inmemory beans)
│   │   └── JdbcSecurityConfig.java        ← NEW (@Profile("jdbc") twin beans + Order 0 admin chain)
│   ├── controller/
│   │   ├── ClientController.java          ← "/" redirect only
│   │   ├── UserController.java
│   │   ├── ApiController.java
│   │   ├── DemoController.java
│   │   └── admin/                          ← NEW
│   │       ├── AdminHomeController.java
│   │       ├── AdminClientController.java
│   │       └── AdminUserController.java
│   ├── service/admin/                      ← NEW
│   │   ├── ClientAdminService.java
│   │   └── UserAdminService.java
│   ├── dto/                                ← NEW
│   │   ├── ClientForm.java
│   │   └── UserForm.java
│   ├── entity/
│   │   ├── AppUser.java                    ← fixed: real @Entity + Set<String> roles
│   │   └── SigningKeyEntity.java           ← NEW
│   ├── repository/                         ← NEW
│   │   ├── AppUserRepository.java
│   │   └── SigningKeyRepository.java
│   ├── user/
│   │   └── CustomUserDetails.java          ← updated to map roles collection
│   ├── log/AuthEventLogger.java
│   └── fiter/RequestLoggingFilter.java
└── src/main/resources/
    ├── application.properties              ← default profile=inmemory
    ├── application-inmemory.properties     ← Flyway OFF
    ├── application-jdbc.properties         ← Flyway ON, separate DB
    ├── db/migration/                       ← NEW
    │   ├── V1__spring_authz_schema.sql
    │   ├── V2__app_user.sql
    │   ├── V3__app_user_role.sql
    │   ├── V4__signing_key.sql
    │   ├── V5__client_audit.sql
    │   └── V6__seed.sql
    └── templates/admin/                    ← NEW (Thymeleaf + Bootstrap)
        ├── layout.html
        ├── dashboard.html
        ├── clients/list.html
        ├── clients/form.html
        ├── users/list.html
        └── users/form.html
```

## Boot cheat sheet

```bash
# Learning path (unchanged, in-memory)
mvn -pl auth-server spring-boot:run

# Production-shaped (JDBC + Admin UI)
mvn -pl auth-server spring-boot:run -Dspring-boot.run.profiles=jdbc
```

Browser: **http://localhost:8095/admin** — login `admin / password`.
