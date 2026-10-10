package com.angle.trading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Token-bucket rate limit for /api/** requests.
 *
 * One bucket per caller key (username + ip by default). The bucket starts
 * full with {@link #capacity} tokens, each request consumes one, and refill
 * adds {@link #refillTokens} every {@link #refillPeriodSeconds} seconds up
 * to the capacity ceiling.
 *
 * When empty, the filter responds 429 with a {@code Retry-After} header.
 *
 * Paths matching {@link #exemptPatterns} skip the filter entirely —
 * essential for SSE/WebSocket streams whose "requests" are long-lived.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "security.rate-limit")
public class RateLimitProperties {

    /** Master switch. false → filter is a passthrough. */
    private boolean enabled = true;

    /** Bucket size (also max burst). */
    private int capacity = 100;

    /** Tokens added per refill period. */
    private int refillTokens = 100;

    /** How often tokens are added, in seconds. 60 + refillTokens=100 → 100/min. */
    private int refillPeriodSeconds = 60;

    /**
     * Caller key strategy — identifies which bucket a request draws from.
     *   USER_AND_IP → "username:ip"       (default, fairest)
     *   USER        → "username"
     *   IP          → "ip"
     */
    private KeyStrategy keyStrategy = KeyStrategy.USER_AND_IP;

    /**
     * URL-pattern exemptions. Ant-style patterns under /api.
     * Matched before the bucket is touched.
     *
     * Default exempts SSE streams; one open stream would otherwise drain
     * the bucket within a minute of heartbeats.
     */
    private List<String> exemptPatterns = new ArrayList<>(List.of(
            "/api/live/stream",       // SSE live tick feed
            "/api/live/stream/**"     // any future sub-paths
    ));

    public enum KeyStrategy { USER_AND_IP, USER, IP }
}
