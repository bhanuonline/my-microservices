package com.angle.trading.indicator;

import com.angle.trading.broker.model.Candle;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Volume-Weighted Average Price.
 *
 *   typicalPrice = (high + low + close) / 3
 *   VWAP[i]      = Σ(typicalPrice[j] × volume[j]) / Σ(volume[j])  for j = 0..i
 *
 * This is a "cumulative from series start" VWAP — feed it a single day's
 * intraday candles for the classic session VWAP. If you feed a multi-day
 * series it just accumulates across days, which is rarely useful.
 *
 * Position 0 is defined (equal to typical price if volume > 0);
 * subsequent positions accumulate. If a candle has zero volume, it's
 * skipped in the numerator/denominator but the VWAP still reports the
 * running value.
 */
public class VwapIndicator implements Indicator {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal THREE = BigDecimal.valueOf(3);

    @Override
    public String name() {
        return "VWAP";
    }

    @Override
    public List<BigDecimal> compute(List<Candle> candles) {
        List<BigDecimal> out = new ArrayList<>(candles.size());
        BigDecimal cumVolPrice = BigDecimal.ZERO;
        BigDecimal cumVolume   = BigDecimal.ZERO;

        for (Candle c : candles) {
            BigDecimal typical = c.high().add(c.low()).add(c.close()).divide(THREE, MC);
            BigDecimal vol = BigDecimal.valueOf(c.volume());
            if (vol.signum() > 0) {
                cumVolPrice = cumVolPrice.add(typical.multiply(vol, MC));
                cumVolume   = cumVolume.add(vol);
            }
            if (cumVolume.signum() == 0) {
                // No volume yet (e.g. index candles) — fall back to typical price
                out.add(typical.setScale(4, RoundingMode.HALF_UP));
            } else {
                out.add(cumVolPrice.divide(cumVolume, MC).setScale(4, RoundingMode.HALF_UP));
            }
        }
        return out;
    }
}
