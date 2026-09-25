package com.angle.trading.controller;

import com.angle.trading.bias.BiasWarmer;
import com.angle.trading.cache.CandleCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Debug endpoints for the candle cache.
 *
 * Mounted under /admin/** so Spring Security's existing chain requires ROLE_ADMIN —
 * these are dev/ops tools, not user-facing.
 *
 * Endpoints:
 *   GET    /admin/cache/candles/stats           → hit / miss / size / hitRate + JVM heap
 *   GET    /admin/cache/candles/keys            → list every cached entry (key + candle count)
 *   POST   /admin/cache/candles/clear           → wipe all entries, return count dropped
 *   DELETE /admin/cache/candles?token=99926000  → wipe every entry for one instrument
 *   POST   /admin/cache/candles/warm            → trigger a warmer round now (don't wait for cron)
 *   GET    /admin/warmer/stats                  → warmer health: rounds, timings, recent history
 *   POST   /admin/warmer/circuit/reset          → force circuit breaker back to CLOSED (admin override)
 *
 * Typical use:
 *   • Confirm cache is warming up  ("misses dropping over time, hits climbing")
 *   • Force a fresh Angel fetch during dev  (POST clear, then hit /bias)
 *   • Verify cache is disabled  (stats always shows zero when provider=noop)
 */
@Slf4j
@RestController
@RequestMapping("/admin/cache/candles")
@RequiredArgsConstructor
public class CacheDebugController {

    private final CandleCache candleCache;
    private final BiasWarmer biasWarmer;

    /** Current cache stats + JVM heap snapshot — cheap, safe to poll. */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        var s = candleCache.stats();
        Runtime rt = Runtime.getRuntime();
        long usedMB  = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long totalMB = rt.totalMemory() / (1024 * 1024);
        long maxMB   = rt.maxMemory()   / (1024 * 1024);
        long usedPct = maxMB == 0 ? 0 : (usedMB * 100 / maxMB);

        return Map.of(
                "hits",     s.hits(),
                "misses",   s.misses(),
                "size",     s.size(),
                "hitRate",  Math.round(s.hitRate() * 1000.0) / 10.0,   // e.g. 87.9 (%)
                "provider", candleCache.getClass().getSimpleName(),
                "heap", Map.of(
                        "usedMB",       usedMB,
                        "committedMB",  totalMB,
                        "maxMB",        maxMB,
                        "usedPercent",  usedPct
                )
        );
    }

    /**
     * List every currently-cached entry with its key + candle count.
     *
     * Response shape (JSON):
     *   {
     *     "count": 3,
     *     "entries": [
     *       { "broker": "ANGEL", "exchange": "NSE", "symbolToken": "99926000",
     *         "interval": "FIVE_MINUTE", "from": "2026-06-26", "to": "2026-09-24",
     *         "candles": 4523 },
     *       ...
     *     ]
     *   }
     *
     * Use this to answer "what's actually in cache right now?" without
     * dumping the entire candle payload.
     */
    @GetMapping("/keys")
    public Map<String, Object> keys() {
        var snapshot = candleCache.snapshot();
        List<Map<String, Object>> entries = new ArrayList<>(snapshot.size());
        snapshot.forEach((k, count) -> entries.add(Map.of(
                "broker",      k.broker(),
                "exchange",    k.exchange(),
                "symbolToken", k.symbolToken(),
                "interval",    k.interval(),
                "from",        k.from().toString(),
                "to",          k.to().toString(),
                "candles",     count
        )));
        return Map.of(
                "count",   entries.size(),
                "entries", entries
        );
    }

    /**
     * Drop every cached entry. Next request per key will hit the broker.
     * Returns the size BEFORE clearing (useful to confirm it did something).
     */
    @PostMapping("/clear")
    public Map<String, Object> clear() {
        long sizeBefore = candleCache.stats().size();
        candleCache.clear();
        log.info("Cache manually cleared via /admin/cache/candles/clear (dropped {} entries)", sizeBefore);
        return Map.of(
                "cleared", true,
                "droppedEntries", sizeBefore
        );
    }

    /**
     * Drop every cached entry for one instrument (all intervals + date ranges).
     * Use when you want to force fresh data for a specific symbol without
     * wiping the whole cache.
     *
     * Example: DELETE /admin/cache/candles?token=99926000  (clears all Nifty 50 entries)
     */
    @DeleteMapping
    public Map<String, Object> clearByToken(@RequestParam String token) {
        int dropped = candleCache.invalidateByToken(token);
        return Map.of(
                "cleared", true,
                "symbolToken", token,
                "droppedEntries", dropped
        );
    }

    /**
     * Trigger the background warmer immediately — pre-fetches all configured
     * instruments. Response returns AFTER the round completes (blocks caller
     * up to bias.warmer.timeout-seconds).
     *
     * Typical use: after a POST /clear, warm the cache back up before real
     * users hit it.
     */
    /**
     * Trigger a warmer round now.
     *
     * @param force when true, bypasses the circuit breaker. Use to test if
     *              Angel has recovered while breaker is OPEN.
     */
    @PostMapping("/warm")
    public Map<String, Object> warmNow(
            @RequestParam(name = "force", defaultValue = "false") boolean force) {
        BiasWarmer.WarmResult r = biasWarmer.warmNow(force);
        return Map.of(
                "triggered",  true,
                "force",      force,
                "total",      r.total(),
                "succeeded",  r.succeeded(),
                "failed",     r.failed()
        );
    }

    /**
     * Warmer health snapshot — round counts, timings, recent history.
     *
     *   totalRounds / successfulRounds / failedRounds  — lifetime counters (since JVM boot)
     *   consecutiveFailures  — climbs when Angel is having issues; reset on next success
     *   lastRoundAt / durationMs / ok / failed        — most recent round detail
     *   avg / min / max DurationMs                    — trend indicators
     *   recentRounds                                   — last 10 rounds, newest last
     *
     * Absolute path so it lives at /admin/warmer/stats (not under /admin/cache/candles/*)
     * — shares the same admin auth chain but semantically outside the candle-cache API.
     */
    @GetMapping("/admin/warmer/stats")
    public BiasWarmer.WarmerStats warmerStats() {
        return biasWarmer.stats();
    }

    /**
     * Force the circuit breaker back to CLOSED. Use when you know Angel is
     * healthy again but the breaker's cooldown hasn't elapsed yet.
     */
    @PostMapping("/admin/warmer/circuit/reset")
    public Map<String, Object> resetCircuit() {
        biasWarmer.resetCircuit();
        return Map.of(
                "reset",        true,
                "circuitState", biasWarmer.stats().circuitState()
        );
    }
}
