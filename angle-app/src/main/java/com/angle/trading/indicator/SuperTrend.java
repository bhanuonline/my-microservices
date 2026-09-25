package com.angle.trading.indicator;

import com.angle.trading.broker.model.Candle;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * SuperTrend (Olivier Seban) — trend-following indicator built on ATR.
 *
 * Idea: draw a "trailing stop" line above (bearish) or below (bullish) the
 * candles at a fixed ATR distance. When price closes on the other side of
 * the line, trend flips.
 *
 * Algorithm per candle i (0-indexed):
 *   HL2         = (high[i] + low[i]) / 2
 *   basicUpper  = HL2 + multiplier × ATR[i]
 *   basicLower  = HL2 - multiplier × ATR[i]
 *
 *   finalUpper  = (basicUpper &lt; prevFinalUpper OR prevClose &gt; prevFinalUpper)
 *                    ? basicUpper : prevFinalUpper
 *   finalLower  = (basicLower &gt; prevFinalLower OR prevClose &lt; prevFinalLower)
 *                    ? basicLower : prevFinalLower
 *
 *   Trend flip rules:
 *     if close &gt; prevFinalUpper → trend BULLISH → line = finalLower
 *     if close &lt; prevFinalLower → trend BEARISH → line = finalUpper
 *     else                       → keep prevTrend → same-side line
 *
 * Positions 0..period are null (ATR not yet warmed up).
 * From position period+1 onward we emit {@link Value}(line, isBullish).
 *
 * Common defaults: period=10, multiplier=3.0
 */
public class SuperTrend {

    private static final MathContext MC = MathContext.DECIMAL64;

    /** One SuperTrend output: line value + trend direction. */
    public record Value(BigDecimal line, boolean bullish) {}

    private final int period;
    private final BigDecimal multiplier;

    public SuperTrend(int period, double multiplier) {
        if (period < 1) throw new IllegalArgumentException("period must be >= 1");
        if (multiplier <= 0) throw new IllegalArgumentException("multiplier must be > 0");
        this.period     = period;
        this.multiplier = BigDecimal.valueOf(multiplier);
    }

    public List<Value> compute(List<Candle> candles) {
        int n = candles.size();
        List<Value> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(null);
        if (n <= period) return out;

        List<BigDecimal> atr = new AverageTrueRange(period).compute(candles);
        BigDecimal prevFinalUpper = null, prevFinalLower = null;
        Boolean prevBullish = null;

        for (int i = period; i < n; i++) {
            Candle c = candles.get(i);
            BigDecimal a = atr.get(i);
            if (a == null) continue;

            BigDecimal hl2 = c.high().add(c.low()).divide(BigDecimal.valueOf(2), MC);
            BigDecimal band = a.multiply(multiplier, MC);
            BigDecimal basicUpper = hl2.add(band);
            BigDecimal basicLower = hl2.subtract(band);

            BigDecimal prevClose = candles.get(i - 1).close();

            BigDecimal finalUpper;
            if (prevFinalUpper == null
                    || basicUpper.compareTo(prevFinalUpper) < 0
                    || prevClose.compareTo(prevFinalUpper) > 0) {
                finalUpper = basicUpper;
            } else {
                finalUpper = prevFinalUpper;
            }

            BigDecimal finalLower;
            if (prevFinalLower == null
                    || basicLower.compareTo(prevFinalLower) > 0
                    || prevClose.compareTo(prevFinalLower) < 0) {
                finalLower = basicLower;
            } else {
                finalLower = prevFinalLower;
            }

            boolean bullish;
            if (prevBullish == null) {
                // Bootstrap: use close vs prev upper as the initial call.
                bullish = c.close().compareTo(finalUpper) > 0;
            } else if (prevBullish && c.close().compareTo(finalLower) < 0) {
                bullish = false;
            } else if (!prevBullish && c.close().compareTo(finalUpper) > 0) {
                bullish = true;
            } else {
                bullish = prevBullish;
            }

            BigDecimal line = (bullish ? finalLower : finalUpper).setScale(4, RoundingMode.HALF_UP);
            out.set(i, new Value(line, bullish));

            prevFinalUpper = finalUpper;
            prevFinalLower = finalLower;
            prevBullish    = bullish;
        }
        return out;
    }
}
