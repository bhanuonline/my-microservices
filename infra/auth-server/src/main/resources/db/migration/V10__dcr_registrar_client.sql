-- Feature 12: Dynamic Client Registration (RFC 7591) bootstrap.
--
-- Seeds the "registrar" client — the bootstrap OAuth client that ops/CI/Terraform
-- use to acquire access tokens for calling /connect/register.
--
--   client_id:      registrar
--   client_secret:  registrar-secret   (BCrypt-hashed)
--   grant:          client_credentials
--   scopes:         client.create      → allowed to POST /connect/register
--                   client.read        → allowed to GET  /connect/register/{id}
--
-- Standard OAuth flow to onboard a NEW client:
--   1. curl -u registrar:registrar-secret \
--          -d grant_type=client_credentials -d scope=client.create \
--          /oauth2/token   →   { access_token: eyJ... }
--   2. curl -H "Authorization: Bearer eyJ..." \
--          -H "Content-Type: application/json" \
--          -d '{"client_name":"my-app","redirect_uris":[...],"grant_types":[...]}' \
--          /connect/register   →   201 { client_id, client_secret, ... }

INSERT INTO oauth2_registered_client (
    id, client_id, client_id_issued_at, client_secret, client_secret_expires_at,
    client_name, client_authentication_methods, authorization_grant_types,
    redirect_uris, post_logout_redirect_uris, scopes,
    client_settings, token_settings
) VALUES (
    'registrar-uuid-000000000000000003',
    'registrar',
    CURRENT_TIMESTAMP,
    '{bcrypt}$2a$10$FnHSOHThxg8c5P6lXkS4U.0vZeBxhimlyh.uYqrSXjkIzQa9.erAC',
    NULL,
    'DCR Bootstrap Client',
    'client_secret_basic',
    'client_credentials',
    NULL,
    NULL,
    'client.create,client.read',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","RS256"],"settings.token.access-token-time-to-live":["java.time.Duration",300.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",3600.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}'
);
