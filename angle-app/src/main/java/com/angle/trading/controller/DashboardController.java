package com.angle.trading.controller;

import com.angle.trading.bias.BiasChangeHistory;
import com.angle.trading.bias.BiasWarmer;
import com.angle.trading.bias.MarketCalendar;
import com.angle.trading.cache.CandleCache;
import com.angle.trading.cache.CandleCacheProperties;
import com.angle.trading.cache.CandleStore;
import com.angle.trading.config.AlertsProperties;
import com.angle.trading.config.BiasProperties;
import com.angle.trading.config.BrokerProperties;
import com.angle.trading.config.CalendarProperties;
import com.angle.trading.config.PaperAutostartProperties;
import com.angle.trading.config.ReportsProperties;
import com.angle.trading.config.TradingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * User dashboard + system status overview.
 *
 * Gathers a live snapshot of every important subsystem — app health,
 * broker connection, cache stats, warmer state, market calendar, trading
 * config, database size — and renders it into dashboard/welcome.html.
 *
 * All data read from existing bean state — no extra work, no side effects.
 * Safe to auto-refresh on the client every 30 seconds.
 *
 * Works whether security is on or off:
 *   - Security ON  → authentication is populated by Spring, we use its name.
 *   - Security OFF (nosec profile) → authentication is null, we fall back
 *     to "anonymous" so the page still renders.
 */
