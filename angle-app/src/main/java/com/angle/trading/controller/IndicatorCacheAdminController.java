package com.angle.trading.controller;

import com.angle.trading.broker.model.Interval;
import com.angle.trading.cache.IndicatorCacheStore;
import com.angle.trading.config.IndicatorCacheProperties;
import com.angle.trading.dto.IndicatorSnapshotDto;
import com.angle.trading.dto.IndicatorsDto;
import com.angle.trading.marketdata.InstrumentMasterService;
import com.angle.trading.marketdata.model.Instrument;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * HTML page:   GET  /admin/cache/indicators
 * Action APIs: POST /api/admin/cache/indicators/...  ← /api/** chain has CSRF disabled
 *
 *   GET  /admin/cache/indicators                              → HTML page
 *   GET  /api/admin/cache/indicators/stats                   → JSON stats
 *   POST /api/admin/cache/indicators/clear                   → evict all
 *   POST /api/admin/cache/indicators/clear/{symbol}          → evict one symbol
 *   POST /api/admin/cache/indicators/refresh?symbol=&...     → force recompute
 */
@Controller
@RequiredArgsConstructor
public class IndicatorCacheAdminController {

    private final IndicatorCacheStore cache;
    private final IndicatorCacheProperties props;
    private final InstrumentMasterService instruments;
    private final StockQuoteController quoteController;

    /** HTML admin page — served under /admin/** (session auth). */
    @GetMapping("/admin/cache/indicators")
    public String page(Model model) {
        model.addAttribute("enabled", props.isEnabled());
        model.addAttribute("ttl",     props.getTtl().toString());
        model.addAttribute("size",    cache.size());
        model.addAttribute("keys",    cache.liveKeys());
        return "admin/indicator-cache";
    }

    /** JSON stats — under /api/** (CSRF disabled, Basic or session auth). */
    @GetMapping("/api/admin/cache/indicators/stats")
    @ResponseBody
    public Map<String, Object> stats() {
        return Map.of(
                "enabled", props.isEnabled(),
                "ttl",     props.getTtl().toString(),
                "size",    cache.size(),
                "keys",    cache.liveKeys()
        );
    }

    @PostMapping("/api/admin/cache/indicators/clear")
    @ResponseBody
    public Map<String, Object> clearAll() {
        int evicted = cache.evictAll();
        return Map.of("evicted", evicted);
    }

    @PostMapping("/api/admin/cache/indicators/clear/{symbol}")
    @ResponseBody
    public Map<String, Object> clearBySymbol(@PathVariable String symbol) {
        int evicted = cache.evictBySymbol(symbol);
        return Map.of("symbol", symbol, "evicted", evicted);
    }

    @PostMapping("/api/admin/cache/indicators/refresh")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> refresh(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "ONE_DAY") Interval interval,
            @RequestParam(defaultValue = "300") int days,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        Instrument inst = instruments.findBySymbol(symbol).orElse(null);
        if (inst == null) return ResponseEntity.notFound().build();

        if (date != null) {
            String key = StockQuoteController.cacheKey("day", symbol, interval, days, date);
            cache.evictBySymbol(symbol);
            List<IndicatorSnapshotDto> fresh = quoteController.computeDayBreakdown(inst, date, interval, days);
            if (fresh == null) return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Computation failed — Angel candle API may be rate-limited"));
            cache.put(key, fresh);
            return ResponseEntity.ok(Map.of("key", key, "rows", fresh.size(), "ttl", props.getTtl().toString()));
        } else {
            LocalDate to = asOf != null ? asOf : LocalDate.now();
            String key = StockQuoteController.cacheKey("indicators", symbol, interval, days, to);
            cache.evictBySymbol(symbol);
            IndicatorsDto fresh = quoteController.computeIndicators(inst, interval, days, asOf);
            if (fresh == null) return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Computation failed — Angel candle API may be rate-limited"));
            cache.put(key, fresh);
            return ResponseEntity.ok(Map.of("key", key, "candleCount", fresh.candleCount(), "ttl", props.getTtl().toString()));
        }
    }
}
