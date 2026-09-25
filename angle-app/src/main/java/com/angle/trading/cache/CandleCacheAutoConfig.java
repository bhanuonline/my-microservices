package com.angle.trading.cache;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.cache.impl.CaffeineCandleCache;
import com.angle.trading.cache.impl.LayeredCandleCache;
import com.angle.trading.cache.impl.LayeredCandleCacheWithStore;
import com.angle.trading.cache.impl.MysqlCandleStore;
import com.angle.trading.cache.impl.NoopCandleCache;
import com.angle.trading.cache.impl.NoopCandleStore;
import com.angle.trading.cache.impl.RedisCandleCache;
import com.angle.trading.persistence.CandleRepository;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

/**
 * Wires the right {@link CandleCache} implementation at boot based on
 * {@code bias.cache.provider}.
 *
 * Providers:
 *   caffeine (default) — in-JVM L1, fast, cache lost on restart
 *   noop               — cache disabled (every request hits broker)
 *   redis              — external L2 only, restart-safe, shareable
 *   layered            — L1 Caffeine + L2 Redis (fast + persistent)
 *   mysql              — L3 MySQL only (durable, slower — range-aware)
 *   layered3           — L1 Caffeine + L2 Redis + L3 MySQL (full stack)
 *
 * A CandleStore bean is ALWAYS provided (either MysqlCandleStore or Noop)
 * so future consumers can inject it without null checks.
 *
 * Only ONE top-level cache bean is created per boot — the others are skipped
 * by @ConditionalOnProperty.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CandleCacheAutoConfig {

    private final CandleCacheProperties properties;

    // ---------- top-level provider beans ----------

    @Bean
    @ConditionalOnProperty(name = "bias.cache.provider", havingValue = "caffeine", matchIfMissing = true)
    public CandleCache caffeineCandleCache() {
        return new CaffeineCandleCache(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "bias.cache.provider", havingValue = "noop")
    public CandleCache noopCandleCache() {
        return new NoopCandleCache();
    }

    @Bean
    @ConditionalOnProperty(name = "bias.cache.provider", havingValue = "redis")
    public CandleCache redisCandleCache(RedisTemplate<String, List<Candle>> candleRedisTemplate) {
        return new RedisCandleCache(candleRedisTemplate, properties);
    }

    @Bean
    @ConditionalOnProperty(name = "bias.cache.provider", havingValue = "layered")
    public CandleCache layeredCandleCache(RedisTemplate<String, List<Candle>> candleRedisTemplate) {
        CandleCache l1 = new CaffeineCandleCache(properties);
        CandleCache l2 = new RedisCandleCache(candleRedisTemplate, properties);
        log.info("LayeredCandleCache active — L1 Caffeine (ttl={}s) → L2 Redis (ttl={}s)",
                properties.getTtlSeconds(), properties.getRedis().getTtlSeconds());
        return new LayeredCandleCache(l1, l2);
    }

    // ---------- MySQL-backed variants (provider = mysql | layered3) ----------

    @Bean
    @ConditionalOnProperty(name = "bias.cache.provider", havingValue = "mysql")
    public CandleCache mysqlOnlyCandleCache(CandleStore candleStore) {
        // MySQL alone doesn't fit the exact-key CandleCache interface well, so
        // this variant wraps a Noop L1 + Noop L2 + real L3 for uniform behaviour.
        log.info("Mysql-only mode active — L3 MySQL only (no L1/L2)");
        return new LayeredCandleCacheWithStore(new NoopCandleCache(), new NoopCandleCache(), candleStore);
    }

    @Bean
    @ConditionalOnProperty(name = "bias.cache.provider", havingValue = "caffeine-mysql")
    public CandleCache caffeineMysqlCandleCache(CandleStore candleStore) {
        // Popular combo: fast L1 + durable L3, no Redis needed.
        CandleCache l1 = new CaffeineCandleCache(properties);
        log.info("Caffeine+MySQL mode active — L1 Caffeine (ttl={}s) → L3 MySQL", properties.getTtlSeconds());
        return new LayeredCandleCacheWithStore(l1, new NoopCandleCache(), candleStore);
    }

    @Bean
    @ConditionalOnProperty(name = "bias.cache.provider", havingValue = "redis-mysql")
    public CandleCache redisMysqlCandleCache(RedisTemplate<String, List<Candle>> candleRedisTemplate,
                                              CandleStore candleStore) {
        // L2 Redis + L3 MySQL, NO Caffeine L1.
        // Useful when running multiple app instances (per-JVM cache would cause
        // staleness) or when you want zero JVM heap use for caching.
        CandleCache l2 = new RedisCandleCache(candleRedisTemplate, properties);
        log.info("Redis+MySQL mode active — L2 Redis (ttl={}s) → L3 MySQL (no L1)",
                properties.getRedis().getTtlSeconds());
        return new LayeredCandleCacheWithStore(new NoopCandleCache(), l2, candleStore);
    }

    @Bean
    @ConditionalOnProperty(name = "bias.cache.provider", havingValue = "layered3")
    public CandleCache layered3CandleCache(RedisTemplate<String, List<Candle>> candleRedisTemplate,
                                            CandleStore candleStore) {
        CandleCache l1 = new CaffeineCandleCache(properties);
        CandleCache l2 = new RedisCandleCache(candleRedisTemplate, properties);
        log.info("Layered3 cache active — L1 Caffeine (ttl={}s) → L2 Redis (ttl={}s) → L3 MySQL",
                properties.getTtlSeconds(), properties.getRedis().getTtlSeconds());
        return new LayeredCandleCacheWithStore(l1, l2, candleStore);
    }

    /**
     * Always provide a CandleStore bean — MysqlCandleStore when the active
     * provider actually persists, Noop otherwise. Simplifies consumer wiring.
     */
    @Bean
    public CandleStore candleStore(java.util.Optional<CandleRepository> repo) {
        String p = properties.getProvider();
        boolean needsMysql = "mysql".equalsIgnoreCase(p)
                || "layered3".equalsIgnoreCase(p)
                || "caffeine-mysql".equalsIgnoreCase(p)
                || "redis-mysql".equalsIgnoreCase(p);
        if (repo.isPresent() && needsMysql) {
            return new MysqlCandleStore(
                    repo.get(),
                    properties.getMysql().getAsyncPoolSize(),
                    properties.getMysql().getBatchSize());
        }
        return new NoopCandleStore();
    }

    /** Retention purge — deletes rows older than N days. Only active in mysql/layered3 mode. */
    @Configuration
    @Conditional(MysqlNeededCondition.class)
    @RequiredArgsConstructor
    @Slf4j
    public static class MysqlRetentionScheduler {
        private final CandleStore candleStore;
        private final CandleCacheProperties properties;

        @Scheduled(cron = "#{@candleCacheProperties.mysql.purgeCron}")
        public void purge() {
            if (!properties.getMysql().isRetentionEnabled()) return;
            int days = properties.getMysql().getRetentionDays();
            java.time.Instant cutoff = java.time.Instant.now().minus(days, java.time.temporal.ChronoUnit.DAYS);
            int n = candleStore.deleteOlderThan(cutoff);
            log.info("MySQL candle retention: purged {} rows older than {} days", n, days);
        }
    }

    /** True when provider uses MySQL (retention cron + repository scan). */
    static class MysqlNeededCondition implements org.springframework.context.annotation.Condition {
        @Override
        public boolean matches(ConditionContext ctx, AnnotatedTypeMetadata md) {
            String p = ctx.getEnvironment().getProperty("bias.cache.provider", "caffeine");
            return "mysql".equalsIgnoreCase(p)
                    || "layered3".equalsIgnoreCase(p)
                    || "caffeine-mysql".equalsIgnoreCase(p)
                    || "redis-mysql".equalsIgnoreCase(p);
        }
    }

    // ---------- Redis infrastructure (only used by redis / layered providers) ----------

    /**
     * RedisTemplate typed for List<Candle>. Registered only when the active
     * provider actually needs Redis — avoids requiring a running Redis
     * server when using caffeine / noop.
     *
     * Serialization: JSON (Jackson) — human-readable in redis-cli, portable
     * across languages, and handles Instant / BigDecimal via jsr310 module.
     */
    @Bean
    @Conditional(RedisNeededCondition.class)
    public RedisTemplate<String, List<Candle>> candleRedisTemplate(RedisConnectionFactory factory) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(mapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL);

        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer(mapper);

        RedisTemplate<String, List<Candle>> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(valueSerializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }

    /** True when provider uses Redis (needs a RedisTemplate bean). */
    static class RedisNeededCondition implements org.springframework.context.annotation.Condition {
        @Override
        public boolean matches(ConditionContext ctx, AnnotatedTypeMetadata md) {
            String p = ctx.getEnvironment().getProperty("bias.cache.provider", "caffeine");
            return "redis".equalsIgnoreCase(p)
                    || "layered".equalsIgnoreCase(p)
                    || "redis-mysql".equalsIgnoreCase(p)
                    || "layered3".equalsIgnoreCase(p);
        }
    }

    // ---------- periodic stats logger (same as before) ----------

    @Configuration
    @ConditionalOnProperty(name = "bias.cache.stats-log-minutes", matchIfMissing = false)
    @RequiredArgsConstructor
    public static class CacheStatsLogger {

        private final CandleCache cache;

        @Scheduled(fixedRateString = "#{@candleCacheProperties.statsLogMinutes * 60 * 1000}")
        public void logStats() {
            var s = cache.stats();
            if (s.hits() + s.misses() == 0) return;

            Runtime rt = Runtime.getRuntime();
            long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
            long totalMB = rt.totalMemory() / (1024 * 1024);
            long maxMB   = rt.maxMemory()   / (1024 * 1024);
            long usedPct = maxMB == 0 ? 0 : (usedMB * 100 / maxMB);

            log.info("CandleCache stats — hits={}, misses={}, hitRate={}%, size={} | JVM heap used={}MB / committed={}MB / max={}MB ({}%)",
                    s.hits(), s.misses(),
                    Math.round(s.hitRate() * 1000.0) / 10.0,
                    s.size(),
                    usedMB, totalMB, maxMB, usedPct);
        }
    }
}
