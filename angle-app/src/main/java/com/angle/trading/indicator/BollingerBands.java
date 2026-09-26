package com.angle.trading.indicator;

import com.angle.trading.broker.model.Candle;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Bollinger Bands (John Bollinger, 1980s) — volatility envelope around a moving average.
 *
 *   middle[i] = SMA(N) of close[i-N+1 .. i]
 *   stdDev[i] = √( Σ (close - middle)² / N )
 *   upper[i]  = middle + (multiplier × stdDev)
 *   lower[i]  = middle - (multiplier × stdDev)
 *
 * Positions 0..N-2 are null (not enough data). From position N-1 onward we emit
 * {@link Value}(middle, upper, lower).
 *
 * Common defaults: period=20, multiplier=2.0 (~95% of price action inside).
 */
public class BollingerBands {

    private static final MathContext MC = MathContext.DECIMAL64;

    /** One Bollinger output: the three lines. */
    public record Value(BigDecimal middle, BigDecimal upper, BigDecimal lower) {}

    private final int period;
    private final BigDecimal multiplier;

    public BollingerBands(int period, double multiplier) {
        if (period < 2) throw new IllegalArgumentException("period must be >= 2");
        if (multiplier <= 0) throw new IllegalArgumentException("multiplier must be > 0");
        this.period     = period;
        this.multiplier = BigDecimal.valueOf(multiplier);
    }

    public List<Value> compute(List<Candle> candles) {
        int n = candles.size();
        List<Value> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(null);
        if (n < period) return out;

        BigDecimal periodBd = BigDecimal.valueOf(period);
        for (int i = period - 1; i < n; i++) {
            // 1. SMA of closes in the last `period` candles
            BigDecimal sum = BigDecimal.ZERO;
            for (int k = i - period + 1; k <= i; k++) sum = sum.add(candles.get(k).close());
            BigDecimal middle = sum.divide(periodBd, MC);

            // 2. Population standard deviation
            BigDecimal variance = BigDecimal.ZERO;
            for (int k = i - period + 1; k <= i; k++) {
                BigDecimal diff = candles.get(k).close().subtract(middle);
                variance = variance.add(diff.multiply(diff, MC));
            }
            variance = variance.divide(periodBd, MC);
            double stdDev = Math.sqrt(variance.doubleValue());
            BigDecimal std = BigDecimal.valueOf(stdDev);

            BigDecimal band = std.multiply(multiplier, MC);
            out.set(i, new Value(
                    middle.setScale(4, RoundingMode.HALF_UP),
                    middle.add(band).setScale(4, RoundingMode.HALF_UP),
                    middle.subtract(band).setScale(4, RoundingMode.HALF_UP)
            ));
        }
        return out;
    }
}
