package com.angle.trading.service;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.config.TrailingProperties;
import com.angle.trading.indicator.AverageTrueRange;
import com.angle.trading.marketdata.MarketDataService;
import com.angle.trading.persistence.SignalEntity;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pure computation of "given a signal + current LTP, what's the new stop?".
 *
 * Called from {@link LiveSignalMonitor} on every tick. Returns null when no
 * change is warranted (haven't hit activation, or the new stop would be
 * WORSE than the current — trailing only tightens, never loosens).
 *
 * The service is stateful only for its ATR cache (per token/interval, 5-min TTL).
 * All signal state lives on {@link SignalEntity} — highWaterMark and stop.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrailingStopService {

    private final TrailingProperties props;
    private final MarketDataService marketDataService;

    private Cache<String, BigDecimal> atrCache;
    private final AverageTrueRange atrIndicator = new AverageTrueRange(14);

    @PostConstruct
    void init() {
        this.atrCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(200)
                .build();
        log.info("TrailingStopService initialised — enabled={} mode={} activationPct={} pctDistance={}",
                props.isEnabled(), props.getMode(),
                props.getActivationPercent(), props.getPercentDistance());
    }

    /** True when trailing is off or set to NONE. Caller can skip all work. */
    public boolean isDisabled() {
        return !props.isEnabled() || "NONE".equalsIgnoreCase(props.getMode());
    }

    /**
     * Compute the new stop for this signal given the latest LTP.
     * Also updates highWaterMark on the entity in-place (caller must persist it).
     *
     * @return new stop if it should be updated (strictly better than current), else null
     */
    public BigDecimal computeNewStop(SignalEntity s, BigDecimal ltp) {
        if (isDisabled() || ltp == null || s == null) return null;
        if (!"OPEN".equals(s.getStatus())) return null;
        if (s.getEntry() == null || s.getTarget() == null || s.getStop() == null) return null;

        boolean isBuy = "BUY".equalsIgnoreCase(s.getAction());

        // Track high-water mark (peak for BUY, trough for SELL)
        BigDecimal hwm = s.getHighWaterMark();
        if (hwm == null) hwm = s.getEntry();
        BigDecimal newHwm = isBuy ? ltp.max(hwm) : ltp.min(hwm);
        s.setHighWaterMark(newHwm);

        // Activation check — has price moved far enough into the trade?
        BigDecimal totalMove = s.getTarget().subtract(s.getEntry()).abs();
        if (totalMove.signum() == 0) return null;
        BigDecimal currentMove = isBuy
                ? newHwm.subtract(s.getEntry())
                : s.getEntry().subtract(newHwm);
        if (currentMove.signum() <= 0) return null;
        double progressPct = currentMove.doubleValue() / totalMove.doubleValue();
        if (progressPct < props.getActivationPercent()) return null;

        // Compute candidate stop per mode
        BigDecimal candidate = switch (props.getMode().toUpperCase()) {
            case "FIXED"     -> trailFixed(newHwm, isBuy);
            case "PERCENT"   -> trailPercent(newHwm, isBuy);
            case "MILESTONE" -> trailMilestone(s, progressPct, isBuy);
            case "ATR"       -> trailAtr(s, newHwm, isBuy);
            default          -> null;
        };
        if (candidate == null) return null;

        // Ratchet — new stop must be BETTER than current
        BigDecimal current = s.getStop();
        boolean better = isBuy
                ? candidate.compareTo(current) > 0
                : candidate.compareTo(current) < 0;
        if (!better) return null;

        // Never trail PAST the current price (would trigger immediate close)
        if (isBuy && candidate.compareTo(ltp) >= 0) return null;
        if (!isBuy && candidate.compareTo(ltp) <= 0) return null;

        return candidate.setScale(4, RoundingMode.HALF_UP);
    }

    // ---------- modes ----------

    private BigDecimal trailFixed(BigDecimal hwm, boolean isBuy) {
        BigDecimal d = BigDecimal.valueOf(props.getFixedDistance());
        return isBuy ? hwm.subtract(d) : hwm.add(d);
    }

    private BigDecimal trailPercent(BigDecimal hwm, boolean isBuy) {
        BigDecimal pct = BigDecimal.valueOf(props.getPercentDistance() / 100.0);
        BigDecimal d = hwm.multiply(pct);
        return isBuy ? hwm.subtract(d) : hwm.add(d);
    }

    /**
     * Discrete ladder. For each rung "moveAt:stopAt" in the config, if
     * progressPct ≥ moveAt/100, propose stop at (entry + stopAt/100 × range).
     * Returns the highest rung reached.
     */
    private BigDecimal trailMilestone(SignalEntity s, double progressPct, boolean isBuy) {
        BigDecimal range = s.getTarget().subtract(s.getEntry()).abs();
        List<double[]> ladder = parseLadder(props.getMilestoneLadder());
        BigDecimal best = null;
        for (double[] rung : ladder) {
            double moveAt = rung[0] / 100.0;
            double stopAt = rung[1] / 100.0;
            if (progressPct < moveAt) continue;
            BigDecimal offset = range.multiply(BigDecimal.valueOf(stopAt));
            BigDecimal candidate = isBuy ? s.getEntry().add(offset) : s.getEntry().subtract(offset);
            if (best == null
                    || (isBuy && candidate.compareTo(best) > 0)
                    || (!isBuy && candidate.compareTo(best) < 0)) {
                best = candidate;
            }
        }
        return best;
    }

    private BigDecimal trailAtr(SignalEntity s, BigDecimal hwm, boolean isBuy) {
        BigDecimal atr = latestAtr(s);
        if (atr == null || atr.signum() <= 0) return null;
        BigDecimal d = atr.multiply(BigDecimal.valueOf(props.getAtrMultiplier()));
        return isBuy ? hwm.subtract(d) : hwm.add(d);
    }

    /**
     * Fetch cached ATR for this signal's instrument+interval. If cache miss,
     * pulls last ~60 candles via MarketDataService (already cached upstream).
     */
    private BigDecimal latestAtr(SignalEntity s) {
        String key = s.getSymbolToken() + "|" + s.getIntervalType();
        BigDecimal cached = atrCache.getIfPresent(key);
        if (cached != null) return cached;
        try {
            Exchange ex = Exchange.valueOf(s.getExchange());
            Interval iv = Interval.valueOf(s.getIntervalType());
            LocalDate to   = LocalDate.now();
            LocalDate from = to.minusDays(10);
            List<Candle> candles = marketDataService.getCandles("ANGEL", ex, s.getSymbolToken(), iv, from, to);
            if (candles == null || candles.size() < props.getAtrPeriod() + 2) return null;
            int start = Math.max(0, candles.size() - 60);
            List<Candle> tail = candles.subList(start, candles.size());
            List<BigDecimal> series = atrIndicator.compute(tail);
            if (series == null || series.isEmpty()) return null;
            BigDecimal latest = series.get(series.size() - 1);
            if (latest != null) atrCache.put(key, latest);
            return latest;
        } catch (Exception e) {
            log.debug("ATR fetch failed for {}: {}", key, e.getMessage());
            return null;
        }
    }

    private static List<double[]> parseLadder(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        List<double[]> out = new ArrayList<>();
        for (String pair : csv.split(",")) {
            String[] kv = pair.trim().split(":");
            if (kv.length != 2) continue;
            try {
                out.add(new double[]{Double.parseDouble(kv[0].trim()), Double.parseDouble(kv[1].trim())});
            } catch (NumberFormatException ignored) { /* skip bad rung */ }
        }
        return out;
    }

    // ---------- introspection (for admin UI) ----------

    public Snapshot snapshot() {
        return new Snapshot(
                props.isEnabled(),
                props.getMode(),
                props.getActivationPercent(),
                props.getFixedDistance(),
                props.getPercentDistance(),
                props.getAtrMultiplier(),
                Arrays.asList(props.getMilestoneLadder().split(","))
        );
    }

    public record Snapshot(
            boolean enabled, String mode, double activationPercent,
            double fixedDistance, double percentDistance, double atrMultiplier,
            List<String> milestoneLadder
    ) {}
}
