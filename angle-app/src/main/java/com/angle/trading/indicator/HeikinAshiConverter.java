package com.angle.trading.indicator;

import com.angle.trading.broker.model.Candle;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts regular OHLCV candles into Heikin Ashi candles.
 *
 * Heikin Ashi smooths price action by averaging OHLC values,
 * making trends easier to identify and reducing noise.
 *
 * Formulas:
 *   HA Close = (Open + High + Low + Close) / 4
 *   HA Open  = (Prev HA Open + Prev HA Close) / 2
 *   HA High  = Max(High, HA Open, HA Close)
 *   HA Low   = Min(Low,  HA Open, HA Close)
 *
 * First candle (no previous HA values):
 *   HA Open  = (Open + Close) / 2
 *   HA Close = (Open + High + Low + Close) / 4
 *   HA High  = High
 *   HA Low   = Low
 *
 * Key signals:
 *   All green + no lower wicks  → strong uptrend
 *   All red + no upper wicks    → strong downtrend
 *   Small body + both wicks     → trend reversal approaching
 */
public final class HeikinAshiConverter {

    private static final MathContext MC   = MathContext.DECIMAL64;
    private static final BigDecimal FOUR  = BigDecimal.valueOf(4);
    private static final BigDecimal TWO   = BigDecimal.valueOf(2);

    private HeikinAshiConverter() {}

    /**
     * Converts a list of regular candles to Heikin Ashi candles.
     * Output list is the same length as input — aligned 1-to-1.
     * Volume and timestamp are preserved from the original candles.
     *
     * @param candles regular OHLCV candles (oldest first)
     * @return Heikin Ashi candles same length as input
     */
    public static List<Candle> convert(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) return List.of();

        List<Candle> result = new ArrayList<>(candles.size());
        BigDecimal prevHaOpen  = null;
        BigDecimal prevHaClose = null;

        for (Candle c : candles) {
            BigDecimal haClose = c.open().add(c.high()).add(c.low()).add(c.close())
                    .divide(FOUR, MC);

            BigDecimal haOpen;
            if (prevHaOpen == null) {
                // First candle — no previous HA values
                haOpen = c.open().add(c.close()).divide(TWO, MC);
            } else {
                haOpen = prevHaOpen.add(prevHaClose).divide(TWO, MC);
            }

            BigDecimal haHigh = c.high().max(haOpen).max(haClose);
            BigDecimal haLow  = c.low().min(haOpen).min(haClose);

            result.add(new Candle(c.timestamp(), haOpen, haHigh, haLow, haClose, c.volume()));

            prevHaOpen  = haOpen;
            prevHaClose = haClose;
        }
        return result;
    }
}
