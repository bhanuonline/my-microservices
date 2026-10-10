-- ============================================================================
-- Seed data — matches today's in-memory beans in SecurityConfig.java so that
-- swapping profiles inmemory <-> jdbc leaves external behaviour unchanged.
-- ============================================================================

-- Admin user: username=admin, password=password (BCrypt-hashed, strength=10).
-- Login uses plaintext 'password'.
-- {bcrypt} prefix required by DelegatingPasswordEncoder (jdbc profile).
INSERT INTO app_user (username, password, email, enabled)
VALUES ('admin', '{bcrypt}$2a$10$qxhXbaQirVC/cqOCcurrNOMxp.wiJmCVzhXyXfQElllWjD6pCMyJu', 'admin@local', 1);

INSERT INTO app_user_role (user_id, role)
VALUES ((SELECT id FROM app_user WHERE username = 'admin'), 'ADMIN');


-- ----------------------------------------------------------------------------
-- OAuth clients — mirror the two RegisteredClient beans in SecurityConfig.java.
--
-- The client_settings / token_settings columns hold Jackson-serialized JSON
-- produced by Spring's OAuth2RegisteredClientMixin. The @class hints ARE
-- required — JdbcRegisteredClientRepository uses polymorphic deserialization.
-- If you want to change these values from the Admin UI later, rebuild via
-- RegisteredClient.Builder + repo.save() and let Spring re-serialize.
--
-- Client secrets are stored as BCrypt hashes with the {bcrypt} algorithm
-- prefix so DelegatingPasswordEncoder knows which encoder to use.
--   demo-client secret plaintext:  secret
--   m2m-client  secret plaintext:  m2m-secret
-- ----------------------------------------------------------------------------

INSERT INTO oauth2_registered_client (
    id, client_id, client_id_issued_at, client_secret, client_secret_expires_at,
    client_name, client_authentication_methods, authorization_grant_types,
    redirect_uris, post_logout_redirect_uris, scopes,
    client_settings, token_settings
) VALUES (
    'demo-client-uuid-0000000000000001',
    'demo-client',
    CURRENT_TIMESTAMP,
    '{bcrypt}$2a$10$qOqbnsfyXe4RzyLFC5AhqOcN6tsq0nDyPL5bNvnzSD852Ce5Ve/He',
    NULL,
    'demo-client',
    'client_secret_basic',
    'authorization_code,refresh_token',
    'http://127.0.0.1:8097/login/oauth2/code/demo-client',
    'http://127.0.0.1:8097/',
    'openid,profile,read',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","RS256"],"settings.token.access-token-time-to-live":["java.time.Duration",300.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",3600.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}'
);

INSERT INTO oauth2_registered_client (
    id, client_id, client_id_issued_at, client_secret, client_secret_expires_at,
    client_name, client_authentication_methods, authorization_grant_types,
    redirect_uris, post_logout_redirect_uris, scopes,
    client_settings, token_settings
) VALUES (
    'm2m-client-uuid-00000000000000002',
    'm2m-client',
    CURRENT_TIMESTAMP,
    '{bcrypt}$2a$10$fIeyi.UAGAwuFcbOO2DFVOXc.KS3i4n9x71h9KRHnpmOVABIY3SPe',
    NULL,
    'm2m-client',
    'client_secret_basic',
    'client_credentials',
    NULL,
    NULL,
    'read,write',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","RS256"],"settings.token.access-token-time-to-live":["java.time.Duration",300.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",3600.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}'
);
