# OAuth2 + JWT auth — the whole flow

## What OAuth2 actually is

A framework for **delegated authorization**. Instead of every app storing your password, one central "authorization server" issues short-lived tokens that other apps trust.

```
   Old world:                           OAuth2 world:
   ─────────────                        ─────────────
   Every app has its own login          One IdP (identity provider) owns login
   Password stored in 5 databases       Password stored once, in the IdP
   Change password → change 5 places    Change password → done
   Compromise 1 app → compromise all    Compromise 1 app → limited blast radius
```

## The players

| Term | What it means | In THIS project |
|---|---|---|
| **Resource Owner** | The user | You / your test user |
| **Client** | The app that wants to access the API | Postman / your web app / your mobile app |
| **Authorization Server** (AS) | Issues tokens after auth | `auth-server` (:8095) |
| **Resource Server** (RS) | Protected API that validates tokens | `user-service`, `product-service`, `order-service`, `api-gateway` |
| **Access Token** | Short-lived credential (usually a JWT) | Returned by `POST /oauth2/token` |

## JWT — what's inside the token

A JWT is 3 base64-encoded parts joined by dots: `header.payload.signature`.

```
   eyJhbGciOiJSUzI1NiIsImtpZCI6IjJlN2IxNzAxIn0
   .
   eyJzdWIiOiJkZW1vLWNsaWVudCIsImlzcyI6Imh0dHA6Ly9sb2NhbGhvc3Q6ODA5NSIsImV4cCI6MTcyNzEwMDIzNH0
   .
   <signature>
```

Decoded payload (JSON):
```json
{
  "sub": "demo-client",              // WHO the token is for
  "iss": "http://localhost:8095",    // WHO issued it
  "exp": 1727100234,                 // WHEN it expires (Unix time)
  "iat": 1727099934,                 // WHEN it was issued
  "scope": "read",                   // WHAT it can do
  "aud": "resource-server"           // WHERE it's meant to be used
}
```

