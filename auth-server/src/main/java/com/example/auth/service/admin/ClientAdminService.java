package com.example.auth.service.admin;

import com.example.auth.config.FeatureFlags;
import com.example.auth.dto.ClientForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.*;

/**
 * Translates between what the admin form gives us ({@link ClientForm}) and what
 * Spring Auth Server needs ({@link RegisteredClient}), plus does the DB work.
 *
 * <p>Why this class exists:
 * Spring's {@code RegisteredClient} is an immutable builder-style object with
 * strong types (Duration for TTLs, ClientAuthenticationMethod for auth
 * methods, Set&lt;String&gt; for scopes). The HTML form gives us flat strings +
 * checkboxes. This service is the translator so controllers stay thin.
 *
 * <p>Also handles:
 * <ul>
 *   <li><b>Secret hashing.</b> Plaintext arrives on create; we BCrypt-encode
 *       before saving. On update, blank secret means "keep the existing hash".</li>
 *   <li><b>PKCE enforcement (Feature 2).</b> {@link #validatePkce(ClientForm)}
 *       returns an error if the caller tries to save a public client without
 *       PKCE. Called by the controller AND enforced defensively inside
 *       {@link #save(ClientForm)}.</li>
 *   <li><b>Refresh token rotation (Feature 1).</b> If the feature flag is on,
 *       reads the form's {@code rotateRefreshTokens} bool and writes
 *       {@code TokenSettings.reuseRefreshTokens(!rotate)}.</li>
 *   <li><b>Audit logging (Feature 4).</b> Every save/delete calls
 *       {@link AuditService#recordClient}. Runs in the same transaction as the
 *       DB write — if either fails, both roll back.</li>
 * </ul>
 *
 * <p>Also called by the REST API mirror ({@code ClientRestController}). Since
 * both browser and REST call this same service, audit rows get the correct
 * actor automatically from {@code SecurityContextHolder} — browser sessions
 * show {@code actor=admin}, JWT bearer shows {@code actor=api-admin}.
 */
@Service
@Profile("jdbc")
public class ClientAdminService {

    private static final Logger log = LoggerFactory.getLogger(ClientAdminService.class);

    private final RegisteredClientRepository repo;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final FeatureFlags flags;
    private final AuditService audit;

    public ClientAdminService(RegisteredClientRepository repo,
                              JdbcTemplate jdbc,
                              PasswordEncoder passwordEncoder,
                              FeatureFlags flags,
                              AuditService audit) {
        this.repo = repo;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.flags = flags;
        this.audit = audit;
    }

    /** JdbcRegisteredClientRepository has no findAll — go direct. */
    public List<Map<String, Object>> listAll() {
        return jdbc.queryForList(
            "SELECT id, client_id, client_name, authorization_grant_types, scopes " +
            "FROM oauth2_registered_client ORDER BY client_id"
        );
    }

    public RegisteredClient findById(String id) {
        return repo.findById(id);
    }

    /** Used by REST API to look up a client by its human-readable clientId. */
    public RegisteredClient findByClientId(String clientId) {
        return repo.findByClientId(clientId);
    }

    public ClientForm toForm(RegisteredClient rc) {
        ClientForm f = new ClientForm();
        f.setId(rc.getId());
        f.setClientId(rc.getClientId());
        f.setClientName(rc.getClientName());
        f.setAuthMethods(new LinkedHashSet<>(map(rc.getClientAuthenticationMethods(),
                ClientAuthenticationMethod::getValue)));
        f.setGrantTypes(new LinkedHashSet<>(map(rc.getAuthorizationGrantTypes(),
                AuthorizationGrantType::getValue)));
        f.setScopes(new LinkedHashSet<>(rc.getScopes()));
        f.setRedirectUris(String.join("\n", rc.getRedirectUris()));
        f.setPostLogoutRedirectUris(String.join("\n", rc.getPostLogoutRedirectUris()));

        ClientSettings cs = rc.getClientSettings();
        f.setRequireProofKey(cs.isRequireProofKey());
        f.setRequireAuthConsent(cs.isRequireAuthorizationConsent());

        TokenSettings ts = rc.getTokenSettings();
        f.setAccessTokenTtlMin((int) ts.getAccessTokenTimeToLive().toMinutes());
        f.setRefreshTokenTtlMin((int) ts.getRefreshTokenTimeToLive().toMinutes());

        // FEATURE: refresh token rotation
        // Only surface the current value when the feature is enabled; otherwise leave null
        // so the form doesn't tempt admins to set something the feature will ignore.
        if (flags.getRefreshTokenRotation().isEnabled()) {
            f.setRotateRefreshTokens(!ts.isReuseRefreshTokens());
        }
        return f;
    }

    /**
     * FEATURE: PKCE enforcement.
     * Returns a validation-error key if the form violates the "public clients must have PKCE"
     * rule and the feature is on; empty otherwise. Controller maps this to a BindingResult error.
     */
    public java.util.Optional<String> validatePkce(ClientForm form) {
        if (!flags.getPkce().isEnforceForPublicClients()) {
            return java.util.Optional.empty();
        }
        boolean isPublic = form.getAuthMethods() != null
                && form.getAuthMethods().contains("none");
        if (isPublic && !form.isRequireProofKey()) {
            return java.util.Optional.of(
                "Public clients (authentication method = none) must have Require PKCE enabled.");
        }
        return java.util.Optional.empty();
    }

