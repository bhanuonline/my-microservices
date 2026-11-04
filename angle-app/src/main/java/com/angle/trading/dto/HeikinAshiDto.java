package com.angle.trading.dto;
import java.util.Map;
/**
 * Heikin Ashi analysis section — same structure as regular MTF
 * but computed on HA candles (smoothed OHLC).
 */
public record HeikinAshiDto(
        Map<String, IndicatorsDto> multiTimeframe,
        MtfConclusionDto           conclusion
) {}
