package com.angle.trading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * "Keep me signed in" cookie behaviour.
 *
 * Uses the PERSISTENT-TOKEN variant backed by table {@code persistent_logins}.
 * Spring's JdbcTokenRepositoryImpl auto-creates the table on startup when
 * {@code createTableOnStartup=true} — handy for dev. In prod set to false
 * once the table exists and manage it via Flyway/schema tooling.
 *
 *   Cookie value = "seriesId:tokenValue"
 *     • seriesId  stays constant across auto-logins for the same device
 *     • tokenValue rotates every auto-login (detects cookie theft)
 *
 * The signing key below is irrelevant for the persistent variant (that's
 * only used by hash-based), but Spring Security still requires one — keep
 * it secret anyway in case we flip modes.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "security.remember-me")
public class RememberMeProperties {

    /** Master switch. false → checkbox has no effect; cookie never set. */
    private boolean enabled = true;

    /** HTML form parameter name — must match the login template checkbox. */
    private String parameterName = "remember-me";

    /** Cookie name sent back to the browser. */
    private String cookieName = "angle-remember";

    /** Cookie lifetime. 1_209_600 = 14 days. */
    private int tokenValiditySeconds = 14 * 24 * 60 * 60;

    /**
     * Fallback hash key — not consulted in persistent-token mode, but the
     * builder insists on one. Pulled from env so a leaked repo doesn't leak
     * the signing material if we ever switch modes.
     */
    private String key = "change-me-in-env";

    /**
     * Let Spring create persistent_logins on startup if missing.
     * Set to false once you're in production + the table exists.
     */
    private boolean createTableOnStartup = true;
}
