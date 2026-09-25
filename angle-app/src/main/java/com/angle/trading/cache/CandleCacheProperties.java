package com.angle.trading.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Config for the pluggable candle cache.
 *
 * Example (application.properties):
 *   bias.cache.provider=caffeine     # caffeine | noop | redis | layered
 *   bias.cache.ttl-seconds=60        # applies to L1 (Caffeine) and default L2
 *   bias.cache.max-size=500          # applies to L1 (Caffeine)
 *   bias.cache.stats-log-minutes=15
 *   bias.cache.redis.ttl-seconds=300 # L2 keeps entries 5× longer than L1 (typical)
 *   bias.cache.redis.key-prefix=candles
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "bias.cache")
public class CandleCacheProperties {

    /** Which cache implementation to activate at boot. */
    private String provider = "caffeine";

    /** L1 TTL — how long an L1 (Caffeine) entry stays fresh before re-asking the next layer. */
    private int ttlSeconds = 60;

    /** L1 max entries — LRU eviction beyond this. */
    private long maxSize = 500;

    /** Emit hit/miss stats to logs every N minutes. 0 = disabled. */
    private int statsLogMinutes = 15;

    /** Redis-specific config (only used when provider = redis or layered). */
    private Redis redis = new Redis();

    /** MySQL persistent store config (used when provider = mysql or layered3). */
    private Mysql mysql = new Mysql();

    /**
     * Redis sub-config. Redis TTL is intentionally longer than L1 so an
     * L1 miss still finds data in L2 (avoiding the slow Angel call).
     */
    @Data
    public static class Redis {
        private int ttlSeconds = 300;         // 5 min — 5× L1 default
        private String keyPrefix = "candles";
    }

    /**
     * MySQL persistent store — L3 tier.
     *
     *   asyncPoolSize    — writer threads for background inserts (2 is plenty for typical volumes)
     *   batchSize        — how many rows to save per JPA saveAll() call
     *   retentionDays    — purge rows older than this many days
     *   purgeCron        — when to run the retention purge (default 3 AM daily)
     *   retentionEnabled — false = keep forever (grows over time)
     */
    @Data
    public static class Mysql {
        private int asyncPoolSize    = 2;
        private int batchSize        = 500;
        private int retentionDays    = 365;
        private String purgeCron     = "0 0 3 * * ?";
        private boolean retentionEnabled = true;
    }
}
