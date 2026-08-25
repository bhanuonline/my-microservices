package com.angle.trading.indicator;

import com.angle.trading.broker.model.Candle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple Moving Average of the close price over a fixed window.
 *
 * SMA at position i (0-indexed) = average of closes[i-period+1 .. i].
 * For i < period-1, result is null (not enough data yet).
 */
public class SimpleMovingAverage implements Indicator {

    private final int period;

    public SimpleMovingAverage(int period) {
        if (period <= 0) throw new IllegalArgumentException("period must be > 0");
        this.period = period;
    }

    @Override
    public String name() {
        return "SMA(" + period + ")";
    }

    @Override
    public List<BigDecimal> compute(List<Candle> candles) {
        List<BigDecimal> out = new ArrayList<>(candles.size());
        BigDecimal windowSum = BigDecimal.ZERO;

        for (int i = 0; i < candles.size(); i++) {
            windowSum = windowSum.add(candles.get(i).close());
            if (i >= period) {
                windowSum = windowSum.subtract(candles.get(i - period).close());
            }
            if (i >= period - 1) {
                out.add(windowSum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP));
            } else {
                out.add(null);
            }
        }
        return out;
    }
}
