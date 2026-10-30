package com.angle.trading.indicator;

import com.angle.trading.broker.model.Candle;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates lower-timeframe candles into higher-timeframe synthetic candles.
 *
 * Example: 2 × 1h candles → 1 × 2h candle
 *   open  = first bar's open
 *   high  = max of all bar highs
 *   low   = min of all bar lows
 *   close = last bar's close
 *   volume = sum of all bar volumes
 *   timestamp = first bar's timestamp (start of the window)
 *
 * Used to synthesize 2h / 3h / 4h candles from 1h data since Angel's
 * SmartAPI historical endpoint does not support those intervals natively.
 */
public final class CandleAggregatorUtil {

    private CandleAggregatorUtil() {}

    /**
     * Aggregate candles into groups of {@code barsPerGroup}.
     * Incomplete trailing groups (< barsPerGroup candles) are included
     * using whatever bars are available — useful for the current live bar.
     */
    public static List<Candle> aggregate(List<Candle> candles, int barsPerGroup) {
        if (candles == null || candles.isEmpty() || barsPerGroup <= 1) return candles;

        List<Candle> out = new ArrayList<>(candles.size() / barsPerGroup + 1);
        int n = candles.size();

        for (int i = 0; i < n; i += barsPerGroup) {
            int end = Math.min(i + barsPerGroup, n);
            List<Candle> group = candles.subList(i, end);

            Candle first = group.get(0);
            Candle last  = group.get(group.size() - 1);

            BigDecimal high = group.stream().map(Candle::high).max(BigDecimal::compareTo).orElse(first.high());
            BigDecimal low  = group.stream().map(Candle::low).min(BigDecimal::compareTo).orElse(first.low());
            long volume     = group.stream().mapToLong(Candle::volume).sum();

            out.add(new Candle(first.timestamp(), first.open(), high, low, last.close(), volume));
        }
        return out;
    }
}
