package com.angle.trading.cache.impl;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.cache.CandleCache;
import com.angle.trading.cache.CandleCacheKey;
import com.angle.trading.cache.RangeLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * L1 + L2 layered cache.
 *
 * Read cascade:
 *   1. L1 (fast, in-JVM Caffeine) — hit → return
 *   2. L2 (external Redis) — hit → fill L1 → return
 *   3. Loader (Angel API) — fill L2 → fill L1 → return
 *
 * Write cascade:
 *   invalidate / invalidateByToken / clear → applied to BOTH layers
 *   (otherwise stale L2 could resurrect via L1 refill)
 *
 * Stats returned = L1 stats (that's the hot path — most queries never reach L2).
 */
@Slf4j
@RequiredArgsConstructor
public class LayeredCandleCache implements CandleCache {

    private final CandleCache l1;
    private final CandleCache l2;

    @Override
    public List<Candle> get(CandleCacheKey key, RangeLoader loader) {
        // Wrap the caller's loader so that on L1 miss, we cascade to L2 (not straight to Angel).
        // On L2 miss, THAT wrapper calls the real Angel loader.
        return l1.get(key, (from, to) -> l2.get(key, loader));
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
        // Return the higher count — L1 and L2 may hold different subsets.
        return Math.max(a, b);
    }

    @Override
    public void clear() {
        l1.clear();
        l2.clear();
    }

    @Override
    public CacheStats stats() {
        // Return L1 stats — that's the hot-path indicator most users care about.
        // L2 stats are visible in Redis directly if needed.
        return l1.stats();
    }

    @Override
    public Map<CandleCacheKey, Integer> snapshot() {
        // Merge both snapshots. Prefer L1 count when a key exists in both
        // (L1 count is the freshest — it was filled on the most recent access).
        Map<CandleCacheKey, Integer> merged = new HashMap<>(l2.snapshot());
        merged.putAll(l1.snapshot());
        return merged;
    }
}
