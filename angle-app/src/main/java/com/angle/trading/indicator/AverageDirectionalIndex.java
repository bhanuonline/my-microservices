package com.angle.trading.indicator;

import com.angle.trading.broker.model.Candle;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Average Directional Index (Wilder, 1978) — trend STRENGTH (not direction).
 *
 * Simplified pipeline:
 *   +DM[i] = max(H[i] - H[i-1], 0)  if H diff > L diff  else 0
 *   -DM[i] = max(L[i-1] - L[i], 0)  if L diff > H diff  else 0
 *   TR[i]  = max(H-L, |H-Cp|, |L-Cp|)
 *
 *   Smoothed(N) via Wilder for +DM, -DM, TR
 *   +DI = 100 × smoothed(+DM) / smoothed(TR)
 *   -DI = 100 × smoothed(-DM) / smoothed(TR)
 *   DX  = 100 × |+DI - -DI| / (+DI + -DI)
 *   ADX = Wilder-smoothed DX over N periods
 *
 * Interpretation:
 *   ADX < 20   → no meaningful trend (ranging)
 *   ADX 20-25  → trend developing
 *   ADX > 25   → strong trend
 *   ADX > 40   → very strong trend
 *
 * The first ~2N positions are null (needs seeding + double smoothing).
 */
public class AverageDirectionalIndex implements Indicator {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final int period;

    public AverageDirectionalIndex(int period) {
        if (period <= 0) throw new IllegalArgumentException("period must be > 0");
        this.period = period;
    }

    /** Standard 14-period ADX. */
    public AverageDirectionalIndex() {
        this(14);
    }

    @Override
    public String name() {
        return "ADX(" + period + ")";
    }

    @Override
    public List<BigDecimal> compute(List<Candle> candles) {
        int n = candles.size();
        List<BigDecimal> plusDm  = new ArrayList<>(n);
        List<BigDecimal> minusDm = new ArrayList<>(n);
        List<BigDecimal> tr      = new ArrayList<>(n);

        plusDm.add(BigDecimal.ZERO);
        minusDm.add(BigDecimal.ZERO);
        tr.add(n == 0 ? BigDecimal.ZERO : candles.get(0).high().subtract(candles.get(0).low()));

        for (int i = 1; i < n; i++) {
            Candle c = candles.get(i);
            Candle p = candles.get(i - 1);

            BigDecimal upMove   = c.high().subtract(p.high());
            BigDecimal downMove = p.low().subtract(c.low());

            BigDecimal pdm = (upMove.compareTo(downMove) > 0 && upMove.signum() > 0) ? upMove : BigDecimal.ZERO;
            BigDecimal mdm = (downMove.compareTo(upMove) > 0 && downMove.signum() > 0) ? downMove : BigDecimal.ZERO;
            plusDm.add(pdm);
            minusDm.add(mdm);

            BigDecimal r1 = c.high().subtract(c.low()).abs();
            BigDecimal r2 = c.high().subtract(p.close()).abs();
            BigDecimal r3 = c.low().subtract(p.close()).abs();
            tr.add(r1.max(r2).max(r3));
        }

        List<BigDecimal> sPlusDm  = wilderSmooth(plusDm,  period);
        List<BigDecimal> sMinusDm = wilderSmooth(minusDm, period);
        List<BigDecimal> sTr      = wilderSmooth(tr,      period);

        // Compute DX for each index where TR-smoothed defined
        List<BigDecimal> dx = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            BigDecimal pd = sPlusDm.get(i), md = sMinusDm.get(i), t = sTr.get(i);
            if (pd == null || md == null || t == null || t.signum() == 0) {
                dx.add(null);
                continue;
            }
            BigDecimal plusDi  = HUNDRED.multiply(pd, MC).divide(t, MC);
            BigDecimal minusDi = HUNDRED.multiply(md, MC).divide(t, MC);
            BigDecimal denom   = plusDi.add(minusDi);
            if (denom.signum() == 0) {
                dx.add(BigDecimal.ZERO);
                continue;
            }
            BigDecimal diff = plusDi.subtract(minusDi).abs();
            dx.add(HUNDRED.multiply(diff, MC).divide(denom, MC));
        }

        // Smooth DX to get ADX
        return wilderSmoothList(dx, period);
    }

    /** Wilder smoothing (skips leading nulls) — returns list same length with nulls up front. */
    private static List<BigDecimal> wilderSmooth(List<BigDecimal> values, int period) {
        int n = values.size();
        List<BigDecimal> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(null);
        if (n < period + 1) return out;

        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 1; i <= period; i++) sum = sum.add(values.get(i));
        out.set(period, sum);

        for (int i = period + 1; i < n; i++) {
            BigDecimal prev = out.get(i - 1);
            BigDecimal next = prev.subtract(prev.divide(BigDecimal.valueOf(period), MC))
                    .add(values.get(i));
            out.set(i, next);
        }
        return out;
    }

    /** Wilder smoothing on a series of already-computed values (like DX). */
    private static List<BigDecimal> wilderSmoothList(List<BigDecimal> values, int period) {
        int n = values.size();
        List<BigDecimal> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(null);
        int firstNonNull = -1;
        for (int i = 0; i < n; i++) {
            if (values.get(i) != null) { firstNonNull = i; break; }
        }
        if (firstNonNull < 0 || n < firstNonNull + period) return out;

        BigDecimal sum = BigDecimal.ZERO;
        for (int i = firstNonNull; i < firstNonNull + period; i++) sum = sum.add(values.get(i));
        int seedIx = firstNonNull + period - 1;
        BigDecimal seedAvg = sum.divide(BigDecimal.valueOf(period), MC);
        out.set(seedIx, seedAvg.setScale(4, RoundingMode.HALF_UP));

        BigDecimal prev = seedAvg;
        for (int i = seedIx + 1; i < n; i++) {
            BigDecimal v = values.get(i);
            if (v == null) { out.set(i, null); continue; }
            BigDecimal next = prev.multiply(BigDecimal.valueOf(period - 1L), MC)
                    .add(v, MC)
                    .divide(BigDecimal.valueOf(period), MC);
            out.set(i, next.setScale(4, RoundingMode.HALF_UP));
            prev = next;
        }
        return out;
    }
}
