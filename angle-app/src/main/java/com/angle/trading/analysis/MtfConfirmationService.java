package com.angle.trading.analysis;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.config.MtfProperties;
import com.angle.trading.indicator.ExponentialMovingAverage;
import com.angle.trading.indicator.SuperTrend;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * "Does the higher timeframe agree with my lower-TF signal?"
 *
 * Called by SignalDetector after consensus fires but before saving. Per
 * configured higher timeframe, computes the current direction (UP/DOWN)
 * using the chosen method, then applies agreementMode (ALL/MAJORITY/ANY).
 *
 * Cache: direction per (symbol|TF) is cached for {@code cacheMinutes} to avoid
 * recomputing indicators on every 5-min candle when the 1H direction rarely
 * changes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtfConfirmationService {

    private static final int LOOKBACK_CANDLES = 100;

    private final MtfProperties props;
    private final MarketDataService marketDataService;

    private Cache<String, String> directionCache;   // key = token|TF, value = UP/DOWN/UNKNOWN
    private volatile List<Interval> higherTfs;

    @PostConstruct
    void init() {
        this.directionCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(Math.max(1, props.getCacheMinutes())))
                .maximumSize(200)
                .build();
        reload();
        log.info("MtfConfirmationService initialised — enabled={} method={} higherTFs={} mode={}",
                props.isEnabled(), props.getMethod(), higherTfs, props.getAgreementMode());
    }

    /** Re-parse config CSVs. Call after admin edits. */
    public synchronized void reload() {
        this.higherTfs = Arrays.stream(props.getHigherTimeframes().split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> {
                    try { return Interval.valueOf(s); }
                    catch (Exception e) { return null; }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        this.directionCache.invalidateAll();
    }

    /**
     * The one call SignalDetector makes. Returns Decision with allowed + reason.
     * Bypasses (returns ALLOW) when disabled or when the signal's own TF is
     * higher than or equal to all configured higher-TFs (no "higher" to check).
     */
    public Decision confirm(String symbolToken, Exchange exchange, Interval signalTf, String action) {
        if (!props.isEnabled()) return Decision.ok("mtf disabled");

        String desired = "BUY".equalsIgnoreCase(action) ? "UP"
                       : "SELL".equalsIgnoreCase(action) ? "DOWN"
                       : null;
        if (desired == null) return Decision.ok("action not BUY/SELL — skip check");

        // Only check TFs STRICTLY higher than the signal's TF
        List<Interval> toCheck = higherTfs.stream()
                .filter(tf -> minsOf(tf) > minsOf(signalTf))
                .toList();
        if (toCheck.isEmpty()) return Decision.ok("no higher TFs configured for " + signalTf);

        int agree = 0;
        int checked = 0;
        List<String> report = new ArrayList<>();
        for (Interval tf : toCheck) {
            String dir = directionFor(symbolToken, exchange, tf);
            report.add(tf.name() + "=" + dir);
            if ("UNKNOWN".equals(dir)) continue;   // don't count as agree OR disagree
            checked++;
            if (dir.equals(desired)) agree++;
        }

        boolean pass = switch (props.getAgreementMode().toUpperCase()) {
            case "ALL"      -> checked > 0 && agree == checked;
            case "MAJORITY" -> checked > 0 && agree * 2 > checked;
            case "ANY"      -> agree > 0;
            default         -> checked > 0 && agree == checked;
        };
        String summary = String.join(", ", report) + " [need=" + desired
                + " mode=" + props.getAgreementMode() + " agree=" + agree + "/" + checked + "]";
        return pass ? Decision.ok(summary) : Decision.deny(summary);
    }

    /** Compute-or-cached direction for one symbol on one higher-TF. */
    public String directionFor(String symbolToken, Exchange exchange, Interval tf) {
        String key = symbolToken + "|" + tf.name();
        String cached = directionCache.getIfPresent(key);
        if (cached != null) return cached;
        String dir = computeDirection(symbolToken, exchange, tf);
        directionCache.put(key, dir);
        return dir;
    }

    private String computeDirection(String symbolToken, Exchange exchange, Interval tf) {
        List<Candle> candles;
        try {
            LocalDate to   = LocalDate.now();
            LocalDate from = to.minusDays(estimateLookbackDays(tf, LOOKBACK_CANDLES));
            candles = marketDataService.getCandles("ANGEL", exchange, symbolToken, tf, from, to);
        } catch (Exception e) {
            log.debug("MTF: candle fetch failed for {}/{}: {}", symbolToken, tf, e.getMessage());
            return "UNKNOWN";
        }
        if (candles == null || candles.size() < Math.max(props.getEmaSlow() + 5, props.getSuperTrendPeriod() + 5)) {
            return "UNKNOWN";
        }
        int start = Math.max(0, candles.size() - LOOKBACK_CANDLES);
        List<Candle> tail = candles.subList(start, candles.size());
        int last = tail.size() - 1;

        try {
            return switch (props.getMethod().toUpperCase()) {
                case "EMA_STACK"    -> emaStackDirection(tail, last);
                case "PRICE_VS_EMA" -> priceVsEmaDirection(tail, last);
                case "SUPERTREND"   -> superTrendDirection(tail, last);
                default             -> emaStackDirection(tail, last);
            };
        } catch (Exception e) {
            log.debug("MTF compute failed: {}", e.getMessage());
            return "UNKNOWN";
        }
    }

    private String emaStackDirection(List<Candle> candles, int idx) {
        List<BigDecimal> fast = new ExponentialMovingAverage(props.getEmaFast()).compute(candles);
        List<BigDecimal> slow = new ExponentialMovingAverage(props.getEmaSlow()).compute(candles);
        BigDecimal f = fast.get(idx), s = slow.get(idx);
        if (f == null || s == null) return "UNKNOWN";
        return f.compareTo(s) > 0 ? "UP" : "DOWN";
    }

    private String priceVsEmaDirection(List<Candle> candles, int idx) {
        List<BigDecimal> ema = new ExponentialMovingAverage(props.getEmaSlow()).compute(candles);
        BigDecimal e = ema.get(idx);
        if (e == null) return "UNKNOWN";
        BigDecimal close = candles.get(idx).close();
        return close.compareTo(e) > 0 ? "UP" : "DOWN";
    }

    private String superTrendDirection(List<Candle> candles, int idx) {
        List<SuperTrend.Value> st = new SuperTrend(props.getSuperTrendPeriod(), props.getSuperTrendMultiplier())
                .compute(candles);
        SuperTrend.Value v = st.get(idx);
        if (v == null) return "UNKNOWN";
        return v.bullish() ? "UP" : "DOWN";
    }

    private static long minsOf(Interval iv) {
        return switch (iv) {
            case ONE_MINUTE -> 1; case FIVE_MINUTE -> 5;
            case FIFTEEN_MINUTE -> 15; case THIRTY_MINUTE -> 30;
            case ONE_HOUR -> 60; case ONE_DAY -> 390;
        };
    }

    private static int estimateLookbackDays(Interval iv, int candles) {
        long mins = minsOf(iv) * candles;
        int days = (int) Math.ceil(mins / 390.0) * 2;
        return Math.max(days, 5);
    }

    public Snapshot snapshot() {
        return new Snapshot(
                props.isEnabled(),
                props.getMethod(),
                props.getAgreementMode(),
                higherTfs.stream().map(Enum::name).collect(Collectors.toList()),
                props.getEmaFast(),
                props.getEmaSlow()
        );
    }

    public record Decision(boolean allowed, String reason) {
        static Decision ok(String r)   { return new Decision(true,  r); }
        static Decision deny(String r) { return new Decision(false, r); }
    }

    public record Snapshot(
            boolean enabled, String method, String agreementMode,
            List<String> higherTimeframes,
            int emaFast, int emaSlow
    ) {}
}
