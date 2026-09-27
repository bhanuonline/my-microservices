package com.angle.trading.controller;

import com.angle.trading.bias.BiasWarmer;
import com.angle.trading.cache.CandleCache;
import com.angle.trading.cache.CandleCacheProperties;
import com.angle.trading.config.BiasProperties;
import com.angle.trading.config.ReportsProperties;
import com.angle.trading.report.DailyReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * HTMX-specific action endpoints for the dashboard.
 *
 * Each action does the work (via the same underlying services used by the
 * plain-JSON controllers) and returns the refreshed Thymeleaf fragment.
 * HTMX swaps just that card in place — no full-page reload.
 *
 * Why separate from CacheDebugController/DailyReportController?
 *   • Those return JSON for API/Postman use — must stay JSON
 *   • These return HTML fragments for the dashboard
 *   • Cleanly separated concerns; both can coexist
 *
 * URL prefix /api/dashboard/action/... makes intent obvious in the network tab.
 */
@Slf4j
@Controller
@RequestMapping("/api/dashboard/action")
@RequiredArgsConstructor
public class DashboardActionController {

    private final CandleCache candleCache;
    private final CandleCacheProperties cacheProperties;
    private final BiasWarmer biasWarmer;
    private final BiasProperties biasProperties;
    private final DailyReportService reportService;
    private final ReportsProperties reportsProperties;

    // ---------- Bias Modules card (sub-toggles) ----------

    /**
     * Toggle any one bias sub-module (vix / correlated / breadth / circuit).
     * Path variable tells us which flag to flip; returns the refreshed card.
     */
    @PostMapping("/bias-module/{name}/toggle")
    public String biasModuleToggle(@org.springframework.web.bind.annotation.PathVariable String name, Model model) {
        boolean newState;
        switch (name.toLowerCase()) {
            case "vix" -> {
                newState = !biasProperties.getVix().isEnabled();
                biasProperties.getVix().setEnabled(newState);
            }
            case "correlated" -> {
                newState = !biasProperties.getCorrelated().isEnabled();
                biasProperties.getCorrelated().setEnabled(newState);
            }
            case "breadth" -> {
                newState = !biasProperties.getBreadth().isEnabled();
                biasProperties.getBreadth().setEnabled(newState);
            }
            case "circuit" -> {
                newState = !biasProperties.getWarmer().getCircuit().isEnabled();
                biasProperties.getWarmer().getCircuit().setEnabled(newState);
            }
            default -> {
                log.warn("Dashboard: unknown bias module '{}'", name);
                model.addAttribute("biasSub", buildBiasSubModel());
                return "fragments/dashboard-cards :: biasModulesCard";
            }
        }
        log.info("Dashboard: bias module '{}' → {}", name, newState ? "ON" : "OFF");
        model.addAttribute("biasSub", buildBiasSubModel());
        return "fragments/dashboard-cards :: biasModulesCard";
    }

    // ---------- Cache card ----------

    /** Warm the cache now (non-forced — respects circuit breaker). */
    @PostMapping("/cache/warm")
    public String cacheWarm(Model model) {
        BiasWarmer.WarmResult r = biasWarmer.warmNow(false);
        log.info("Dashboard: cache warm triggered — {} ok / {} failed", r.succeeded(), r.failed());
        model.addAttribute("cache", buildCacheModel());
        return "fragments/dashboard-cards :: cacheCard";
    }

    /** Clear all cache entries. */
    @PostMapping("/cache/clear")
    public String cacheClear(Model model) {
        long sizeBefore = candleCache.stats().size();
        candleCache.clear();
        log.info("Dashboard: cache cleared (dropped {} entries)", sizeBefore);
        model.addAttribute("cache", buildCacheModel());
        return "fragments/dashboard-cards :: cacheCard";
    }

    // ---------- Warmer card ----------

    /** Force a warmer round bypassing the circuit breaker. */
    @PostMapping("/warmer/force")
    public String warmerForce(Model model) {
        BiasWarmer.WarmResult r = biasWarmer.warmNow(true);
        log.info("Dashboard: warmer FORCE-triggered — {} ok / {} failed", r.succeeded(), r.failed());
        model.addAttribute("warmer", buildWarmerModel());
        return "fragments/dashboard-cards :: warmerCard";
    }

    /** Reset the circuit breaker to CLOSED. */
    @PostMapping("/warmer/reset")
    public String warmerReset(Model model) {
        biasWarmer.resetCircuit();
        log.info("Dashboard: warmer circuit breaker reset");
        model.addAttribute("warmer", buildWarmerModel());
        return "fragments/dashboard-cards :: warmerCard";
    }

    // ---------- Reports card ----------

    /** Finalize today's daily report — writes markdown to disk. */
    @PostMapping("/reports/finalize")
    public String reportsFinalize(Model model) throws IOException {
        var path = reportService.writeForToday();
        log.info("Dashboard: today's report finalized → {}", path);
        model.addAttribute("reports", buildReportsModel());
        return "fragments/dashboard-cards :: reportsCard";
    }

    // ---------- model builders (mirror DashboardController's private helpers) ----------

    private Map<String, Object> buildCacheModel() {
        CandleCache.CacheStats s = candleCache.stats();
        Map<String, Object> m = new HashMap<>();
        m.put("provider",   cacheProperties.getProvider());
        m.put("size",       s.size());
        m.put("hits",       s.hits());
        m.put("misses",     s.misses());
        m.put("hitRate",    Math.round(s.hitRate() * 1000.0) / 10.0);
        m.put("ttlSeconds", cacheProperties.getTtlSeconds());
        m.put("maxSize",    cacheProperties.getMaxSize());
        return m;
    }

    private Map<String, Object> buildWarmerModel() {
        BiasWarmer.WarmerStats s = biasWarmer.stats();
        Map<String, Object> m = new HashMap<>();
        m.put("enabled",             biasProperties.getWarmer().isEnabled());
        m.put("cron",                biasProperties.getWarmer().getCron());
        m.put("currentlyRunning",    s.currentlyRunning());
        m.put("totalRounds",         s.totalRounds());
        m.put("successfulRounds",    s.successfulRounds());
        m.put("failedRounds",        s.failedRounds());
        m.put("consecutiveFailures", s.consecutiveFailures());
        m.put("lastRoundAt",         s.lastRoundAt());
        m.put("lastRoundDurationMs", s.lastRoundDurationMs());
        m.put("avgDurationMs",       s.avgDurationMs());
        m.put("circuitState",        s.circuitState());
        return m;
    }

    private Map<String, Object> buildReportsModel() {
        Map<String, Object> m = new HashMap<>();
        m.put("enabled",          reportsProperties.isEnabled());
        m.put("outputDir",        reportsProperties.getOutputDir());
        m.put("autoWriteAtClose", reportsProperties.isAutoWriteAtClose());
        m.put("closeCron",        reportsProperties.getCloseCron());
        return m;
    }

    private Map<String, Object> buildBiasSubModel() {
        Map<String, Object> m = new HashMap<>();
        m.put("vixEnabled",        biasProperties.getVix().isEnabled());
        m.put("correlatedEnabled", biasProperties.getCorrelated().isEnabled());
        m.put("breadthEnabled",    biasProperties.getBreadth().isEnabled());
        m.put("timeframes",        biasProperties.getTimeframes());
        m.put("circuitEnabled",    biasProperties.getWarmer().getCircuit().isEnabled());
        return m;
    }
}
