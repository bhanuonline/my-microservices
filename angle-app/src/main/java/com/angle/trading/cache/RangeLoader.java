package com.angle.trading.cache;

import com.angle.trading.broker.model.Candle;

import java.time.LocalDate;
import java.util.List;

/**
 * A loader that can fetch candles for ANY date range, not just the
 * range in the original cache key.
 *
 * Why not just {@code Supplier<List<Candle>>}? Because gap-fill needs
 * to say "fetch me only the last 3 days" when the DB already has
 * the first 87. A parameterless supplier forces us to re-fetch the
 * whole 90-day window.
 *
 * Implementations typically wrap a broker call:
 * <pre>
 * RangeLoader loader = (from, to) -> broker.getCandles(exch, token, interval, from, to);
 * </pre>
 */
@FunctionalInterface
public interface RangeLoader {
    List<Candle> load(LocalDate from, LocalDate to);
}
