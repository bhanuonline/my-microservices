package com.angle.trading.ai.model;

import java.time.Instant;
import java.util.List;

/**
 * AI-generated trade suggestion for one bias sheet.
 *
 *   action      — LONG / SHORT / WAIT / AVOID (parsed from rationale prefix or falls back)
 *   confidence  — high / medium / low (parsed from prompt tag or estimated)
 *   rationale   — 2-4 sentence narrative
 *   keyPoints   — bulleted extractions from rationale (nice for UI)
 *   model       — which LLM produced this (for auditing / A/B testing later)
 *   cached      — true when served from local cache (no API call this time)
 *   generatedAt — when the response was first computed (not the cache-hit time)
 */
public record AiSignal(
        String       action,
        String       confidence,
        String       rationale,
        List<String> keyPoints,
        String       model,
        boolean      cached,
        Instant      generatedAt
) {}
