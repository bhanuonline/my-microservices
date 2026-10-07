package solveproblem.urlshortener.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class ShardDataSourceConfig {

    // Each shard is a fully independent MySQL instance (different Docker container / port).
    // In production these would be different physical/virtual machines.
    private DataSource buildDataSource(String url, String username, String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setMaximumPoolSize(5); // small pool, fine for local testing
        return ds;
    }

    @Bean
    public Map<String, DataSource> shardDataSources() {
        Map<String, DataSource> shards = new LinkedHashMap<>();

        shards.put("shard-1", buildDataSource(
                "jdbc:mysql://localhost:3311/urlshortener", "root", "root"));

        shards.put("shard-2", buildDataSource(
                "jdbc:mysql://localhost:3312/urlshortener", "root", "root"));

        shards.put("shard-3", buildDataSource(
                "jdbc:mysql://localhost:3313/urlshortener", "root", "root"));

        shards.put("shard-4", buildDataSource(
                "jdbc:mysql://localhost:3314/urlshortener", "root", "root"));

        return shards;
    }
}
