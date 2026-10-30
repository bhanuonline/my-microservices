package com.angle.trading.dto;

import java.util.List;

/**
 * Candle-by-candle indicator breakdown for a single trading date.
 * Only present in the response when {@code breakdown=true} is passed.
 *
 * <p>For today's date (live mode): rows grow as new candles close.
 * For past dates: rows are complete (all candles for that day).
 *
 * @param date            the trading date e.g. "2026-10-30"
 * @param interval        the candle interval e.g. "ONE_HOUR"
 * @param totalBars       number of candle rows returned
 * @param expectedBars    total bars expected for a full session
 *                        (e.g. 26 for 15m, 7 for 1h on NSE)
 * @param sessionProgress e.g. "4 of 26 bars complete (15% of day)"
 * @param rows            one entry per candle, ordered 09:15 → 15:15
 */
public record DayBreakdownDto(
        String                     date,
        String                     interval,
        int                        totalBars,
        int                        expectedBars,
        String                     sessionProgress,
        List<IndicatorSnapshotDto> rows
) {}
