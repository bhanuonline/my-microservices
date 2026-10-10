package com.example.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The HTML form's shape, in Java. Every field maps to an input on the
 * new-client / edit-client page.
 *
 * <p>Why this exists (instead of using Spring's {@link
 * org.springframework.security.oauth2.server.authorization.client.RegisteredClient}
 * directly):
 * <ul>
 *   <li>{@code RegisteredClient} is an immutable builder with strong types
 *       — {@code Duration} for TTLs, {@code ClientAuthenticationMethod} for
 *       auth methods, etc. HTML forms send flat strings.</li>
 *   <li>Bean Validation ({@code @NotBlank}, etc.) needs a mutable POJO.</li>
 *   <li>Some fields on the form don't exist on RegisteredClient
 *       (e.g. {@code rotateRefreshTokens} — that lives inside
 *       {@code TokenSettings.reuseRefreshTokens}).</li>
 * </ul>
 *
 * <p>{@code ClientAdminService} does the translation on save; {@code toForm()}
 * does the reverse for edit forms.
 *
 * <p>Convention: on UPDATE, {@code clientSecret} blank = "keep existing hash",
 * non-blank = "replace with this new plaintext (BCrypt it before saving)".
 */
@Data
public class ClientForm {

    /** Persistence id (null on create, present on edit). */
    private String id;

    @NotBlank
    private String clientId;

    /** Plain-text on the form; BCrypt-hashed before storage. Blank on edit = unchanged. */
    private String clientSecret;

    @NotBlank
    private String clientName;

    /** e.g. CLIENT_SECRET_BASIC, CLIENT_SECRET_POST, NONE */
    private Set<String> authMethods = new LinkedHashSet<>();

    /** e.g. authorization_code, refresh_token, client_credentials */
    private Set<String> grantTypes = new LinkedHashSet<>();

    /** Free-form scope names. */
    private Set<String> scopes = new LinkedHashSet<>();

    /** One per line in the textarea. */
    private String redirectUris;

    private String postLogoutRedirectUris;

    private boolean requireProofKey;
    private boolean requireAuthConsent;

    /** Minutes. */
    private int accessTokenTtlMin = 5;
    private int refreshTokenTtlMin = 60;

    /**
     * FEATURE: refresh token rotation.
     * When true → TokenSettings.reuseRefreshTokens(false) (each refresh issues a new refresh token).
     * When null → feature-disabled path; service leaves TokenSettings.reuseRefreshTokens untouched.
     * Boolean (not boolean) so we can distinguish "unset" from "explicit false".
     */
    private Boolean rotateRefreshTokens;

    /** CSV-friendly setter for the scopes textbox on the form (name="scopesCsv"). */
    public void setScopesCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            this.scopes = new LinkedHashSet<>();
            return;
        }
        this.scopes = Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
