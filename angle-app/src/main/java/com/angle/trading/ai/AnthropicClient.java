package com.angle.trading.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Thin REST client for Anthropic's Messages API.
 *
 * POST https://api.anthropic.com/v1/messages
 * Headers:
 *   x-api-key: <ANTHROPIC_API_KEY>
 *   anthropic-version: 2023-06-01
 *   content-type: application/json
 *
 * Docs: https://docs.anthropic.com/en/api/messages
 *
 * We use a small model + short max_tokens because we want quick, focused
 * responses (not long essays). Failures surface as RuntimeException — the
 * caller (AiSignalService) catches and downgrades to a fallback message.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnthropicClient {

    private final AiProperties aiProperties;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * Send a single message-turn to Claude. Returns the raw text content of
     * the model's first response block. Throws on network / API errors.
     */
    public String complete(String system, String userMessage) {
        AiProperties.Anthropic cfg = aiProperties.getAnthropic();
        if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY is not set");
        }

        Map<String, Object> body = Map.of(
                "model",      cfg.getModel(),
                "max_tokens", cfg.getMaxTokens(),
                "system",     system,
                "messages",   List.of(Map.of("role", "user", "content", userMessage))
        );

        long start = System.currentTimeMillis();
        RestClient client = RestClient.builder()
                .baseUrl(cfg.getBaseUrl())
                .build();

        String rawJson = client.post()
                .uri("/v1/messages")
                .header("x-api-key",         cfg.getApiKey())
                .header("anthropic-version", cfg.getApiVersion())
                .header("content-type",      "application/json")
                .body(body)
                .retrieve()
                .body(String.class);

        long ms = System.currentTimeMillis() - start;
        log.info("Anthropic call OK — model={}, {} ms", cfg.getModel(), ms);
        return extractText(rawJson);
    }

    /**
     * Extract the plain-text content from Claude's JSON response.
     * Shape: { "content": [ { "type": "text", "text": "..." } ], ... }
     */
    private String extractText(String rawJson) {
        try {
            JsonNode root = json.readTree(rawJson);
            JsonNode content = root.path("content");
            if (content.isArray() && content.size() > 0) {
                for (JsonNode block : content) {
                    if ("text".equals(block.path("type").asText())) {
                        return block.path("text").asText().trim();
                    }
                }
            }
            log.warn("Anthropic response had no text block: {}", rawJson);
            return "(empty response from AI)";
        } catch (Exception e) {
            log.warn("Failed to parse Anthropic response: {}", e.getMessage());
            return "(failed to parse AI response)";
        }
    }
}
