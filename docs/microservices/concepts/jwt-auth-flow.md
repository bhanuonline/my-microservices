# JWT auth flow — own auth-server + resource-server pattern

## The problem it solves

- One identity provider issues tokens; every service verifies them offline (no DB call per request).
- Stateless: pods can scale horizontally without shared session state.

## How it works

```
<!-- ASCII: client → auth-server (get JWT) → gateway (validate) → downstream (validate again) -->
```

## How it's wired in THIS project

- **Issuer:** `auth-server` (:8095), Spring Authorization Server
- **Validators:** `api-gateway`, `resource-server`, `user-service`, `product-service`, `order-service`
- **Single source of truth:** every service's config has:
  ```
  spring.security.oauth2.resourceserver.jwt.issuer-uri: http://localhost:8095
  ```
- **Public key fetch:** each service fetches `<issuer-uri>/.well-known/jwks.json` on startup and caches.
- **JWT relay:** gateway's `TokenRelay` filter forwards the `Authorization` header to downstream.

## Zero-trust vs edge-trust

We use **zero-trust**: every service validates the JWT independently. Even if someone bypassed the gateway (e.g. hits user-service directly at :8081), they still need a valid JWT.

## How to observe it running

```bash
# JWKS available
curl -s http://localhost:8095/.well-known/openid-configuration | jq
curl -s http://localhost:8095/oauth2/jwks | jq

# Hit a protected endpoint without JWT → 401
curl -i http://localhost:8080/api/v1/users

# Get a token (client_credentials flow)
# TODO: register a client in auth-server first, then:
# curl -X POST http://localhost:8095/oauth2/token \
#   -u "client-id:client-secret" \
#   -d "grant_type=client_credentials"
```

## Common failure modes

- **Issuer mismatch** — auth-server's `iss` claim doesn't match downstream's `issuer-uri`. Everything fails.
- **Clock skew** — token `exp` is in the past due to container clock drift.
- **JWKS not cached / re-fetched** — auth-server key rotation confuses old caches.

## Interview talking points

- OAuth2 grant types: `client_credentials`, `authorization_code` (with PKCE), `refresh_token`.
- Stateless JWT vs opaque token + introspection tradeoff.
- Public/private key (RSA/ECDSA) vs shared secret (HMAC) signing.
- Token revocation problem (JWTs are stateless — can't easily revoke). Solutions: short expiry + refresh, blacklist cache.
- Own IdP vs managed (Keycloak, Auth0, Okta).
