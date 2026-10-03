package com.example.auth.api.v1.dto;

import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JSON body returned by GET / POST / PUT on /api/v1/admin/clients.
 *
 * <p>Built via {@link #from(RegisteredClient)} — flattens Spring's builder-style
 * object into a plain record. Note what's NOT here: the plaintext secret.
 * BCrypt hashes are fine to return (you can't reverse them), but plaintext
 * secrets are only shown ONCE at create time and never again.
 */
public record ClientResponse(
        String id,
        String clientId,
        String clientName,
        Instant clientIdIssuedAt,
        Set<String> authMethods,
        Set<String> grantTypes,
        Set<String> scopes,
        Set<String> redirectUris,
        Set<String> postLogoutRedirectUris,
        boolean requireProofKey,
        boolean requireAuthConsent,
        long accessTokenTtlMin,
        long refreshTokenTtlMin,
        boolean reuseRefreshTokens
) {
    public static ClientResponse from(RegisteredClient rc) {
        return new ClientResponse(
                rc.getId(),
                rc.getClientId(),
                rc.getClientName(),
                rc.getClientIdIssuedAt(),
                rc.getClientAuthenticationMethods().stream()
                        .map(ClientAuthenticationMethod::getValue).collect(Collectors.toSet()),
                rc.getAuthorizationGrantTypes().stream()
                        .map(AuthorizationGrantType::getValue).collect(Collectors.toSet()),
                rc.getScopes(),
                rc.getRedirectUris(),
                rc.getPostLogoutRedirectUris(),
                rc.getClientSettings().isRequireProofKey(),
                rc.getClientSettings().isRequireAuthorizationConsent(),
                rc.getTokenSettings().getAccessTokenTimeToLive().toMinutes(),
                rc.getTokenSettings().getRefreshTokenTimeToLive().toMinutes(),
                rc.getTokenSettings().isReuseRefreshTokens()
        );
    }
}