@Controller
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final BiasProperties biasProperties;
    private final BrokerProperties brokerProperties;
    private final TradingProperties tradingProperties;
    private final CandleCacheProperties cacheProperties;
    private final CandleCache candleCache;
    private final CandleStore candleStore;
    private final BiasWarmer biasWarmer;
    private final MarketCalendar marketCalendar;
    private final AlertsProperties alertsProperties;
    private final ReportsProperties reportsProperties;
    private final PaperAutostartProperties paperProperties;
    private final CalendarProperties calendarProperties;
    private final BiasChangeHistory changeHistory;

    @GetMapping
    public String home(Model model, Authentication authentication) {
        String user = (authentication != null) ? authentication.getName() : "anonymous";
        LocalDateTime sessionStart = LocalDateTime.now().withNano(0);
        log.info("User '{}' opened dashboard at {}", user, sessionStart);

        model.addAttribute("user", user);
        model.addAttribute("sessionStart", sessionStart);
        model.addAttribute("generatedAt", LocalDateTime.now().withNano(0));

        model.addAttribute("app",     buildAppStatus());
        model.addAttribute("broker",  buildBrokerStatus());
        model.addAttribute("cache",   buildCacheStatus());
        model.addAttribute("warmer",  buildWarmerStatus());
        model.addAttribute("market",  buildMarketStatus());
        model.addAttribute("config",  buildConfigStatus());
        model.addAttribute("trading", buildTradingStatus());
        model.addAttribute("db",      buildDbStatus());
        model.addAttribute("alerts",  buildAlertsStatus());
        model.addAttribute("biasSub", buildBiasSubStatus());
        model.addAttribute("reports", buildReportsStatus());
        model.addAttribute("paper",   buildPaperStatus());
        model.addAttribute("instruments", biasProperties.getInstruments());
        model.addAttribute("recentChanges", changeHistory.recent());
        model.addAttribute("systemAlerts",  buildSystemAlerts());
        model.addAttribute("healthCounts",  buildHealthCounts(model));

        return "dashboard/welcome";
    }

    // ---------- data collectors ----------

    private Map<String, Object> buildAppStatus() {
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMB  = rt.maxMemory() / (1024 * 1024);
        int usedPct = maxMB == 0 ? 0 : (int) (usedMB * 100 / maxMB);
        Map<String, Object> m = new HashMap<>();
        m.put("uptime",       formatDuration(Duration.ofMillis(uptimeMs)));
        m.put("heapUsedMB",   usedMB);
        m.put("heapMaxMB",    maxMB);
        m.put("heapPercent",  usedPct);
        m.put("healthy",      usedPct < 85);   // simple heuristic
        return m;
    }

    private Map<String, Object> buildBrokerStatus() {
        BrokerProperties.Angel a = brokerProperties.getAngel();
        Map<String, Object> m = new HashMap<>();
        m.put("angelEnabled", a.isEnabled());
        m.put("clientCode",   a.getClientCode());
        m.put("upstoxEnabled", brokerProperties.getUpstox().isEnabled());
        m.put("kiteEnabled",   brokerProperties.getKite().isEnabled());
        return m;
    }

    private Map<String, Object> buildCacheStatus() {
        CandleCache.CacheStats s = candleCache.stats();
        Map<String, Object> m = new HashMap<>();
        m.put("provider",  cacheProperties.getProvider());
        m.put("size",      s.size());
        m.put("hits",      s.hits());
        m.put("misses",    s.misses());
        m.put("hitRate",   Math.round(s.hitRate() * 1000.0) / 10.0);
        m.put("ttlSeconds", cacheProperties.getTtlSeconds());
        m.put("maxSize",   cacheProperties.getMaxSize());
        return m;
    }

    private Map<String, Object> buildWarmerStatus() {
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

    private Map<String, Object> buildMarketStatus() {
        LocalDate today = LocalDate.now();
        boolean open = marketCalendar.isMarketOpen(today);
        Map<String, Object> m = new HashMap<>();
        m.put("openToday",      open);
        m.put("closedReason",   marketCalendar.closedReason(today));
        m.put("instrumentCount", biasProperties.getInstruments().size());
        m.put("holidayCount",   marketCalendar.holidayCount());
        m.put("calendarEnabled", calendarProperties.isEnabled());
        return m;
    }

    private Map<String, Object> buildAlertsStatus() {
        Map<String, Object> m = new HashMap<>();
        m.put("masterEnabled",    alertsProperties.isEnabled());
        m.put("telegramEnabled",  alertsProperties.getTelegram().isEnabled());
        m.put("whatsappEnabled",  alertsProperties.getWhatsapp().isEnabled());
        m.put("onTradeOpen",      alertsProperties.isOnTradeOpen());
        m.put("onTradeClose",     alertsProperties.isOnTradeClose());
        m.put("onSessionEnd",     alertsProperties.isOnSessionEnd());
        m.put("onErrors",         alertsProperties.isOnErrors());
        return m;
    }

    private Map<String, Object> buildBiasSubStatus() {
        Map<String, Object> m = new HashMap<>();
        m.put("vixEnabled",        biasProperties.getVix().isEnabled());
        m.put("correlatedEnabled", biasProperties.getCorrelated().isEnabled());
        m.put("breadthEnabled",    biasProperties.getBreadth().isEnabled());
        m.put("timeframes",        biasProperties.getTimeframes());
        m.put("circuitEnabled",    biasProperties.getWarmer().getCircuit().isEnabled());
        return m;
    }

    private Map<String, Object> buildReportsStatus() {
        Map<String, Object> m = new HashMap<>();
        m.put("enabled",          reportsProperties.isEnabled());
        m.put("outputDir",        reportsProperties.getOutputDir());
        m.put("autoWriteAtClose", reportsProperties.isAutoWriteAtClose());
        m.put("closeCron",        reportsProperties.getCloseCron());
        return m;
    }

    private Map<String, Object> buildPaperStatus() {
        Map<String, Object> m = new HashMap<>();
        m.put("autostartEnabled", paperProperties.isEnabled());
        m.put("sessionCount",     paperProperties.getSessions() == null ? 0 : paperProperties.getSessions().size());
        return m;
    }

    /**
     * Derive live "action-needed" alerts from current state:
     *   circuit OPEN, DB unreachable, broker down, market closed, warmer disabled etc.
     * Rendered in the "Needs a decision" card.
     */
    private java.util.List<Map<String, Object>> buildSystemAlerts() {
        java.util.List<Map<String, Object>> out = new java.util.ArrayList<>();
        BiasWarmer.WarmerStats w = biasWarmer.stats();

        if ("OPEN".equals(w.circuitState())) {
            out.add(alert("bad", "Warmer circuit OPEN",
                    "Consecutive Angel failures tripped the breaker. Auto-recovers after cooldown or reset manually.",
                    "now"));
        }
        if (w.consecutiveFailures() >= 2) {
            out.add(alert("warn", w.consecutiveFailures() + " consecutive warmer failures",
                    "Angel returning errors. Circuit will trip if it continues.",
                    "recent"));
        }
        if (!brokerProperties.getAngel().isEnabled()) {
            out.add(alert("bad", "Angel broker disabled",
                    "broker.angel.enabled=false — no live data will flow into the bias engine.",
                    "config"));
        }
        if (!biasProperties.isEnabled()) {
            out.add(alert("warn", "Bias engine disabled",
                    "bias.enabled=false — /bias page will not render. Change detector also skips.",
                    "config"));
        }
        if (!marketCalendar.isMarketOpen(LocalDate.now())) {
            out.add(alert("info", "Market closed today",
                    "Reason: " + marketCalendar.closedReason(LocalDate.now())
                            + ". Warmer and change detector skipping.",
                    "today"));
        }
        if (candleStore.count() < 0) {
            out.add(alert("bad", "MySQL candle_history unreachable",
                    "Cannot read from persistent candle store. Live queries fall back to Angel.",
                    "now"));
        }
        if (!alertsProperties.isEnabled()) {
            out.add(alert("info", "Alerts master switch OFF",
                    "Bias changes will be logged but no Telegram/WhatsApp notification will fire.",
                    "config"));
        }
        // Happy state — always show a positive if no other alerts.
        if (out.isEmpty()) {
            out.add(alert("ok", "All systems green",
                    "Warmer healthy, broker connected, cache warm. Nothing needs attention.",
                    "now"));
        }
        return out;
    }

    private static Map<String, Object> alert(String kind, String title, String detail, String when) {
        Map<String, Object> m = new HashMap<>();
        m.put("kind", kind);      // ok | info | warn | bad
        m.put("title", title);
        m.put("detail", detail);
        m.put("when", when);
        return m;
    }

    /**
     * Tally green/yellow/red status across all subsystem cards.
     * Feeds the 4 KPI cards + donut at the top of the Landmark dashboard.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildHealthCounts(Model model) {
        BiasWarmer.WarmerStats w = biasWarmer.stats();
        long dbCount = candleStore.count();

        int total = 12, healthy = 0, warning = 0, critical = 0;

        // App
        Map<String, Object> app = (Map<String, Object>) model.getAttribute("app");
        if (app != null && Boolean.TRUE.equals(app.get("healthy"))) healthy++; else warning++;
        // Broker
        if (brokerProperties.getAngel().isEnabled()) healthy++; else critical++;
        // Cache — always healthy (has a working provider by definition)
        healthy++;
        // Warmer
        if ("OPEN".equals(w.circuitState())) critical++;
        else if (w.consecutiveFailures() > 0) warning++;
        else healthy++;
        // Market — informational, always "healthy" (closed is not a problem)
        healthy++;
        // Bias config
        if (biasProperties.isEnabled()) healthy++; else warning++;
        // Trading
        healthy++;
        // DB
        if (dbCount >= 0) healthy++; else critical++;
        // Alerts
        if (alertsProperties.isEnabled()) healthy++; else warning++;
        // Modules — VIX + Correlated + Breadth all enabled?
        if (biasProperties.getVix().isEnabled()
                && biasProperties.getCorrelated().isEnabled()
                && biasProperties.getBreadth().isEnabled()) healthy++; else warning++;
        // Reports
        if (reportsProperties.isEnabled()) healthy++; else warning++;
        // Paper autostart — off is normal for personal use
        healthy++;

        Map<String, Object> out = new HashMap<>();
        out.put("total",    total);
        out.put("healthy",  healthy);
        out.put("warning",  warning);
        out.put("critical", critical);
        return out;
    }

    private Map<String, Object> buildConfigStatus() {
        Map<String, Object> m = new HashMap<>();
        m.put("biasEnabled",         biasProperties.isEnabled());
        m.put("refreshMinutes",      biasProperties.getRefreshMinutes());
        m.put("lookbackDays",        biasProperties.getLookbackDays());
        m.put("changeAlertsEnabled", biasProperties.getChangeAlerts().isEnabled());
        return m;
    }

    private Map<String, Object> buildTradingStatus() {
        long capital = tradingProperties.getCapital();
        double riskPct = tradingProperties.getRiskPercent();
        long maxRisk = Math.round(capital * riskPct / 100.0);
        Map<String, Object> m = new HashMap<>();
        m.put("capital",       capital);
        m.put("riskPercent",   riskPct);
        m.put("maxRiskPerTrade", maxRisk);
        return m;
    }

    private Map<String, Object> buildDbStatus() {
        Map<String, Object> m = new HashMap<>();
        try {
            long count = candleStore.count();
            m.put("candleRowCount", count);
            m.put("available", count >= 0);
        } catch (Exception e) {
            m.put("candleRowCount", -1);
            m.put("available", false);
            m.put("error", e.getMessage());
        }
        m.put("retentionEnabled", cacheProperties.getMysql().isRetentionEnabled());
        m.put("retentionDays",    cacheProperties.getMysql().getRetentionDays());
        return m;
    }

    /** Convert Duration to a compact "2h 34min" style string. */
    private static String formatDuration(Duration d) {
        long h = d.toHours();
        long m = d.toMinutesPart();
        long s = d.toSecondsPart();
        if (h > 0) return h + "h " + m + "min";
        if (m > 0) return m + "min " + s + "s";
        return s + "s";
    }
}
