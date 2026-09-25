package com.angle.trading.cache.impl;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.cache.CandleStore;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * No-op store — used when the active cache provider doesn't include a
 * persistent tier (caffeine / redis / layered / noop). Wired so consumers
 * can always depend on {@link CandleStore} without null checks.
 */
public class NoopCandleStore implements CandleStore {

    @Override
    public List<Candle> findRange(String broker, String exchange, String symbolToken,
                                   String intervalType, LocalDate from, LocalDate to) {
        return List.of();
    }

    @Override
    public CompletableFuture<Integer> saveAsync(String broker, String exchange, String symbolToken,
                                                 String intervalType, List<Candle> candles) {
        return CompletableFuture.completedFuture(0);
    }

    @Override public int deleteOlderThan(Instant savedAtCutoff) { return 0; }
    @Override public int deleteByToken(String symbolToken)      { return 0; }
    @Override public long count()                                { return 0; }
    @Override public Optional<Instant> latestTimestamp(String broker, String exchange,
                                                        String symbolToken, String intervalType) {
        return Optional.empty();
    }
}
