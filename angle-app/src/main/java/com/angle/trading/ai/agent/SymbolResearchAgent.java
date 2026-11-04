package com.angle.trading.ai.agent;

import com.angle.trading.ai.AiProperties;
import com.angle.trading.ai.agent.tools.BacktestTool;
import com.angle.trading.ai.agent.tools.IndicatorsTool;
import com.angle.trading.ai.agent.tools.NewsSentimentTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Symbol Research Agent — orchestrates Claude tool calls to produce a ResearchReport.
 *
 * Flow:
 *   1. Check in-memory cache (same symbol + same day = instant return)
 *   2. Build tool list and system prompt
 *   3. Run AnthropicAgentClient loop (Claude picks tools, app executes them)
 *   4. Parse Claude's final JSON response into ResearchReport
 *   5. Cache and return
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SymbolResearchAgent {

    private final AnthropicAgentClient agentClient;
    private final IndicatorsTool       indicatorsTool;
    private final BacktestTool         backtestTool;
    private final NewsSentimentTool    newsSentimentTool;
    private final AiProperties         props;
    private final ObjectMapper         mapper = new ObjectMapper();

    private record CacheEntry(ResearchReport report, Instant cachedAt) {}
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public ResearchReport research(String symbol) {
        String cacheKey = symbol.toUpperCase() + "|" + java.time.LocalDate.now();

        CacheEntry cached = cache.get(cacheKey);
        if (cached != null) {
            long ageMin = java.time.Duration.between(cached.cachedAt(), Instant.now()).toMinutes();
            if (ageMin < props.getAgent().getCacheMinutes()) {
                log.debug("Research cache HIT for {} ({}min old)", symbol, ageMin);
                return cached.report();
            }
        }

        log.info("Starting symbol research for: {}", symbol);

        List<ResearchTool> tools = List.of(indicatorsTool, backtestTool, newsSentimentTool);

        String system = """
                You are a senior Indian equity research analyst.
                Research the given stock symbol using the available tools, then provide a trading recommendation.

                Guidelines:
                - Always call get_indicators first (understand the technical setup)
                - Call run_backtest to validate if the strategy has historical edge
                - Call get_news_sentiment for macro/sector context
                - Base your recommendation on the combination of all three

                After using the tools, respond with ONLY a valid JSON object — no markdown, no extra text:
                {
                  "action": "BUY" or "SELL" or "HOLD",
                  "confidence": <integer 0-100>,
                  "entry": <number or null>,
                  "stop": <number or null>,
                  "target": <number or null>,
                  "reasoning": "<2-3 sentences explaining your recommendation based on the tool results>"
                }
                """;

        String userMessage = "Research this Indian stock and give me a trading recommendation: " + symbol;

        AnthropicAgentClient.AgentResult result = agentClient.run(system, userMessage, tools);

        ResearchReport report = parseReport(symbol, result);

        cache.put(cacheKey, new CacheEntry(report, Instant.now()));
        return report;
    }

    private ResearchReport parseReport(String symbol, AnthropicAgentClient.AgentResult result) {
        try {
            String text = result.text().trim();
            // Strip markdown code fences if present
            if (text.startsWith("```")) {
                text = text.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
            }
            JsonNode node = mapper.readTree(text);

            String     action     = node.path("action").asText("HOLD").toUpperCase();
            int        confidence = node.path("confidence").asInt(50);
            BigDecimal entry      = nodeToBigDecimal(node, "entry");
            BigDecimal stop       = nodeToBigDecimal(node, "stop");
            BigDecimal target     = nodeToBigDecimal(node, "target");
            String     reasoning  = node.path("reasoning").asText("No reasoning provided.");

            return new ResearchReport(
                    symbol, action, confidence, entry, stop, target, reasoning,
                    result.toolsUsed(),
                    props.getAgent().getModel(),
                    result.durationMs(),
                    Instant.now()
            );
        } catch (Exception e) {
            log.warn("Failed to parse agent JSON response for {}: {} — raw: {}", symbol, e.getMessage(), result.text());
            return new ResearchReport(
                    symbol, "HOLD", 0, null, null, null,
                    "Could not parse AI response: " + result.text(),
                    result.toolsUsed(),
                    props.getAgent().getModel(),
                    result.durationMs(),
                    Instant.now()
            );
        }
    }

    private BigDecimal nodeToBigDecimal(JsonNode node, String field) {
        JsonNode n = node.path(field);
        if (n.isNull() || n.isMissingNode()) return null;
        try { return new BigDecimal(n.asText()); } catch (Exception e) { return null; }
    }

    /** Used by admin to clear cache manually. */
    public void clearCache() { cache.clear(); }

    public Map<String, Object> cacheStats() {
        return Map.of("entries", cache.size(), "keys", cache.keySet());
    }
}
