package com.angle.trading.indicator;

import com.angle.trading.broker.model.Candle;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Exponential Moving Average of the close price.
 *
 * Weights recent prices more heavily than older ones:
 *
 *   multiplier k = 2 / (period + 1)
 *   EMA[i] = close[i] * k + EMA[i-1] * (1 - k)
 *
 * Seeded at position (period - 1) with a plain SMA of the first `period`
 * closes. Positions before that are null.
 *
 * Useful on its own AND as a building block for MACD.
 */
public class ExponentialMovingAverage implements Indicator {

    private static final MathContext MC = MathContext.DECIMAL64;

    private final int period;
    private final BigDecimal multiplier;
    private final BigDecimal oneMinusMultiplier;

    public ExponentialMovingAverage(int period) {
        if (period <= 0) throw new IllegalArgumentException("period must be > 0");
        this.period = period;
        this.multiplier = BigDecimal.valueOf(2)
                .divide(BigDecimal.valueOf(period + 1L), MC);
        this.oneMinusMultiplier = BigDecimal.ONE.subtract(multiplier);
    }

    @Override
    public String name() {
        return "EMA(" + period + ")";
    }

    @Override
    public List<BigDecimal> compute(List<Candle> candles) {
        List<BigDecimal> out = new ArrayList<>(candles.size());
        BigDecimal seedSum = BigDecimal.ZERO;
        BigDecimal prevEma = null;

        for (int i = 0; i < candles.size(); i++) {
            BigDecimal close = candles.get(i).close();

            if (i < period - 1) {
                seedSum = seedSum.add(close);
                out.add(null);
                continue;
            }

            if (i == period - 1) {
                seedSum = seedSum.add(close);
                prevEma = seedSum.divide(BigDecimal.valueOf(period), MC);
                out.add(prevEma.setScale(4, RoundingMode.HALF_UP));
                continue;
            }

            // EMA[i] = close * k + prevEma * (1 - k)
            prevEma = close.multiply(multiplier, MC)
                    .add(prevEma.multiply(oneMinusMultiplier, MC), MC);
            out.add(prevEma.setScale(4, RoundingMode.HALF_UP));
        }
        return out;
    }
}
