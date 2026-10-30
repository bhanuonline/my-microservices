package com.angle.trading.dto;

import java.util.Map;

/**
 * Multi-Timeframe indicators response.
 *
 *   symbol            — the instrument
 *   requestedInterval — the highest interval passed by caller
 *   timeframes        — ordered map: lowest → highest TF, each with full indicators
 *   conclusion        — cross-TF analysis: bias, confidence, trade setup
 */
public record MtfResponseDto(
        String symbol,
        String requestedInterval,
        Map<String, IndicatorsDto> timeframes,
        MtfConclusionDto   conclusion,
        TradeSuggestionDto tradeSuggestion
) {}