    /** Create or update. On update, blank secret keeps the existing hash. */
    public void save(ClientForm form) {
        // Defense-in-depth: even if a controller forgets to call validatePkce,
        // the service refuses to persist an insecure public client.
        validatePkce(form).ifPresent(msg -> { throw new IllegalArgumentException(msg); });

        boolean isNew = !StringUtils.hasText(form.getId());
        String id = isNew ? UUID.randomUUID().toString() : form.getId();

        RegisteredClient.Builder b = RegisteredClient.withId(id)
                .clientId(form.getClientId())
                .clientName(form.getClientName());

        // ---- secret handling ----
        if (isNew) {
            require(form.getClientSecret(), "clientSecret required on create");
            b.clientSecret(passwordEncoder.encode(form.getClientSecret()));
        } else if (StringUtils.hasText(form.getClientSecret())) {
            b.clientSecret(passwordEncoder.encode(form.getClientSecret()));
        } else {
            // preserve existing hash
            RegisteredClient existing = repo.findById(id);
            require(existing, "client with id=" + id + " not found");
            b.clientSecret(existing.getClientSecret());
        }

        for (String m : form.getAuthMethods()) {
            b.clientAuthenticationMethod(new ClientAuthenticationMethod(m));
        }
        for (String g : form.getGrantTypes()) {
            b.authorizationGrantType(new AuthorizationGrantType(g));
        }
        for (String s : form.getScopes()) {
            b.scope(s);
        }
        for (String uri : splitLines(form.getRedirectUris())) {
            b.redirectUri(uri);
        }
        for (String uri : splitLines(form.getPostLogoutRedirectUris())) {
            b.postLogoutRedirectUri(uri);
        }

        b.clientSettings(ClientSettings.builder()
                .requireProofKey(form.isRequireProofKey())
                .requireAuthorizationConsent(form.isRequireAuthConsent())
                .build());

        TokenSettings.Builder tsBuilder = TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofMinutes(form.getAccessTokenTtlMin()))
                .refreshTokenTimeToLive(Duration.ofMinutes(form.getRefreshTokenTtlMin()));

        // FEATURE: refresh token rotation
        //   flag off → leave Spring's default (reuse=true) alone
        //   flag on  → form's value if set, else feature's default-for-new
        if (flags.getRefreshTokenRotation().isEnabled()) {
            boolean rotate = form.getRotateRefreshTokens() != null
                    ? form.getRotateRefreshTokens()
                    : flags.getRefreshTokenRotation().isDefaultForNew();
            tsBuilder.reuseRefreshTokens(!rotate);
            log.debug("Refresh token rotation for {}: rotate={} (reuse={})",
                    form.getClientId(), rotate, !rotate);
        }

        b.tokenSettings(tsBuilder.build());

        RegisteredClient rc = b.build();
        repo.save(rc);
        log.info("Saved client id={} clientId={} (isNew={})", id, form.getClientId(), isNew);

        // Audit — snapshot the form (plaintext secret stripped so it never lands in DB audit).
        audit.recordClient(isNew ? "CREATE" : "UPDATE",
                form.getClientId(), snapshotForAudit(form));
    }

    /** JdbcRegisteredClientRepository has no delete — go direct. */
    public void deleteById(String id) {
        // Capture clientId BEFORE delete for audit — post-delete lookup is impossible.
        RegisteredClient existing = repo.findById(id);
        String clientId = existing != null ? existing.getClientId() : id;

        int n = jdbc.update("DELETE FROM oauth2_registered_client WHERE id = ?", id);
        log.info("Deleted client id={} (rows={})", id, n);

        audit.recordClient("DELETE", clientId, java.util.Map.of("id", id));
    }

    /** Strip secret before we hand the form to the audit serializer. */
    private static java.util.Map<String, Object> snapshotForAudit(ClientForm f) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("clientId", f.getClientId());
        m.put("clientName", f.getClientName());
        m.put("authMethods", f.getAuthMethods());
        m.put("grantTypes", f.getGrantTypes());
        m.put("scopes", f.getScopes());
        m.put("redirectUris", f.getRedirectUris());
        m.put("postLogoutRedirectUris", f.getPostLogoutRedirectUris());
        m.put("requireProofKey", f.isRequireProofKey());
        m.put("requireAuthConsent", f.isRequireAuthConsent());
        m.put("accessTokenTtlMin", f.getAccessTokenTtlMin());
        m.put("refreshTokenTtlMin", f.getRefreshTokenTtlMin());
        m.put("rotateRefreshTokens", f.getRotateRefreshTokens());
        // Deliberately omit clientSecret — never in audit trail.
        return m;
    }

    // ---- helpers ----
    private static <T, R> List<R> map(Collection<T> in, java.util.function.Function<T, R> fn) {
        return in.stream().map(fn).toList();
    }

    private static List<String> splitLines(String s) {
        if (!StringUtils.hasText(s)) return List.of();
        return Arrays.stream(s.split("\\R"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private static void require(Object v, String msg) {
        if (v == null || (v instanceof String s && !StringUtils.hasText(s))) {
            throw new IllegalArgumentException(msg);
        }
    }
}
