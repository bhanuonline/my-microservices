-- Feature 14: REST API mirror bootstrap.
-- Seeds the "api-admin" client used by machines (CI, Terraform, K8s operators)
-- to call /api/v1/admin/** JSON endpoints.
--
--   client_id:      api-admin
--   client_secret:  api-admin-secret   (BCrypt-hashed)
--   grant:          client_credentials
--   scopes:         admin.read   → GET  endpoints
--                   admin.write  → POST/PUT/DELETE endpoints
--                   admin.unlock → user unlock (separated for least-privilege)

INSERT INTO oauth2_registered_client (
    id, client_id, client_id_issued_at, client_secret, client_secret_expires_at,
    client_name, client_authentication_methods, authorization_grant_types,
    redirect_uris, post_logout_redirect_uris, scopes,
    client_settings, token_settings
) VALUES (
    'api-admin-uuid-000000000000000004',
    'api-admin',
    CURRENT_TIMESTAMP,
    '{bcrypt}$2a$10$1h5d3UF9Uehd9WL/o8rah.3W/29lsR7OmEOuF1iQMX2I7CRk94hqC',
    NULL,
    'REST API Admin Client',
    'client_secret_basic',
    'client_credentials',
    NULL,
    NULL,
    'admin.read,admin.write,admin.unlock',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","RS256"],"settings.token.access-token-time-to-live":["java.time.Duration",300.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",3600.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}'
);
