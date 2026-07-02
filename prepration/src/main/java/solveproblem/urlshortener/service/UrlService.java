package solveproblem.urlshortener.service;


import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import solveproblem.urlshortener.sharding.ConsistentHashRing;
import solveproblem.urlshortener.sharding.SnowflakeIdGenerator;
import solveproblem.urlshortener.util.Base62Encoder;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Optional;

@Service
public class UrlService {

    private final Map<String, DataSource> shardDataSources;
    private final ConsistentHashRing hashRing;
    private final SnowflakeIdGenerator idGenerator;

    public UrlService(@Qualifier("shardDataSources") Map<String, DataSource> shardDataSources,
                       ConsistentHashRing hashRing,
                       SnowflakeIdGenerator idGenerator) {
        this.shardDataSources = shardDataSources;
        this.hashRing = hashRing;
        this.idGenerator = idGenerator;
    }

    /**
     * Creates a new short URL:
     * 1. Generate a globally unique ID (Snowflake)
     * 2. Encode it to a short, URL-safe string (Base62)
     * 3. Determine which shard this short_code belongs to (consistent hashing)
     * 4. Insert the row into that shard
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

        System.out.printf("Created short_code=%s -> routed to %s%n", shortCode, shardId);
        return shortCode;
    }

    /**
     * Looks up the original long URL for a given short_code.
     * Uses the SAME hashing logic to know which shard to query —
     * this consistency is what makes routing work.
     */
    public Optional<String> getLongUrl(String shortCode) {
        String shardId = hashRing.getShardForKey(shortCode);
        DataSource dataSource = shardDataSources.get(shardId);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        try {
            String longUrl = jdbcTemplate.queryForObject(
                    "SELECT long_url FROM urls WHERE short_code = ?",
                    String.class,
                    shortCode
            );
            System.out.printf("Looked up short_code=%s -> found on %s%n", shortCode, shardId);
            return Optional.ofNullable(longUrl);
        } catch (Exception e) {
            System.out.printf("short_code=%s not found on %s%n", shortCode, shardId);
            return Optional.empty();
        }
    }
}
