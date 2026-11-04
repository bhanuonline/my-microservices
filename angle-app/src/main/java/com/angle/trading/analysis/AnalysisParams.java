package com.angle.trading.analysis;

import com.angle.trading.broker.model.Interval;

import java.time.LocalDate;

/**
 * Immutable value object that carries all parameters for a single-symbol analysis request.
 *
 * @param symbol       Angel scrip symbol e.g. "HDFCBANK-EQ"
 * @param live         enable live progressive mode
 * @param interval     highest TF in MTF stack (default ONE_DAY)
 * @param baseInterval smallest TF — controls live refresh frequency (null = use config)
 * @param days         calendar days of warmup history
 * @param asOf         historical date — null means today
 * @param breakdown    include candle-by-candle dayBreakdown section
 * @param ha           include Heikin Ashi analysis alongside regular indicators
 * @param marketOpen   override session open "HH:mm" — null = auto-detect
 * @param marketClose  override session close "HH:mm" — null = auto-detect
 */
public record AnalysisParams(
        String    symbol,
        boolean   live,
        Interval  interval,
        Interval  baseInterval,
        int       days,
        LocalDate asOf,
        boolean   breakdown,
        boolean   ha,
        String    marketOpen,
        String    marketClose
) {
    public static AnalysisParams of(String symbol) {
        return new AnalysisParams(symbol, false, Interval.ONE_DAY, null, 60,
                null, false, false, null, null);
    }
}
