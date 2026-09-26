package com.angle.trading.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI signal generation config.
 *
 * Example (application.properties):
 *   ai.enabled=true
 *   ai.provider=anthropic
 *   ai.anthropic.api-key=${ANTHROPIC_API_KEY:}
 *   ai.anthropic.model=claude-sonnet-4-5
 *   ai.anthropic.max-tokens=300
 *   ai.cache-minutes=5
 *
 * API key MUST come from environment variable — never commit real keys.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private boolean enabled = false;
    private String  provider = "anthropic";
    private int     cacheMinutes = 5;
    private int     timeoutSeconds = 15;

    private Anthropic anthropic = new Anthropic();

    @Data
    public static class Anthropic {
        private String apiKey;
        private String baseUrl = "https://api.anthropic.com";
        private String model = "claude-sonnet-4-5-20250929";
        private String apiVersion = "2023-06-01";
        private int    maxTokens = 300;
    }
}
