package com.angle.trading.analysis;

import com.angle.trading.bias.VixService;
import com.angle.trading.bias.model.VixSection;
import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.config.RegimeProperties;
import com.angle.trading.indicator.AverageDirectionalIndex;
import com.angle.trading.marketdata.MarketDataService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Determines whether current market conditions are favorable for signal firing.
 *
 * Three independent gates — each can be enabled/disabled via config:
 *   1. ADX regime      → RANGE / MIXED / TREND, filter by allowedRegimes CSV
 *   2. India VIX gate  → skip when VIX ≥ maxAllowed (choppy market)
 *   3. Time-of-day     → only fire within allowedWindows (CSV HH:mm-HH:mm)
 *
 * When {@code regime.enabled=false} everything passes — behaves like this
 * class doesn't exist. Same with individual sub-flags.
 *
 * Regime detection is cached per (symbol,interval) for 10 minutes to avoid
 * recomputing ADX on every candle close. VIX cached for 5 min in {@link VixService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegimeService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int    ADX_LOOKBACK_CANDLES = 60;

    private final RegimeProperties props;
    private final MarketDataService marketDataService;
    private final VixService vixService;
    private final AverageDirectionalIndex adxIndicator = new AverageDirectionalIndex(14);

    private Cache<String, String> regimeCache;   // key = token|interval, value = TREND/MIXED/RANGE
    private volatile Set<String> allowedRegimesSet;
    private volatile List<TimeWindow> allowedWindowsParsed;

    @PostConstruct
    void init() {
        this.regimeCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(10))
                .maximumSize(100)
                .build();
        parseConfig();
        log.info("RegimeService initialised — master={} adx={} vix={} time={} adx-regimes={} vix-max={} time-windows={}",
                props.isEnabled(),
                props.getAdx().isEnabled(),
                props.getVix().isEnabled(),
                props.getTime().isEnabled(),
                props.getAdx().getAllowedRegimes(),
                props.getVix().getMaxAllowed(),
                props.getTime().getAllowedWindows());
    }

    /** Re-parse config CSVs. Call from admin endpoint after property edit. */
    public synchronized void parseConfig() {
        this.allowedRegimesSet = Arrays.stream(props.getAdx().getAllowedRegimes().split(","))
                .map(String::trim).map(String::toUpperCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.allowedWindowsParsed = parseWindows(props.getTime().getAllowedWindows());
        this.regimeCache.invalidateAll();
    }

    /**
     * The one call SignalDetector makes: "is it safe to fire a signal for
     * this symbol right now?". Returns a Decision that includes reason so
     * we can log why signals were skipped.
     */
    public Decision allow(String symbolToken, Exchange exchange, Interval interval, List<Candle> recentCandles) {
        if (!props.isEnabled()) return Decision.ok("regime disabled");

        // Time-of-day gate — cheapest check first
        if (props.getTime().isEnabled()) {
            LocalTime now = LocalTime.now(IST);
            if (!allowedWindowsParsed.isEmpty() && allowedWindowsParsed.stream().noneMatch(w -> w.contains(now))) {
                return Decision.deny("time-of-day: " + now + " not in allowedWindows");
            }
        }

        // VIX gate — one shared value across all symbols
        if (props.getVix().isEnabled()) {
            try {
                VixSection vix = vixService.fetchLatest();
                if (vix != null && vix.value() != null) {
                    double v = vix.value().doubleValue();
                    if (v >= props.getVix().getMaxAllowed()) {
                        return Decision.deny("vix " + v + " >= " + props.getVix().getMaxAllowed());
                    }
                }
            } catch (Exception e) {
                log.debug("VIX fetch failed, skipping VIX gate: {}", e.getMessage());
            }
        }

        // ADX regime gate — per-symbol
        if (props.getAdx().isEnabled()) {
            String regime = detectRegime(symbolToken, exchange, interval, recentCandles);
            if (!allowedRegimesSet.contains(regime)) {
                return Decision.deny("regime " + regime + " not in allowed " + allowedRegimesSet);
            }
        }

        return Decision.ok("all gates passed");
    }

    /**
     * Compute or fetch cached ADX regime for one symbol/interval.
     * Uses caller-provided candles when available (SignalDetector already fetched them)
     * to avoid a duplicate MarketDataService call.
     */
    public String detectRegime(String symbolToken, Exchange exchange, Interval interval, List<Candle> candles) {
        String key = symbolToken + "|" + interval.name();
        String cached = regimeCache.getIfPresent(key);
        if (cached != null) return cached;

        List<Candle> src = candles;
        if (src == null || src.size() < ADX_LOOKBACK_CANDLES) {
            try {
                LocalDate to   = LocalDate.now(IST);
                LocalDate from = to.minusDays(10);
                src = marketDataService.getCandles("ANGEL", exchange, symbolToken, interval, from, to);
            } catch (Exception e) {
                log.debug("Regime: candle fetch failed for {}: {}", key, e.getMessage());
                return "UNKNOWN";
            }
        }
        if (src == null || src.size() < ADX_LOOKBACK_CANDLES) return "UNKNOWN";

        int start = Math.max(0, src.size() - ADX_LOOKBACK_CANDLES);
        List<Candle> tail = src.subList(start, src.size());
        List<BigDecimal> adxSeries = adxIndicator.compute(tail);
        if (adxSeries == null || adxSeries.isEmpty()) return "UNKNOWN";
        BigDecimal latest = adxSeries.get(adxSeries.size() - 1);
        if (latest == null) return "UNKNOWN";

        double adx = latest.doubleValue();
        String regime;
        if (adx < props.getAdx().getWeakThreshold())         regime = "RANGE";
        else if (adx < props.getAdx().getStrongThreshold())  regime = "MIXED";
        else                                                  regime = "TREND";

        regimeCache.put(key, regime);
        return regime;
    }

    /** Snapshot of current state for the admin/debug endpoint. */
    public Snapshot snapshot() {
        VixSection vix = null;
        try { vix = vixService.fetchLatest(); } catch (Exception ignored) { }
        return new Snapshot(
                props.isEnabled(),
                props.getAdx().isEnabled(),
                props.getVix().isEnabled(),
                props.getTime().isEnabled(),
                LocalTime.now(IST).toString(),
                allowedRegimesSet,
                allowedWindowsParsed.stream().map(TimeWindow::toString).toList(),
                vix == null ? null : vix.value(),
                props.getVix().getMaxAllowed()
        );
    }

    // ---------- helpers ----------

    private static List<TimeWindow> parseWindows(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(TimeWindow::parse)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    // ---------- inner types ----------

    public record Decision(boolean allowed, String reason) {
        static Decision ok(String r)   { return new Decision(true,  r); }
        static Decision deny(String r) { return new Decision(false, r); }
    }

    public record Snapshot(
            boolean masterEnabled, boolean adxEnabled, boolean vixEnabled, boolean timeEnabled,
            String  nowIST,
            Set<String> allowedRegimes, List<String> allowedWindows,
            BigDecimal currentVix, double vixMaxAllowed
    ) {}

    private record TimeWindow(LocalTime start, LocalTime end) {
        static TimeWindow parse(String range) {
            try {
                String[] p = range.split("-");
                if (p.length != 2) return null;
                return new TimeWindow(LocalTime.parse(p[0].trim()), LocalTime.parse(p[1].trim()));
            } catch (Exception e) { return null; }
        }
        boolean contains(LocalTime t) {
            return !t.isBefore(start) && t.isBefore(end);
        }
        @Override public String toString() { return start + "-" + end; }
    }
}
