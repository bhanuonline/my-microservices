package com.angle.trading.dto;
/**
 * Cross-validates regular candle vs Heikin Ashi conclusions.
 *
 *   agreement = true  → both agree → VERY_STRONG signal, high confidence
 *   agreement = false → conflicting → WAIT, use with caution
 *
 * tradeRecommendation:
 *   LONG  — both bullish
 *   SHORT — both bearish
 *   WAIT  — conflicting or no clear trend in either
 */
public record CombinedConclusionDto(
        String  regularBias,
        String  haBias,
        boolean agreement,
        String  finalBias,
        String  strength,             // VERY_STRONG | STRONG | MODERATE | WEAK | CONFLICTING
        String  tradeRecommendation,  // LONG | SHORT | WAIT
        String  note
) {}
