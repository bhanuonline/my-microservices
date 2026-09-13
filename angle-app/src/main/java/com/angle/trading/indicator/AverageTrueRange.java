package com.angle.trading.indicator;

import com.angle.trading.broker.model.Candle;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Average True Range (Wilder, 1978) — volatility indicator.
 *
 *   TR[i]  = max(H-L, |H-Cp|, |L-Cp|)   where Cp is previous close
 *   ATR[N] = simple average of TR[1..N]
 *   ATR[i] = (prevATR × (N-1) + TR[i]) / N   (Wilder's smoothing)
 *
 * Position 0 is null (no previous close).
 * Positions 1..N-1 are null (not enough data for first ATR).
 * ATR is defined from position N onward.
 *
 * Use for realistic stop-loss distances: stop = 2 × ATR below entry.
 */
public class AverageTrueRange implements Indicator {

    private static final MathContext MC = MathContext.DECIMAL64;

    private final int period;

    public AverageTrueRange(int period) {
        if (period <= 0) throw new IllegalArgumentException("period must be > 0");
        this.period = period;
    }

    /** Standard 14-period ATR. */
    public AverageTrueRange() {
        this(14);
    }

    @Override
    public String name() {
        return "ATR(" + period + ")";
    }

    @Override
    public List<BigDecimal> compute(List<Candle> candles) {
        int n = candles.size();
        List<BigDecimal> tr = new ArrayList<>(n);
        List<BigDecimal> out = new ArrayList<>(n);

        // TR[0] undefined; use H-L as a fallback so downstream indexing is clean
        tr.add(candles.isEmpty() ? BigDecimal.ZERO : candles.get(0).high().subtract(candles.get(0).low()));
        out.add(null);

        for (int i = 1; i < n; i++) {
            Candle c   = candles.get(i);
            Candle p   = candles.get(i - 1);
            BigDecimal r1 = c.high().subtract(c.low()).abs();
            BigDecimal r2 = c.high().subtract(p.close()).abs();
            BigDecimal r3 = c.low().subtract(p.close()).abs();
            BigDecimal maxTr = r1.max(r2).max(r3);
            tr.add(maxTr);

            if (i < period) {
                out.add(null);
                continue;
            }
            if (i == period) {
                // Seed with SMA of first period TR values (skip TR[0] which is only H-L)
                BigDecimal sum = BigDecimal.ZERO;
                for (int j = 1; j <= period; j++) sum = sum.add(tr.get(j));
                out.add(sum.divide(BigDecimal.valueOf(period), MC).setScale(4, RoundingMode.HALF_UP));
                continue;
            }
            // Wilder smoothing
            BigDecimal prev = out.get(i - 1);
            BigDecimal next = prev.multiply(BigDecimal.valueOf(period - 1L), MC)
                    .add(maxTr, MC)
                    .divide(BigDecimal.valueOf(period), MC);
            out.add(next.setScale(4, RoundingMode.HALF_UP));
        }
        return out;
    }
}
