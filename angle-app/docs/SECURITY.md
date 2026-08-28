# Security

## Spring Security

Custom `SecurityConfig` in `config/SecurityConfig.java`. Default auto-config is
excluded on `AngleAppApplication` — everything is wired manually.

### Toggling security off (`nosec` profile)

`SecurityConfig` is annotated `@Profile("!nosec")`. When you run the app with
the `nosec` profile active, the entire security config is skipped and the app
runs with no authentication.

**Enable no-security mode:**
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=nosec
```

Or via env var:
```bash
export SPRING_PROFILES_ACTIVE=nosec
./mvnw spring-boot:run
```

**Turn security back on:**
Just run without the profile:
```bash
./mvnw spring-boot:run
```

Servlet filters (like `TraditionalFilter`) live in `FilterConfig.java` — kept
separate so they still run when security is disabled.

> ⚠️ Never set `nosec` in production. Only for local dev / learning.

## Authentication

- `AuthController` — login endpoint
- `RegistrationController` — new user signup
- Filter chain in `filter/` package
- User model + service layer under `service/`

## Authorization

Role-based:
- `USER` — regular access (dashboard, own data)
- `ADMIN` — admin-only endpoints in `AdminController`

## Secrets Management

**No secrets in source or `application.properties`.**

All broker credentials use env var placeholders:

```properties
broker.angel.api-key=${ANGEL_API_KEY:}
```

Set env vars before starting the app (see [ENV-VARIABLES.md](ENV-VARIABLES.md)).

## Session Security

- CSRF: verify configuration in `SecurityConfig`
- Session fixation: Spring default protection enabled
- Password hashing: BCrypt (verify in `SecurityConfig`)

## HTTPS

Not enforced by the app. Terminate TLS at a reverse proxy (nginx, ALB) in
production. For local dev, HTTP is fine.

## CORS

`config/CorsConfig.java` configures CORS. Restrict allowed origins for
production — don't leave `*`.

## Broker Tokens

- Never log JWT tokens, TOTP secrets, or passwords
- Rotate tokens periodically
- Store refresh tokens encrypted at rest if persisted

## Audit Trail

Add order/trade logging so every executed action can be traced back to a user
and timestamp. Currently not implemented — see [ROADMAP.md](ROADMAP.md).

## Reporting Issues

If you find a security bug, do not open a public issue. Contact the maintainer
directly.
