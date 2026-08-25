package com.angle.trading.indicator;

import com.angle.trading.broker.model.Candle;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Relative Strength Index (Wilder, 1978).
 *
 *   change[i]  = close[i] - close[i-1]
 *   gain[i]    = max(change,  0)
 *   loss[i]    = max(-change, 0)
 *
 *   First avgGain / avgLoss (at index `period`) = simple average of the
 *   first `period` gains / losses.
 *
 *   Subsequent values use Wilder's smoothing:
 *     avgGain[i] = (avgGain[i-1] * (period - 1) + gain[i]) / period
 *     avgLoss[i] = (avgLoss[i-1] * (period - 1) + loss[i]) / period
 *
 *   RS  = avgGain / avgLoss
 *   RSI = 100 - (100 / (1 + RS))
 *
 * Edge cases:
 *   avgLoss == 0 → RSI = 100 (no down moves in the window)
 *   avgGain == 0 → RSI =   0 (no up moves in the window)
 *
 * Convention (>70 overbought, <30 oversold) is a strategy concern, not
 * this class's — this class just returns the number.
 */
public class RelativeStrengthIndex implements Indicator {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final int period;

    public RelativeStrengthIndex(int period) {
        if (period <= 0) throw new IllegalArgumentException("period must be > 0");
        this.period = period;
    }

    /** Standard 14-period RSI. */
    public RelativeStrengthIndex() {
        this(14);
    }

    @Override
    public String name() {
        return "RSI(" + period + ")";
    }

    @Override
    public List<BigDecimal> compute(List<Candle> candles) {
        int n = candles.size();
        List<BigDecimal> out = new ArrayList<>(n);
        // First value cannot be computed — need previous close to get change.
        if (n == 0) return out;
        out.add(null);
        if (n == 1) return out;

        BigDecimal avgGain = null;
        BigDecimal avgLoss = null;
        BigDecimal gainSum = BigDecimal.ZERO;
        BigDecimal lossSum = BigDecimal.ZERO;
        BigDecimal periodBd    = BigDecimal.valueOf(period);
        BigDecimal periodMinus = BigDecimal.valueOf(period - 1L);

        for (int i = 1; i < n; i++) {
            BigDecimal change = candles.get(i).close().subtract(candles.get(i - 1).close());
            BigDecimal gain = change.signum() > 0 ? change : BigDecimal.ZERO;
            BigDecimal loss = change.signum() < 0 ? change.negate() : BigDecimal.ZERO;

            if (i < period) {
                gainSum = gainSum.add(gain);
                lossSum = lossSum.add(loss);
                out.add(null);
                continue;
            }

            if (i == period) {
                gainSum = gainSum.add(gain);
                lossSum = lossSum.add(loss);
                avgGain = gainSum.divide(periodBd, MC);
                avgLoss = lossSum.divide(periodBd, MC);
            } else {
                avgGain = avgGain.multiply(periodMinus, MC).add(gain, MC).divide(periodBd, MC);
                avgLoss = avgLoss.multiply(periodMinus, MC).add(loss, MC).divide(periodBd, MC);
            }

            out.add(rsi(avgGain, avgLoss));
        }
        return out;
    }

    private static BigDecimal rsi(BigDecimal avgGain, BigDecimal avgLoss) {
        if (avgLoss.signum() == 0) return HUNDRED.setScale(4, RoundingMode.HALF_UP);
        if (avgGain.signum() == 0) return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal rs  = avgGain.divide(avgLoss, MC);
        BigDecimal rsi = HUNDRED.subtract(HUNDRED.divide(BigDecimal.ONE.add(rs), MC));
        return rsi.setScale(4, RoundingMode.HALF_UP);
    }
}