Paste any JWT into [jwt.io](https://jwt.io) to inspect it.

**Key insight:** the payload is NOT encrypted. It's readable by anyone. What makes it trustworthy is the **signature** — signed with auth-server's private key, verified with its public key.

## The 4 main grant types (flows)

Different callers need different flows. Same JWT format; different way to get it.

```
   ┌──────────────────────────────────────────────────────────────────┐
   │                                                                    │
   │  1. Authorization Code (+ PKCE)                                   │
   │     Caller: Web / mobile app with a real user                      │
   │     Flow:   Browser redirect → user logs in → code → JWT          │
   │     JWT has: user identity (sub=alice)                            │
   │                                                                    │
   │  2. Client Credentials                                            │
   │     Caller: Machine-to-machine (backend, scheduled job)            │
   │     Flow:   POST /oauth2/token with client_id + secret            │
   │     JWT has: client identity (sub=demo-client), NO user           │
   │                                                                    │
   │  3. Refresh Token                                                 │
   │     Caller: Any client after their access token expired            │
   │     Flow:   POST /oauth2/token with refresh_token to get new JWT  │
   │     Point:  don't force user to re-login every 5 minutes          │
   │                                                                    │
   │  4. Password (aka Resource Owner Password Credentials)            │
   │     ⚠️ DEPRECATED — don't use in new systems                       │
   │     Caller sends username + password directly to AS               │
   │     Insecure; violates delegated-authorization principle          │
   │                                                                    │
   └──────────────────────────────────────────────────────────────────┘
```

## Authorization Code vs Client Credentials — visual

```
   AUTHORIZATION CODE (real users)         CLIENT CREDENTIALS (M2M)
   ────────────────────────────            ───────────────────────

   [Browser]                                [Postman]
      │                                        │
      │ 1. GET /oauth2/authorize?              │ 1. POST /oauth2/token
      │    response_type=code&                 │    Basic Auth: id:secret
      │    client_id=demo&                     │    body: grant_type=client_credentials
      │    redirect_uri=...                    ▼
      ▼                                     [Auth-server]
   [Auth-server login page]                    │
      │ 2. user enters creds                   │ 2. verify client_id+secret
      │ 3. AS redirects browser back           │ 3. sign a JWT
      │    with ?code=abc123                   │ 4. return JWT
      ▼                                        ▼
   [Client app catches redirect]           [Postman has JWT ✓]
      │                                        │
      │ 4. POST /oauth2/token                  │ 5. call API
      │    grant_type=authorization_code       │    Authorization: Bearer <jwt>
      │    code=abc123                         ▼
      │    client_secret=...                [Resource-server]
      ▼                                        │
   [Auth-server]                               │ 6. verify JWT signature
      │ 5. verify code                         │    (using AS's JWKS)
      │ 6. return JWT                          │ 7. allow request
      ▼
   [Client app has JWT ✓]
      │
      │ 7. call API with Bearer
      ▼
   [Resource-server]
```

## How verification works (JWKS)

The trick that makes JWT stateless — resource-servers verify tokens WITHOUT calling auth-server per request.

```
   On startup, EVERY resource-server:
     GET http://<auth-server>/.well-known/jwks.json
     → gets auth-server's public key(s)
     → caches them locally

   On EVERY incoming request with a JWT:
     1. Split JWT: header.payload.signature
     2. Look up which key signed it (by 'kid' in header)
     3. Verify signature using cached public key
        (no network call!)
     4. If valid + not expired → allow
     5. If invalid → 401
```

**Consequence:** auth-server can be DOWN and resource-servers still validate tokens (until JWKS cache expires). Great for availability.

**Also:** auth-server rotates its keys → resource-servers refetch JWKS → old tokens become invalid. Poor man's revocation.

## How it's wired in THIS project

### Auth-server side

**File:** `auth-server/.../config/SecurityConfig.java`

- Registers ONE client: `demo-client` / `secret`, `authorization_code` grant
- Issues JWTs signed with an RSA key pair (generated in-memory on startup)
- Exposes JWKS at `http://localhost:8095/oauth2/jwks`
- Serves discovery metadata at `http://localhost:8095/.well-known/openid-configuration`

**Config:**
- `spring-security-oauth2-authorization-server` dep provides the whole framework
- `AuthorizationServerSettings.builder().issuer("http://localhost:8095").build()`

### Resource-server side (user/product/order/gateway)

**Files:** `<service>/.../config/SecurityConfig.java` — same in every service:

```java
http
  .authorizeHttpRequests(auth -> auth
      .requestMatchers("/actuator/**").permitAll()   // health etc public
      .anyRequest().authenticated()                   // everything else needs JWT
  )
  .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
```

**Config:** all services have:
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8095
```

That ONE line tells Spring: "on startup, fetch JWKS from this URL. Then verify every JWT's signature and issuer claim against it."

### Gateway's extra role

Gateway does JWT validation AND uses `TokenRelay` to forward the `Authorization` header downstream. So when you hit `:8080/api/v1/products` with a JWT:
- Gateway validates it
- Gateway adds it to the outgoing request to product-service
- Product-service validates it AGAIN (zero-trust)

## Grant type currently enabled

Right now `demo-client` only supports `authorization_code`. That's fine for a real web app but painful for testing.

**To enable `client_credentials` for testing** — add to `SecurityConfig.registeredClientRepository()`:

```java
.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
.scope("read")
.scope("write")
```

Or register a SECOND client just for testing. The [Postman testing guide](#postman-testing-with-client-credentials) below assumes this is done.

## Postman testing with client_credentials

Once auth-server supports `client_credentials`:

### Step 1 — get a token

```bash
curl -X POST http://localhost:8095/oauth2/token \
  -u "demo-client:secret" \
  -d "grant_type=client_credentials"
```

Response:
```json
{
  "access_token": "eyJhbGc...",
  "token_type": "Bearer",
  "expires_in": 300,
  "scope": "read"
}
```

### Step 2 — use the token

```bash
JWT="eyJhbGc..."   # paste from step 1
curl -H "Authorization: Bearer $JWT" http://localhost:8080/api/v1/products
```

### Step 3 — automate in Postman

In the "Token — client_credentials" request:
1. **Tests** tab → paste:
   ```javascript
   const json = pm.response.json();
   pm.collectionVariables.set("jwt", json.access_token);
   console.log("JWT stored in collection variable");
   ```
2. Every request in the collection can now use `Authorization: Bearer {{jwt}}`
3. When token expires (~5 min), re-run the token request to refresh

## Interview talking points

- **Stateless JWT vs opaque token + introspection:** JWT scales better (no per-request AS call); opaque revokes cleanly. Trade-off.
- **JWKS caching + key rotation:** how resource-servers verify without calling AS every time.
- **`iss` claim** — critical. Every service in the fleet must agree on `issuer-uri`.
- **Grant types:** authorization_code for users, client_credentials for M2M, refresh for long sessions. Password grant is deprecated.
- **PKCE (Proof Key for Code Exchange)** — extends authorization_code to prevent code-interception attacks in public clients (mobile apps, SPAs).
- **Token expiry:** short-lived (5-15 min access token) + long-lived refresh token. Balance security vs UX.
- **Scopes vs roles:** scopes describe what the client is authorized for (`read`, `write`); roles describe what the USER can do. Some systems conflate them.
- **TokenRelay filter (Spring Cloud Gateway):** forwards the incoming JWT to downstream services. Enables "single sign-on across microservices."
- **Zero-trust:** every service validates JWT independently, not just the gateway. Prevents lateral movement if a service is bypassed.

## Common failure modes

| Symptom | Cause | Fix |
|---|---|---|
| 401 with valid-looking JWT | `iss` claim doesn't match service's `issuer-uri` | Check both sides EXACTLY |
| 401 immediately after login | Clock skew between auth-server and resource-server (JWT `exp` looks expired) | Sync clocks via NTP |
| Token accepted by gateway, rejected by user-service | user-service has different `issuer-uri` config | Standardize |
| JWKS 404 on service startup | auth-server not up yet | Start auth-server BEFORE downstream |
| `unauthorized_client` on token endpoint | Client doesn't have requested grant type registered | Add grant to client config |

## TL;DR

> Auth-server issues JWTs. Every service validates them offline using auth-server's public key. Same JWT trusted by every service (they all agree on the issuer). For testing, use `client_credentials` grant — one HTTP call gets you a token, use it as `Authorization: Bearer <token>`.
