package solveproblem.urlshortener.service;


import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import solveproblem.urlshortener.sharding.ConsistentHashRing;
import solveproblem.urlshortener.sharding.SnowflakeIdGenerator;
import solveproblem.urlshortener.util.Base62Encoder;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Service
public class UrlService {

    private final Map<String, DataSource> shardDataSources;
    private final ConsistentHashRing hashRing;
    private final SnowflakeIdGenerator idGenerator;
    private final StringRedisTemplate redisTemplate;

    // Cache entries expire after 24 hours as a safety net, even though
    // long_url essentially never changes after creation.
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final String CACHE_KEY_PREFIX = "shorturl:";

    public UrlService(@Qualifier("shardDataSources") Map<String, DataSource> shardDataSources,
                      ConsistentHashRing hashRing,
                      SnowflakeIdGenerator idGenerator,
                      StringRedisTemplate redisTemplate) {
        this.shardDataSources = shardDataSources;
        this.hashRing = hashRing;
        this.idGenerator = idGenerator;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Creates a new short URL:
     * 1. Generate a globally unique ID (Snowflake)
     * 2. Encode it to a short, URL-safe string (Base62)
     * 3. Determine which shard this short_code belongs to (consistent hashing)
     * 4. Insert the row into that shard (source of truth)
     * 5. Pre-populate the cache, since this URL is likely to be read soon
     */
    public String createShortUrl(String longUrl) {
        long id = idGenerator.generateId();
        String shortCode = Base62Encoder.encode(id);

        String shardId = hashRing.getShardForKey(shortCode);
        DataSource dataSource = shardDataSources.get(shardId);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update(
                "INSERT INTO urls (short_code, long_url) VALUES (?, ?)",
                shortCode, longUrl
        );

        // Write-through: populate cache immediately so the very first read is fast
        redisTemplate.opsForValue().set(CACHE_KEY_PREFIX + shortCode, longUrl, CACHE_TTL);

        System.out.printf("Created short_code=%s -> routed to %s, cached%n", shortCode, shardId);
        return shortCode;
    }

    /**
     * Looks up the original long URL for a given short_code.
     * Cache-aside pattern:
     *   1. Check Redis first
     *   2. If HIT -> return immediately, DB never touched
     *   3. If MISS -> query the correct shard, then populate cache for next time
     */
    public Optional<String> getLongUrl(String shortCode) {
        String cacheKey = CACHE_KEY_PREFIX + shortCode;

        // 1. Check cache first
        String cachedUrl = redisTemplate.opsForValue().get(cacheKey);
        if (cachedUrl != null) {
            System.out.printf("CACHE HIT for short_code=%s%n", shortCode);
            return Optional.of(cachedUrl);
        }

        // 2. Cache miss -> fall back to the database
        System.out.printf("CACHE MISS for short_code=%s -> querying DB%n", shortCode);
        String shardId = hashRing.getShardForKey(shortCode);
        DataSource dataSource = shardDataSources.get(shardId);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        try {
            String longUrl = jdbcTemplate.queryForObject(
                    "SELECT long_url FROM urls WHERE short_code = ?",
                    String.class,
                    shortCode
            );

            // 3. Populate cache so subsequent reads are fast
            redisTemplate.opsForValue().set(cacheKey, longUrl, CACHE_TTL);

            System.out.printf("Looked up short_code=%s -> found on %s, cached for next time%n", shortCode, shardId);
            return Optional.ofNullable(longUrl);
        } catch (Exception e) {
            System.out.printf("short_code=%s not found on %s%n", shortCode, shardId);
            return Optional.empty();
        }
    }

    /**
     * INTENTIONALLY BUGGY — for learning purposes.
     * Updates the long_url in the database ONLY.
     * Does NOT touch Redis. This will cause stale reads:
     * the DB has the new value, but Redis keeps serving the OLD value
     * until the TTL (24h) expires.
     *
     * We'll fix this once we've observed the bug.
     */
    public boolean updateLongUrlBuggy(String shortCode, String newLongUrl) {
        String shardId = hashRing.getShardForKey(shortCode);
        DataSource dataSource = shardDataSources.get(shardId);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        int rowsUpdated = jdbcTemplate.update(
                "UPDATE urls SET long_url = ? WHERE short_code = ?",
                newLongUrl, shortCode
        );

        System.out.printf("[BUGGY UPDATE] short_code=%s -> DB updated on %s, Redis NOT touched (bug)%n", shortCode, shardId);
        return rowsUpdated > 0;
    }
}
