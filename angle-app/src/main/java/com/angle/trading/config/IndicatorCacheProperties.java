package com.angle.trading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * indicators.cache.enabled      = true   — bypass cache entirely when false
 * indicators.cache.ttl          = PT15M  — fallback TTL (not used when date-specific TTLs are set)
 * indicators.cache.ttl-past     = PT24H  — TTL for past dates (data is complete, never changes)
 * indicators.cache.ttl-today    = PT2M   — TTL for today's date (data grows hourly, refresh often)
 * indicators.cache.redis-prefix = ind    — Redis key prefix (ind:indicators|RELIANCE|...)
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "indicators.cache")
public class IndicatorCacheProperties {
    private boolean  enabled     = true;
    private Duration ttl         = Duration.ofMinutes(15);
    private Duration ttlPast     = Duration.ofHours(24);
    private Duration ttlToday    = Duration.ofMinutes(2);
    private String   redisPrefix = "ind";
}
