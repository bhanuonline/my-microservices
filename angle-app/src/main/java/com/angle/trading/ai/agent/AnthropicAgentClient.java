package com.angle.trading.ai.agent;

import com.angle.trading.ai.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages API client with tool-use (agentic loop) support.
 *
 * Separate from AnthropicClient (single-turn) so existing callers are unaffected.
 *
 * Loop:
 *   1. Send message + tool definitions to Claude
 *   2. Claude responds with tool_use blocks → execute each → send tool_result
 *   3. Repeat until stop_reason = "end_turn" or maxRounds exceeded
 *   4. Return the final text block
 *
 * Anthropic tool-use docs: https://docs.anthropic.com/en/docs/tool-use
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnthropicAgentClient {

    private final AiProperties props;
    private final ObjectMapper  mapper = new ObjectMapper();

    /**
     * Run an agentic loop with the given tools.
     *
     * @param system      system prompt
     * @param userMessage initial user message
     * @param tools       tools Claude may call
     * @return final text response from Claude
     */
    public AgentResult run(String system, String userMessage, List<ResearchTool> tools) {
        AiProperties.Anthropic cfg = props.getAnthropic();
        AiProperties.Agent     agentCfg = props.getAgent();

        if (cfg.getApiKey() == null || cfg.getApiKey().isBlank())
            throw new IllegalStateException("ANTHROPIC_API_KEY is not set");

        List<Map<String, Object>> toolDefs = tools.stream().map(t -> {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("name", t.name());
            d.put("description", t.description());
            d.put("input_schema", t.inputSchema());
            return d;
        }).toList();

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", userMessage));

        List<String> toolsUsed = new ArrayList<>();
        int rounds = 0;
        long start = System.currentTimeMillis();

        RestClient client = RestClient.builder()
                .baseUrl(cfg.getBaseUrl())
                .build();

        while (rounds < agentCfg.getMaxRounds()) {
            rounds++;

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model",      agentCfg.getModel());
            body.put("max_tokens", agentCfg.getMaxTokens());
            body.put("system",     system);
            body.put("tools",      toolDefs);
            body.put("messages",   messages);

            String rawJson = client.post()
                    .uri("/v1/messages")
                    .header("x-api-key",         cfg.getApiKey())
                    .header("anthropic-version", cfg.getApiVersion())
                    .header("content-type",      "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root;
            try {
                root = mapper.readTree(rawJson);
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse Anthropic response: " + e.getMessage());
            }

            String     stopReason  = root.path("stop_reason").asText();
            JsonNode   contentNode = root.path("content");

            // Add assistant turn to message history
            List<Object> assistantContent = new ArrayList<>();
            for (JsonNode block : contentNode) {
                assistantContent.add(mapper.convertValue(block, Map.class));
            }
            messages.add(Map.of("role", "assistant", "content", assistantContent));

            log.debug("Agent round {} — stop_reason={}", rounds, stopReason);

            if ("end_turn".equals(stopReason)) {
                for (JsonNode block : contentNode) {
                    if ("text".equals(block.path("type").asText())) {
                        long ms = System.currentTimeMillis() - start;
                        log.info("Agent done — {} rounds, {} tools, {} ms", rounds, toolsUsed.size(), ms);
                        return new AgentResult(block.path("text").asText().trim(), toolsUsed, ms);
                    }
                }
            }

            if ("tool_use".equals(stopReason)) {
                List<Map<String, Object>> toolResults = new ArrayList<>();
                for (JsonNode block : contentNode) {
                    if (!"tool_use".equals(block.path("type").asText())) continue;

                    String toolName   = block.path("name").asText();
                    String toolUseId  = block.path("id").asText();
                    Map<String, Object> input = mapper.convertValue(block.path("input"), Map.class);

                    log.info("Agent calling tool: {}({})", toolName, input);
                    toolsUsed.add(toolName);

                    String result = tools.stream()
                            .filter(t -> t.name().equals(toolName))
                            .findFirst()
                            .map(t -> safeExecute(t, input))
                            .orElse("Unknown tool: " + toolName);

                    toolResults.add(Map.of(
                            "type",        "tool_result",
                            "tool_use_id", toolUseId,
                            "content",     result
                    ));
                }
                messages.add(Map.of("role", "user", "content", toolResults));
            }
        }

        long ms = System.currentTimeMillis() - start;
        log.warn("Agent loop hit maxRounds={} without end_turn", agentCfg.getMaxRounds());
        return new AgentResult("(agent loop exceeded max rounds)", toolsUsed, ms);
    }

    private String safeExecute(ResearchTool tool, Map<String, Object> args) {
        try {
            return tool.execute(args);
        } catch (Exception e) {
            log.warn("Tool {} failed: {}", tool.name(), e.getMessage());
            return "Tool error: " + e.getMessage();
        }
    }

    public record AgentResult(String text, List<String> toolsUsed, long durationMs) {}
}
