package com.angle.trading.orb;

import com.angle.trading.broker.model.Candle;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Wilder's ADX + Directional Indicators — returns +DI, -DI, and ADX for the
 * LAST candle of the series (not a full per-bar series).
 *
 * Same math as {@link com.angle.trading.indicator.AverageDirectionalIndex} but
 * exposes all three values together, which the ORB combined-signal logic needs.
 *
 * Pipeline:
 *   +DM, -DM, TR          → raw per-bar
 *   Wilder-smoothed over N → smoothed +DM, -DM, TR
 *   +DI = 100 × smooth(+DM) / smooth(TR)
 *   -DI = 100 × smooth(-DM) / smooth(TR)
 *   DX  = 100 × |+DI − -DI| / (+DI + -DI)
 *   ADX = Wilder-smoothed DX
 *
 * Needs at least 2 × period candles to warm up (seed TR/DM smoothing + seed DX
 * smoothing). Returns {@link AdxDi#EMPTY} if not enough data.
 */
public final class AdxDiCalculator {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private AdxDiCalculator() {}

    /**
     * Compute +DI, -DI, ADX for the LAST candle in {@code candles}.
     *
     * @param candles chronological OHLC series (oldest first)
     * @param period  smoothing period (14 is Wilder's standard)
     */
    public static AdxDi compute(List<Candle> candles, int period) {
        if (candles == null || candles.size() < period * 2 + 1) return AdxDi.EMPTY;

        int n = candles.size();
        BigDecimal[] plusDm  = new BigDecimal[n];
        BigDecimal[] minusDm = new BigDecimal[n];
        BigDecimal[] tr      = new BigDecimal[n];

        plusDm[0]  = BigDecimal.ZERO;
        minusDm[0] = BigDecimal.ZERO;
        tr[0]      = candles.get(0).high().subtract(candles.get(0).low());

        for (int i = 1; i < n; i++) {
            Candle c = candles.get(i);
            Candle p = candles.get(i - 1);
            BigDecimal upMove   = c.high().subtract(p.high());
            BigDecimal downMove = p.low().subtract(c.low());
            plusDm[i]  = (upMove.compareTo(downMove)   > 0 && upMove.signum()   > 0) ? upMove   : BigDecimal.ZERO;
            minusDm[i] = (downMove.compareTo(upMove) > 0 && downMove.signum() > 0) ? downMove : BigDecimal.ZERO;
            BigDecimal r1 = c.high().subtract(c.low()).abs();
            BigDecimal r2 = c.high().subtract(p.close()).abs();
            BigDecimal r3 = c.low().subtract(p.close()).abs();
            tr[i] = r1.max(r2).max(r3);
        }

        BigDecimal[] sPlusDm  = wilderSmooth(plusDm,  period);
        BigDecimal[] sMinusDm = wilderSmooth(minusDm, period);
        BigDecimal[] sTr      = wilderSmooth(tr,      period);

        // Compute DX for every candle where smoothed values exist
        BigDecimal[] dx = new BigDecimal[n];
        for (int i = 0; i < n; i++) {
            BigDecimal pd = sPlusDm[i], md = sMinusDm[i], t = sTr[i];
            if (pd == null || md == null || t == null || t.signum() == 0) continue;
            BigDecimal plusDi  = HUNDRED.multiply(pd, MC).divide(t, MC);
            BigDecimal minusDi = HUNDRED.multiply(md, MC).divide(t, MC);
            BigDecimal denom   = plusDi.add(minusDi);
            if (denom.signum() == 0) { dx[i] = BigDecimal.ZERO; continue; }
            BigDecimal diff = plusDi.subtract(minusDi).abs();
            dx[i] = HUNDRED.multiply(diff, MC).divide(denom, MC);
        }

        // Smooth DX → ADX
        BigDecimal[] adx = wilderSmoothArr(dx, period);

        // Extract final-bar values
        int last = n - 1;
        BigDecimal plusDi, minusDi;
        if (sTr[last] == null || sTr[last].signum() == 0) {
            plusDi = null; minusDi = null;
        } else {
            plusDi  = HUNDRED.multiply(sPlusDm[last],  MC).divide(sTr[last], MC).setScale(2, RoundingMode.HALF_UP);
            minusDi = HUNDRED.multiply(sMinusDm[last], MC).divide(sTr[last], MC).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal finalAdx = adx[last] == null ? null : adx[last].setScale(2, RoundingMode.HALF_UP);
        return new AdxDi(finalAdx, plusDi, minusDi);
    }

    /** Standard period = 14. */
    public static AdxDi compute(List<Candle> candles) { return compute(candles, 14); }

    // ─── Wilder smoothing (seed = sum of first N, then: prev - prev/N + current) ───

    private static BigDecimal[] wilderSmooth(BigDecimal[] values, int period) {
        int n = values.length;
        BigDecimal[] out = new BigDecimal[n];
        if (n < period + 1) return out;

        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 1; i <= period; i++) sum = sum.add(values[i]);
        out[period] = sum;

        for (int i = period + 1; i < n; i++) {
            BigDecimal prev = out[i - 1];
            out[i] = prev.subtract(prev.divide(BigDecimal.valueOf(period), MC)).add(values[i]);
        }
        return out;
    }

    private static BigDecimal[] wilderSmoothArr(BigDecimal[] values, int period) {
        int n = values.length;
        BigDecimal[] out = new BigDecimal[n];
        int firstNonNull = -1;
        for (int i = 0; i < n; i++) if (values[i] != null) { firstNonNull = i; break; }
        if (firstNonNull < 0 || n < firstNonNull + period) return out;

        BigDecimal sum = BigDecimal.ZERO;
        for (int i = firstNonNull; i < firstNonNull + period; i++) sum = sum.add(values[i]);
        int seedIx = firstNonNull + period - 1;
        BigDecimal seedAvg = sum.divide(BigDecimal.valueOf(period), MC);
        out[seedIx] = seedAvg;

        BigDecimal prev = seedAvg;
        for (int i = seedIx + 1; i < n; i++) {
            if (values[i] == null) continue;
            prev = prev.multiply(BigDecimal.valueOf(period - 1L), MC)
                       .add(values[i], MC)
                       .divide(BigDecimal.valueOf(period), MC);
            out[i] = prev;
        }
        return out;
    }
}
