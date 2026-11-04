package com.angle.trading.ai.agent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Final output of the Symbol Research Agent.
 *
 *   action     — BUY / SELL / HOLD
 *   confidence — 0–100 (Claude's self-assessed confidence)
 *   entry      — suggested entry price (null if HOLD)
 *   stop       — stop-loss level (null if HOLD)
 *   target     — profit target (null if HOLD)
 *   reasoning  — 2–3 sentence explanation
 *   toolsUsed  — which tools Claude called, in order
 *   model      — the Claude model that generated this
 *   durationMs — total wall-clock time including all tool calls
 *   generatedAt— when this report was created
 */
public record ResearchReport(
        String       symbol,
        String       action,
        int          confidence,
        BigDecimal   entry,
        BigDecimal   stop,
        BigDecimal   target,
        String       reasoning,
        List<String> toolsUsed,
        String       model,
        long         durationMs,
        Instant      generatedAt
) {}
