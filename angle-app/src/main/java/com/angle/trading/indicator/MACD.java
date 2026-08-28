package com.angle.trading.indicator;

import com.angle.trading.broker.model.Candle;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Moving Average Convergence Divergence (Gerald Appel).
 *
 *   MACD line   = EMA(fast) - EMA(slow)          (typically 12, 26)
 *   Signal line = EMA(signalPeriod) of MACD line (typically 9)
 *   Histogram   = MACD line - Signal line
 *
 * Because MACD is inherently multi-valued we expose two APIs:
 *
 *   compute(candles)        → List<BigDecimal>  — MACD line only (Indicator contract)
 *   computeSeries(candles)  → List<MacdValue>   — MACD, signal, histogram together
 *
 * Positions where any component is undefined are null (in {@code compute})
 * or fields inside {@link MacdValue} are null (in {@code computeSeries}).
 */
public class MACD implements Indicator {

    private static final MathContext MC = MathContext.DECIMAL64;

    private final int fastPeriod;
    private final int slowPeriod;
    private final int signalPeriod;

    private final ExponentialMovingAverage fastEma;
    private final ExponentialMovingAverage slowEma;

    /** Standard MACD(12, 26, 9). */
    public MACD() {
        this(12, 26, 9);
    }

    public MACD(int fastPeriod, int slowPeriod, int signalPeriod) {
        if (fastPeriod <= 0 || slowPeriod <= 0 || signalPeriod <= 0) {
            throw new IllegalArgumentException("periods must be > 0");
        }
        if (fastPeriod >= slowPeriod) {
            throw new IllegalArgumentException("fastPeriod must be < slowPeriod");
        }
        this.fastPeriod   = fastPeriod;
        this.slowPeriod   = slowPeriod;
        this.signalPeriod = signalPeriod;
        this.fastEma      = new ExponentialMovingAverage(fastPeriod);
        this.slowEma      = new ExponentialMovingAverage(slowPeriod);
    }

    @Override
    public String name() {
        return "MACD(" + fastPeriod + "," + slowPeriod + "," + signalPeriod + ")";
    }

    /** Just the MACD line — for the plain Indicator contract. */
    @Override
    public List<BigDecimal> compute(List<Candle> candles) {
        List<MacdValue> series = computeSeries(candles);
        List<BigDecimal> out = new ArrayList<>(series.size());
        for (MacdValue v : series) out.add(v.macd());
        return out;
    }

    /** Full MACD output: MACD line, signal line, histogram. */
    public List<MacdValue> computeSeries(List<Candle> candles) {
        int n = candles.size();
        List<BigDecimal> fast = fastEma.compute(candles);
        List<BigDecimal> slow = slowEma.compute(candles);

        // MACD line = fast - slow (available once slow EMA is defined, i.e. i >= slowPeriod - 1)
        List<BigDecimal> macdLine = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            BigDecimal f = fast.get(i);
            BigDecimal s = slow.get(i);
            macdLine.add((f == null || s == null) ? null : f.subtract(s));
        }

        // Signal = EMA(signalPeriod) of macdLine — but seeded on the FIRST non-null MACD value.
        List<BigDecimal> signal   = new ArrayList<>(n);
        List<BigDecimal> histogram = new ArrayList<>(n);
        BigDecimal signalMultiplier = BigDecimal.valueOf(2)
                .divide(BigDecimal.valueOf(signalPeriod + 1L), MC);
        BigDecimal oneMinusMult = BigDecimal.ONE.subtract(signalMultiplier);

        BigDecimal seedSum   = BigDecimal.ZERO;
        int        seedCount = 0;
        BigDecimal prevSignal = null;

        for (int i = 0; i < n; i++) {
            BigDecimal m = macdLine.get(i);
            if (m == null) {
                signal.add(null);
                histogram.add(null);
                continue;
            }

            if (prevSignal == null) {
                seedSum = seedSum.add(m);
                seedCount++;
                if (seedCount < signalPeriod) {
                    signal.add(null);
                    histogram.add(null);
                    continue;
                }
                prevSignal = seedSum.divide(BigDecimal.valueOf(signalPeriod), MC);
            } else {
                prevSignal = m.multiply(signalMultiplier, MC)
                        .add(prevSignal.multiply(oneMinusMult, MC), MC);
            }

            BigDecimal sig  = prevSignal.setScale(4, RoundingMode.HALF_UP);
            BigDecimal hist = m.subtract(prevSignal).setScale(4, RoundingMode.HALF_UP);
            signal.add(sig);
            histogram.add(hist);
        }

        List<MacdValue> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            BigDecimal m = macdLine.get(i);
            out.add(new MacdValue(
                    m == null ? null : m.setScale(4, RoundingMode.HALF_UP),
                    signal.get(i),
                    histogram.get(i)
            ));
        }
        return out;
    }

    public record MacdValue(BigDecimal macd, BigDecimal signal, BigDecimal histogram) {}
}
