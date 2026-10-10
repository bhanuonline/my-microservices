package com.example.auth.config;

import com.example.auth.entity.SigningKeyEntity;
import com.example.auth.entity.SigningKeyEntity.Status;
import com.example.auth.repository.AppUserRepository;
import com.example.auth.repository.SigningKeyRepository;
import com.example.auth.user.CustomUserDetails;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyOperation;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.transaction.annotation.Transactional;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * JDBC-profile counterpart to {@link SecurityConfig}.
 *
 * Wires the "production-shaped" path:
 *   • Clients / authorizations / consents backed by MySQL (authdb_jdbc)
 *   • Users loaded from app_user + app_user_role
 *   • Passwords/secrets encoded via DelegatingPasswordEncoder ({bcrypt}, {noop}, …)
 *   • RSA signing key persisted in signing_key so tokens survive restarts
 *   • A dedicated /admin/** filter chain
 *
 * All beans are gated by @Profile("jdbc"). Their inmemory twins in SecurityConfig
 * are gated by @Profile("inmemory") so both paths coexist.
 */
@Configuration
@Profile("jdbc")
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class JdbcSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(JdbcSecurityConfig.class);

    // ==========================================================================
    // OAuth2 server storage — the three JDBC repositories/services Spring ships.
    // ==========================================================================

    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jt) {
        log.info("Wiring JdbcRegisteredClientRepository (reads oauth2_registered_client)");
        return new JdbcRegisteredClientRepository(jt);
    }

    @Bean
    public OAuth2AuthorizationService authorizationService(JdbcTemplate jt,
                                                            RegisteredClientRepository rc) {
        log.info("Wiring JdbcOAuth2AuthorizationService");
        return new JdbcOAuth2AuthorizationService(jt, rc);
    }

    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(JdbcTemplate jt,
                                                                          RegisteredClientRepository rc) {
        log.info("Wiring JdbcOAuth2AuthorizationConsentService");
        return new JdbcOAuth2AuthorizationConsentService(jt, rc);
    }

    // ==========================================================================
    // App users + password encoding.
    // DelegatingPasswordEncoder inspects the {prefix} on stored hashes to pick
    // the matching encoder — that's why V6 uses '{bcrypt}$2a$10$…'.
    // ==========================================================================

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(AppUserRepository users) {
        return username -> users.findByUsername(username)
                .map(CustomUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No user with username=" + username));
    }

    // ==========================================================================
    // Signing key — persisted so JWT `kid` stays stable across restarts.
    // On first-ever boot the signing_key table is empty; we generate + persist.
    // ==========================================================================

    /**
     * Multi-key JWKSource: reads from the DB on EVERY sign/verify call.
     *
     * Trade-off: 1 DB round-trip per token operation.
     *   Pro — rotation is instant, no cache invalidation, single-instance safe.
     *   Con — not great at scale. For >100 tokens/sec add a Caffeine cache (1 min TTL).
     *
     * Signing selection: Spring's NimbusJwtEncoder asks for a key via a JWKSelector.
     * We tag PRIMARY with KeyUse.SIGNATURE so Nimbus's default sign selector picks it,
     * and SECONDARY keys are returned with no `use` tag — they still verify by kid but
     * aren't chosen for signing. RETIRED keys are excluded (active=false).
     *
     * Bootstrap fallback: if the table is empty (fresh DB, migrations run but nothing
     * seeded), we generate + persist one on demand and mark it PRIMARY.
     */
    /**
     * FEATURE 11 — pick the {@link com.example.auth.crypto.SigningKeyStore} backend
     * based on features.kms-keys.backend. JPA is the default; VAULT swaps in the
     * remote-signing store.
     */
    @Bean
    public com.example.auth.crypto.SigningKeyStore signingKeyStore(
            com.example.auth.config.FeatureFlags flags,
            SigningKeyRepository jpaRepo,
            org.springframework.beans.factory.ObjectProvider<org.springframework.vault.core.VaultTemplate> vaultProvider,
            org.springframework.beans.factory.ObjectProvider<io.micrometer.observation.ObservationRegistry> obsProvider) {

        if (flags.getKmsKeys().isEnabled()
                && flags.getKmsKeys().getBackend() == com.example.auth.config.FeatureFlags.KmsKeys.Backend.VAULT) {
            var vault = vaultProvider.getObject();
            var vcfg = flags.getKmsKeys().getVault();
            var registry = obsProvider.getIfAvailable(
                    () -> io.micrometer.observation.ObservationRegistry.NOOP);
            log.info("FEATURE 11: SigningKeyStore backend = VAULT (uri={}, key={})",
                    vcfg.getUri(), vcfg.getKeyName());
            return new com.example.auth.crypto.VaultSigningKeyStore(
                    vault, vcfg.getTransitPath(), vcfg.getKeyName(), registry);
        }
        log.info("FEATURE 11: SigningKeyStore backend = JPA (Feature 3 behaviour)");
        return new com.example.auth.crypto.JpaSigningKeyStore(jpaRepo);
    }

    /**
     * JWKSource now delegates to the SigningKeyStore. Behaviour identical to Feature 3
     * for the JPA backend (same sign/verify selector logic). For the Vault backend,
     * activeJwks() returns Vault-tagged JWKs and the sign path is short-circuited
     * (see RemoteSigningJwtEncoder bean below).
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource(
            com.example.auth.crypto.SigningKeyStore store) {
        return (jwkSelector, ctx) -> {
            List<JWK> all = store.activeJwks();

            // Sign vs verify heuristic (Feature 3): sign requests have no kid filter.
            boolean signRequest = jwkSelector.getMatcher().getKeyIDs() == null
                    || jwkSelector.getMatcher().getKeyIDs().isEmpty();
            List<JWK> candidates = signRequest
                    ? all.stream().filter(k -> k.getKeyID().equals(store.primaryKid())).toList()
                    : all;

            return jwkSelector.select(new JWKSet(candidates));
        };
    }

    /**
     * FEATURE 11 — Vault-only: replace Spring's default NimbusJwtEncoder with our
     * remote-signing encoder. Spring's default encoder needs an RSAPrivateKey locally,
     * which is impossible with Vault. On JPA profile this bean is absent and Spring's
     * auto-configured NimbusJwtEncoder is used.
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "features.kms-keys.backend", havingValue = "vault")
    public org.springframework.security.oauth2.jwt.JwtEncoder remoteSigningJwtEncoder(
            com.example.auth.crypto.SigningKeyStore store) {
        log.info("FEATURE 11: JwtEncoder = RemoteSigningJwtEncoder (Vault-backed)");
        return new com.example.auth.crypto.RemoteSigningJwtEncoder(store);
    }

    @Transactional
    protected void bootstrapPrimary(SigningKeyRepository keys) {
        SigningKeyEntity fresh = generateEntity();
        fresh.setStatus(Status.PRIMARY);
        fresh.setActive(true);
        keys.save(fresh);
        log.info("Bootstrapped PRIMARY signing key (kid={})", fresh.getKid());
    }

    /**
     * SigningKeyEntity → Nimbus RSAKey.
     *
     * PRIMARY   → key_ops = [sign, verify]     Nimbus's sign selector picks this
     * SECONDARY → key_ops = [verify]           Included in JWKS + eligible for verify by kid,
     *                                          NOT eligible for signing
     *
     * If we just set use=sig on PRIMARY and left SECONDARY unset, Nimbus's default
     * selector accepts both (unset key_use matches any) → "multiple signing keys" error.
     */
    private static JWK toJwk(SigningKeyEntity e) {
        RSAPublicKey publicKey = parsePublic(e.getPublicKey());
        RSAPrivateKey privateKey = parsePrivate(e.getPrivateKey());
        RSAKey.Builder b = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(e.getKid())
                .keyUse(KeyUse.SIGNATURE);
        if (e.getStatus() == Status.PRIMARY) {
            b.keyOperations(java.util.Set.of(KeyOperation.SIGN, KeyOperation.VERIFY));
        } else {
            b.keyOperations(java.util.Set.of(KeyOperation.VERIFY));
        }
        return b.build();
    }

    // ==========================================================================
    // /admin/** filter chain.
    // securityMatcher("/admin/**") limits this chain to admin URLs so the
    // pre-existing default chain (SecurityConfig @Order 2) still handles
    // everything else without conflict.
    // ==========================================================================

    @Bean
    @Order(0)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
        // Login form itself is handled by SecurityConfig's Order(2) chain (permits /login).
        // FEATURE 5's LockoutAuthenticationHandler is wired into THAT chain via
        // ObjectProvider — see SecurityConfig.defaultSecurityFilterChain.
        http
            .securityMatcher("/admin/**")
            .authorizeHttpRequests(a -> a.anyRequest().hasRole("ADMIN"))
            .formLogin(Customizer.withDefaults())
            .csrf(Customizer.withDefaults());
        return http.build();
    }

    /**
     * FEATURE 14 — REST API mirror.
     * JWT bearer auth on /api/v1/admin/**, method-level scope enforcement via @PreAuthorize.
     * CSRF is off (stateless bearer clients don't have sessions).
     */
    @Bean
    @Order(-1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/v1/**")
            .authorizeHttpRequests(a -> a.anyRequest().authenticated())
            .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(
                    org.springframework.security.config.http.SessionCreationPolicy.STATELESS));
        log.info("FEATURE 14: REST API chain wired at /api/v1/**");
        return http.build();
    }

    // ==========================================================================
    // Helpers
    // ==========================================================================

    private static SigningKeyEntity generateEntity() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair kp = gen.generateKeyPair();
            SigningKeyEntity e = new SigningKeyEntity();
            e.setKid(UUID.randomUUID().toString());
            e.setPublicKey(toPem("PUBLIC KEY", kp.getPublic().getEncoded()));
            e.setPrivateKey(toPem("PRIVATE KEY", kp.getPrivate().getEncoded()));
            return e;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate RSA signing key", ex);
        }
    }

    private static String toPem(String label, byte[] der) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        return "-----BEGIN " + label + "-----\n" + base64 + "\n-----END " + label + "-----\n";
    }

    private static byte[] fromPem(String pem) {
        String stripped = pem
                .replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(stripped);
    }

    private static RSAPublicKey parsePublic(String pem) {
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(fromPem(pem)));
        } catch (Exception ex) {
            throw new IllegalStateException("Bad RSA public key PEM", ex);
        }
    }

    private static RSAPrivateKey parsePrivate(String pem) {
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(fromPem(pem)));
        } catch (Exception ex) {
            throw new IllegalStateException("Bad RSA private key PEM", ex);
        }
    }
}
