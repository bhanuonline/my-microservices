package com.angle.trading.marketstructure;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.config.AnalysisProperties;
import com.angle.trading.marketstructure.model.SwingPoint;
import com.angle.trading.marketstructure.model.SwingType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects local highs (pivot highs) and local lows (pivot lows).
 *
 * A candle at index i is a swing high if its high is STRICTLY greater than
 * every high in [i - lookback, i - 1] AND in [i + 1, i + lookback].
 * Analogous rule for swing lows (with lows and strictly-less-than).
 *
 * Strict comparison on both sides means true ties never confirm — good
 * defensive default for noisy intraday data.
 *
 * The earliest index that can confirm is `lookback`; the latest is
 * `size - lookback - 1`. Anything outside that range is skipped.
 */
@Service
@RequiredArgsConstructor
public class SwingDetector {

    private final AnalysisProperties analysisProperties;

    public List<SwingPoint> detect(List<Candle> candles) {
        return detect(candles, analysisProperties.getSmc().getSwing().getLookback());
    }

    public List<SwingPoint> detect(List<Candle> candles, int lookback) {
        if (lookback <= 0) throw new IllegalArgumentException("lookback must be > 0");
        List<SwingPoint> swings = new ArrayList<>();
        int n = candles.size();

        for (int i = lookback; i < n - lookback; i++) {
            Candle center = candles.get(i);
            if (isSwingHigh(candles, i, lookback)) {
                swings.add(new SwingPoint(i, center.timestamp(), center.high(), SwingType.HIGH));
            } else if (isSwingLow(candles, i, lookback)) {
                swings.add(new SwingPoint(i, center.timestamp(), center.low(), SwingType.LOW));
            }
        }
        return swings;
    }

    private static boolean isSwingHigh(List<Candle> candles, int i, int lookback) {
        java.math.BigDecimal h = candles.get(i).high();
        for (int j = 1; j <= lookback; j++) {
            if (candles.get(i - j).high().compareTo(h) >= 0) return false;
            if (candles.get(i + j).high().compareTo(h) >= 0) return false;
        }
        return true;
    }

    private static boolean isSwingLow(List<Candle> candles, int i, int lookback) {
        java.math.BigDecimal l = candles.get(i).low();
        for (int j = 1; j <= lookback; j++) {
            if (candles.get(i - j).low().compareTo(l) <= 0) return false;
            if (candles.get(i + j).low().compareTo(l) <= 0) return false;
        }
        return true;
    }
}
