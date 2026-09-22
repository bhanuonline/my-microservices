package com.angle.trading.bias.model;

import java.util.Map;

/**
 * Section 15 — the "should I trade LONG / SHORT / SKIP" answer.
 *
 * Each dimension contributes +1 bullish / -1 bearish / 0 neutral.
 * Sum → strength label → final recommendation.
 */
public record ConsolidatedScore(
        Map<String, Integer> dimensionScores,   // e.g. {"MultiTF":+1, "EMA":+1, "VWAP":+1, "ADX":0, "Structure":+1, "MACD":0, "RSI":+1}
        int totalScore,
        String strength,                        // "STRONG" / "MODERATE" / "WEAK" / "NONE"
        String recommendation,                  // "LONG-ONLY" / "SHORT-ONLY" / "NO-TRADE"
        double confidencePercent                // (|totalScore| / maxPossible) × 100
) {}
