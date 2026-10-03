package com.example.auth.api.v1.dto;

import com.example.auth.dto.ClientForm;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Set;

/**
 * JSON body for {@code POST /api/v1/admin/clients} + {@code PUT .../clients/{id}}.
 *
 * <p>Records-flavoured mirror of the browser form ({@link ClientForm}). We keep
 * them separate so JSON field naming can differ from HTML input names, and so
 * validation rules on records can evolve independently.
 *
 * <p>{@link #toForm()} converts to the browser DTO so we can reuse the same
 * {@code ClientAdminService.save()} path — no duplicated business logic.
 *
 * <p>Convention (same as browser): on UPDATE, blank {@code clientSecret} means
 * "keep the existing hash".
 */
public record ClientRequest(
        @NotBlank String clientId,
        @NotBlank String clientName,
        String clientSecret,          // plaintext on create; null/blank on update = keep
        Set<String> authMethods,
        Set<String> grantTypes,
        Set<String> scopes,
        List<String> redirectUris,
        List<String> postLogoutRedirectUris,
        boolean requireProofKey,
        boolean requireAuthConsent,
        Integer accessTokenTtlMin,    // null → default 5
        Integer refreshTokenTtlMin,   // null → default 60
        Boolean rotateRefreshTokens
) {
    /** Convert to the browser DTO the service already speaks. */
    public ClientForm toForm() {
        ClientForm f = new ClientForm();
        f.setClientId(clientId);
        f.setClientName(clientName);
        f.setClientSecret(clientSecret);
        f.setAuthMethods(authMethods == null ? Set.of() : authMethods);
        f.setGrantTypes(grantTypes == null ? Set.of() : grantTypes);
        f.setScopes(scopes == null ? Set.of() : scopes);
        f.setRedirectUris(redirectUris == null ? "" : String.join("\n", redirectUris));
        f.setPostLogoutRedirectUris(postLogoutRedirectUris == null ? "" : String.join("\n", postLogoutRedirectUris));
        f.setRequireProofKey(requireProofKey);
        f.setRequireAuthConsent(requireAuthConsent);
        f.setAccessTokenTtlMin(accessTokenTtlMin != null ? accessTokenTtlMin : 5);
        f.setRefreshTokenTtlMin(refreshTokenTtlMin != null ? refreshTokenTtlMin : 60);
        f.setRotateRefreshTokens(rotateRefreshTokens);
        return f;
    }
}
