package com.example.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Two jobs:
 * <ol>
 *   <li>Tell Spring "this class is a config-properties target" via
 *       {@code @EnableConfigurationProperties(FeatureFlags.class)}. That's how
 *       Spring knows to bind {@code features.*} properties into the
 *       {@link FeatureFlags} bean.</li>
 *   <li>Log every flag's resolved value at boot. When something behaves
 *       weirdly, this is the first place to check — you can see whether a
 *       feature is on, off, or misconfigured before anything else runs.</li>
 * </ol>
 *
 * <p>The runner block prints a formatted table like:
 * <pre>
 *   ================= Feature flags =================
 *   refresh-token-rotation.enabled         = true
 *   pkce.enforce-for-public-clients        = true
 *   key-rotation.enabled                   = true
 *   ...
 *   =================================================
 * </pre>
 */
@Configuration
@EnableConfigurationProperties(FeatureFlags.class)
public class FeaturesConfig {

    private static final Logger log = LoggerFactory.getLogger(FeaturesConfig.class);

    @Bean
    public ApplicationRunner logFeatureFlags(FeatureFlags flags) {
        return args -> log.info("""
                ================= Feature flags =================
                refresh-token-rotation.enabled         = {}
                refresh-token-rotation.default-for-new = {}
                pkce.enforce-for-public-clients        = {}
                pkce.warn-for-confidential-clients     = {}
                key-rotation.enabled                   = {}
                key-rotation.auto-retire-after-days    = {}
                audit.enabled                          = {}
                audit.retention-days                   = {}
                account-lockout.enabled                = {}
                account-lockout.max-attempts           = {}
                account-lockout.lockout-minutes        = {}
                rate-limit.enabled                     = {}
                rate-limit.login (cap/refillPerMin)    = {}/{}
                rate-limit.token (cap/refillPerMin)    = {}/{}
                custom-claims.enabled                  = {}
                custom-claims (email/roles/uid)        = {}/{}/{}
                metrics.enabled                        = {}
                consent-page.enabled                   = {}
                dcr.enabled                            = {}
                rest-api.enabled                       = {}
                kms-keys.enabled                       = {}
                kms-keys.backend                       = {}
                tracing.enabled                        = {}
                =================================================""",
                flags.getRefreshTokenRotation().isEnabled(),
                flags.getRefreshTokenRotation().isDefaultForNew(),
                flags.getPkce().isEnforceForPublicClients(),
                flags.getPkce().isWarnForConfidentialClients(),
                flags.getKeyRotation().isEnabled(),
                flags.getKeyRotation().getAutoRetireAfterDays(),
                flags.getAudit().isEnabled(),
                flags.getAudit().getRetentionDays(),
                flags.getAccountLockout().isEnabled(),
                flags.getAccountLockout().getMaxAttempts(),
                flags.getAccountLockout().getLockoutMinutes(),
                flags.getRateLimit().isEnabled(),
                flags.getRateLimit().getLogin().getCapacity(),
                flags.getRateLimit().getLogin().getRefillPerMinute(),
                flags.getRateLimit().getToken().getCapacity(),
                flags.getRateLimit().getToken().getRefillPerMinute(),
                flags.getCustomClaims().isEnabled(),
                flags.getCustomClaims().isIncludeEmail(),
                flags.getCustomClaims().isIncludeRoles(),
                flags.getCustomClaims().isIncludeUserId(),
                flags.getMetrics().isEnabled(),
                flags.getConsentPage().isEnabled(),
                flags.getDcr().isEnabled(),
                flags.getRestApi().isEnabled(),
                flags.getKmsKeys().isEnabled(),
                flags.getKmsKeys().getBackend(),
                flags.getTracing().isEnabled());
    }
}
