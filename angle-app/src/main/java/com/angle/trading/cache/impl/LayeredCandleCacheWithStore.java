package com.angle.trading.cache.impl;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.cache.CandleCache;
import com.angle.trading.cache.CandleCacheKey;
import com.angle.trading.cache.CandleStore;
import com.angle.trading.cache.RangeLoader;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Three-tier cache with GAP-FILL: L1 Caffeine → L2 Redis → L3 MySQL → Angel.
 *
 * The clever bit: on L2 miss, we don't just call the broker for the full
 * requested range. Instead we:
 *   1. Check MySQL for the latest candle we already have
 *   2. If DB has data covering the request → skip Angel entirely
 *   3. If DB is partial → fetch ONLY the missing tail from Angel
 *   4. Merge DB rows + Angel rows, save Angel rows to DB, return
 *
 * Effect: on day 2+, the warmer's Angel calls collapse from 4,500 candles
 * to just a handful (today's delta).
 *
 * Write cascade (invalidate / clear):
 *   L1 and L2: always propagated.
 *   L3 (MySQL): NOT wiped on invalidate/clear — use deleteByToken or
 *   the retention cron for L3 cleanup.
 */
@Slf4j
public class LayeredCandleCacheWithStore implements CandleCache {

    private final CandleCache l1;
    private final CandleCache l2;
    private final CandleStore l3;

    public LayeredCandleCacheWithStore(CandleCache l1, CandleCache l2, CandleStore l3) {
        this.l1 = l1;
        this.l2 = l2;
        this.l3 = l3;
        log.info("LayeredCandleCacheWithStore active — L1 {} + L2 {} + L3 MySQL (gap-fill enabled)",
                l1.getClass().getSimpleName(), l2.getClass().getSimpleName());
    }

    @Override
    public List<Candle> get(CandleCacheKey key, RangeLoader loader) {
        return l1.get(key, (from, to) ->
                l2.get(key, (f2, t2) -> loadWithGapFill(key, loader)));
    }

    /**
     * L3-aware load: check MySQL for existing rows, only fetch the missing
     * tail from Angel. Falls back to full Angel fetch on any DB error.
     */
    private List<Candle> loadWithGapFill(CandleCacheKey key, RangeLoader loader) {
        // What does the DB already have in the requested range?
        List<Candle> fromDb = l3.findRange(
                key.broker(), key.exchange(), key.symbolToken(),
                key.interval(), key.from(), key.to());

        // What's the newest candle in the DB (across ALL dates, not just requested range)?
        Optional<Instant> latest = l3.latestTimestamp(
                key.broker(), key.exchange(), key.symbolToken(), key.interval());

        Instant requestedEndInstant = key.to().plusDays(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant();

        // Case 1 — DB empty → full fetch
        if (latest.isEmpty()) {
            log.info("Gap-fill: L3 empty for {}:{} — full Angel fetch",
                    key.symbolToken(), key.interval());
            return fetchFullAndSave(key, loader);
        }

        // Case 2 — DB has data covering the whole requested range → skip Angel
        if (!latest.get().isBefore(requestedEndInstant)) {
            log.debug("Gap-fill: L3 covers request for {}:{} (latest {} ≥ end {}) — returning {} rows from DB",
                    key.symbolToken(), key.interval(), latest.get(), requestedEndInstant, fromDb.size());
            return fromDb;
        }

        // Case 3 — DB has partial data → fetch delta from Angel
        LocalDate gapFrom = latest.get().atZone(ZoneId.systemDefault()).toLocalDate();
        long gapMinutes = ChronoUnit.MINUTES.between(latest.get(), requestedEndInstant);
        log.info("Gap-fill: {}:{} has data until {} — fetching delta [{} → {}] (~{} min) from Angel",
                key.symbolToken(), key.interval(), latest.get(), gapFrom, key.to(), gapMinutes);

        List<Candle> delta;
        try {
            delta = loader.load(gapFrom, key.to());
        } catch (Exception e) {
            log.warn("Gap-fill: Angel delta fetch failed for {}:{} — returning DB rows only ({} candles). Error: {}",
                    key.symbolToken(), key.interval(), fromDb.size(), e.getMessage());
            return fromDb;
        }

        // Save the delta (fire-and-forget). Dupes handled at DB layer.
        if (delta != null && !delta.isEmpty()) {
            l3.saveAsync(key.broker(), key.exchange(), key.symbolToken(),
                    key.interval(), delta);
        }

        // Merge: DB rows + delta rows (dedup by timestamp, keep newest)
        return mergeByTimestamp(fromDb, delta);
    }

    /** Full-range Angel fetch + async save. Used when DB has nothing. */
    private List<Candle> fetchFullAndSave(CandleCacheKey key, RangeLoader loader) {
        List<Candle> fromAngel = loader.load(key.from(), key.to());
        if (fromAngel != null && !fromAngel.isEmpty()) {
            l3.saveAsync(key.broker(), key.exchange(), key.symbolToken(),
                    key.interval(), fromAngel);
        }
        return fromAngel == null ? List.of() : fromAngel;
    }

    /**
     * Merge two candle lists by timestamp. Delta rows win on collision
     * (they're fresher — Angel is source of truth).
     */
    private static List<Candle> mergeByTimestamp(List<Candle> older, List<Candle> newer) {
        if (newer == null || newer.isEmpty()) return older;
        if (older == null || older.isEmpty()) return newer;
        Map<Instant, Candle> byTs = new HashMap<>(older.size() + newer.size());
        for (Candle c : older) byTs.put(c.timestamp(), c);
        for (Candle c : newer) byTs.put(c.timestamp(), c);   // newer overwrites
        List<Candle> merged = new ArrayList<>(byTs.values());
        merged.sort((a, b) -> a.timestamp().compareTo(b.timestamp()));
        return merged;
    }

    @Override
    public void invalidate(CandleCacheKey key) {
        l1.invalidate(key);
        l2.invalidate(key);
    }

    @Override
    public int invalidateByToken(String symbolToken) {
        int a = l1.invalidateByToken(symbolToken);
        int b = l2.invalidateByToken(symbolToken);
        return Math.max(a, b);
    }

    @Override
    public void clear() {
        l1.clear();
        l2.clear();
    }

    @Override
    public CacheStats stats() {
        return l1.stats();
    }

    @Override
    public Map<CandleCacheKey, Integer> snapshot() {
        Map<CandleCacheKey, Integer> merged = new HashMap<>(l2.snapshot());
        merged.putAll(l1.snapshot());
        return merged;
    }
}
