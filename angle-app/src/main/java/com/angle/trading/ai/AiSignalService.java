package com.angle.trading.ai;

import com.angle.trading.ai.model.AiSignal;
import com.angle.trading.bias.model.BiasSheet;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Orchestrates a single "get AI opinion" call.
 *
 *   1. Build prompt from BiasSheet.
 *   2. Check per-symbol cache — same sheet → return cached AiSignal.
 *   3. Call {@link AnthropicClient#complete}.
 *   4. Parse response into structured {@link AiSignal}.
 *   5. Store in cache. Return.
 *
 * All failures are caught and returned as a "WAIT" signal with the error
 * message — the UI keeps working even if the AI is down.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSignalService {

    private final AiProperties aiProperties;
    private final AnthropicClient anthropic;
    private final AiPromptBuilder promptBuilder;

    private Cache<String, AiSignal> cache;

    @PostConstruct
    void init() {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(Math.max(1, aiProperties.getCacheMinutes())))
                .maximumSize(200)
                .build();
        log.info("AiSignalService initialised — provider={}, cacheMin={}",
                aiProperties.getProvider(), aiProperties.getCacheMinutes());
    }

    public boolean isEnabled() {
        return aiProperties.isEnabled()
                && aiProperties.getAnthropic().getApiKey() != null
                && !aiProperties.getAnthropic().getApiKey().isBlank();
    }

    public AiSignal analyse(BiasSheet sheet) {
        if (!isEnabled()) {
            return fallback("AI disabled — set ai.enabled=true and ANTHROPIC_API_KEY", true);
        }
        String cacheKey = sheet.symbolToken() + "|" + sheet.asOf().getEpochSecond();
        AiSignal cached = cache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("AI signal cache HIT for {}", cacheKey);
            return new AiSignal(cached.action(), cached.confidence(), cached.rationale(),
                    cached.keyPoints(), cached.model(), true, cached.generatedAt());
        }

        String userMsg = promptBuilder.user(sheet);
        try {
            String raw = anthropic.complete(promptBuilder.system(), userMsg);
            AiSignal parsed = parse(raw, aiProperties.getAnthropic().getModel());
            cache.put(cacheKey, parsed);
            return parsed;
        } catch (Exception e) {
            log.warn("AI call failed for {}: {}", sheet.symbol(), e.getMessage());
            return fallback("AI call failed: " + e.getMessage(), false);
        }
    }

    // ---------- parse ----------

    /**
     * Parse Claude's response into a structured AiSignal.
     * Expected shape (from the system prompt):
     *   ACTION: LONG (high confidence)
     *   sentence 1
     *   sentence 2
     *   sentence 3
     *   KEY: point1 ; point2 ; point3
     */
    private static AiSignal parse(String raw, String model) {
        String action = "WAIT";
        String confidence = "medium";
        String rationale;
        List<String> keyPoints = new ArrayList<>();

        String[] lines = raw.split("\\r?\\n");
        StringBuilder rat = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.toUpperCase().startsWith("ACTION:")) {
                String rest = trimmed.substring(7).trim();
                // e.g. "LONG (high confidence)"
                String[] parts = rest.split("\\s+", 2);
                if (parts.length >= 1) action = parts[0].toUpperCase();
                if (parts.length >= 2) {
                    String tail = parts[1].toLowerCase();
                    if (tail.contains("high"))    confidence = "high";
                    else if (tail.contains("low")) confidence = "low";
                    else                            confidence = "medium";
                }
            } else if (trimmed.toUpperCase().startsWith("KEY:")) {
                String rest = trimmed.substring(4).trim();
                keyPoints = Arrays.stream(rest.split(";"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
            } else {
                if (rat.length() > 0) rat.append(' ');
                rat.append(trimmed);
            }
        }
        rationale = rat.length() == 0 ? raw : rat.toString();
        return new AiSignal(action, confidence, rationale, keyPoints, model, false, Instant.now());
    }

    private static AiSignal fallback(String message, boolean disabled) {
        return new AiSignal(
                "WAIT",
                "low",
                message,
                List.of(),
                disabled ? "n/a" : "error",
                false,
                Instant.now()
        );
    }
}
