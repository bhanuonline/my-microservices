package com.angle.trading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Brute-force protection for the login form.
 *
 * TWO independent gates:
 *   1. Per-username lock — persisted on {@code AppUserEntity}.
 *      5 failed attempts → account locked for 15 min.
 *      Catches targeted attacks against one user.
 *
 *   2. Per-IP throttle — in-memory Caffeine cache.
 *      20 failed attempts from the same IP in 15 min → block that IP.
 *      Catches botnets rotating usernames.
 *
 * Master switch {@link #enabled} = false bypasses BOTH gates (dev convenience).
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "security.lockout")
public class SecurityLockoutProperties {

    /** Master switch for both gates. false → no lockout at all. */
    private boolean enabled = true;

    /** Fail count that triggers account lock. */
    private int maxAttempts = 5;

    /** How long the account stays locked once triggered. */
    private int lockDurationMinutes = 15;

    private final Ip ip = new Ip();

    @Data
    public static class Ip {
        /** Enable per-IP throttle (second gate). */
        private boolean enabled = true;

        /** Fail count from one IP within {@link #windowMinutes} that blocks the IP. */
        private int maxAttemptsPerWindow = 20;

        /** Rolling window length for IP throttle. */
        private int windowMinutes = 15;
    }
}
