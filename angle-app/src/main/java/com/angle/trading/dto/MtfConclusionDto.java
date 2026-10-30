package com.angle.trading.dto;

import java.util.List;

/**
 * Overall multi-timeframe conclusion.
 *
 *   primaryBias          — trend from the highest timeframe (BULLISH | BEARISH | null)
 *   alignedTimeframes    — timeframes that agree with primaryBias
 *   conflictingTimeframes — timeframes going against primaryBias
 *   confidence           — STRONG | MODERATE | WEAK (based on how many HIGHER TFs agree)
 *   tradeSetup           — actionable label:
 *       TREND_CONTINUATION   — all TFs aligned → enter now in bias direction
 *       WAIT_FOR_PULLBACK_END — higher TFs have bias, lower TFs going against (pullback in progress)
 *       MIXED_SIGNALS        — some higher TFs agree, others don't → caution
 *       NO_CLEAR_TREND       — no consistent bias in higher TFs
 */
public record MtfConclusionDto(
        String       primaryBias,
        List<String> alignedTimeframes,
        List<String> conflictingTimeframes,
        String       confidence,
        String       tradeSetup
) {}
