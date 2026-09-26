package com.angle.trading.broker.angel.stream;

import com.angle.trading.broker.angel.stream.model.Tick;
import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.config.BrokerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aggregates live ticks into candles across multiple intervals in parallel.
 *
 * For each configured interval (e.g. 1M, 5M, 15M), maintains one "current
 * bar" per (exchange, token). On every tick, updates the current bar's OHLC.
 * A scheduled sweep emits {@link CandleClosedEvent} for any bar whose
 * end-time has elapsed, then removes it — the next tick starts a new bar.
 *
 * Volume is null in Mode 1 (LTP) because Angel doesn't send per-tick volume.
 * Upgrade to Mode 2/3 to populate it.
 *
 * Bar-start alignment uses UTC-truncation. 5-min bars align at :00, :05, :10 …
 * — matches Angel REST candles' timestamps so consumers can treat live and
 * historical bars identically.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CandleAggregator {

    private final BrokerProperties brokerProperties;
    private final ApplicationEventPublisher events;

    /** Key: (exchange:token:interval) → current bar being built. */
    private final Map<String, BarBuilder> current = new ConcurrentHashMap<>();

    /** Parsed intervals from config. Populated lazily on first tick. */
    private volatile List<Interval> intervals;

    // ---------- ingest ----------

    @EventListener
    public void onTick(TickEvent e) {
        if (!isEnabled()) return;
        Tick t = e.tick();
        for (Interval iv : intervals()) {
            String key = key(t.exchangeCode(), t.symbolToken(), iv);
            Instant barStart = barStart(t.exchangeTime(), iv);
            current.compute(key, (k, existing) -> {
                if (existing == null || !existing.barStart.equals(barStart)) {
                    // Old bar (if any) already got picked up by sweep, or missed —
                    // start a fresh bar for this new bucket.
                    return new BarBuilder(t.exchangeCode(), t.symbolToken(), iv, barStart, t.ltp());
                }
                existing.update(t.ltp());
                return existing;
            });
        }
    }

    // ---------- sweep ----------

    /** Runs every {@code sweepSeconds} — emits closed bars, drops them from state. */
    @Scheduled(fixedRateString = "#{@brokerProperties.angel.stream.aggregate.sweepSeconds * 1000}")
    public void sweep() {
        if (!isEnabled() || current.isEmpty()) return;
        Instant now = Instant.now();
        List<String> toRemove = new ArrayList<>();
        current.forEach((k, b) -> {
            Instant barEnd = b.barStart.plus(intervalDuration(b.interval));
            if (!now.isBefore(barEnd)) {
                emit(b);
                toRemove.add(k);
            }
        });
        toRemove.forEach(current::remove);
    }

    private void emit(BarBuilder b) {
        Candle c = new Candle(b.barStart, b.open, b.high, b.low, b.close, 0L);
        events.publishEvent(new CandleClosedEvent(b.exchangeCode, b.symbolToken, b.interval, c));
        log.debug("Candle closed: {}:{} {} @ {}  O={} H={} L={} C={}",
                b.exchangeCode, b.symbolToken, b.interval, b.barStart, b.open, b.high, b.low, b.close);
    }

    // ---------- helpers ----------

    private boolean isEnabled() {
        return brokerProperties.getAngel().getStream().isEnabled()
                && brokerProperties.getAngel().getStream().getAggregate().isEnabled();
    }

    private List<Interval> intervals() {
        List<Interval> cached = intervals;
        if (cached != null) return cached;
        String csv = brokerProperties.getAngel().getStream().getAggregate().getIntervals();
        List<Interval> parsed = Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try { return Interval.valueOf(s); }
                    catch (Exception e) {
                        log.warn("Unknown interval '{}' in aggregate.intervals — skipping", s);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
        intervals = parsed;
        log.info("CandleAggregator configured for intervals: {}", parsed);
        return parsed;
    }

    private static String key(int exchange, String token, Interval iv) {
        return exchange + ":" + token + ":" + iv.name();
    }

    /**
     * Align a timestamp to the start of its interval bucket.
     * For 5-min: 09:17:42 → 09:15:00. For 1-hour: 09:17:42 → 09:00:00.
     * For 1-day: 2026-09-26T09:17:42 → 2026-09-26T00:00:00 UTC (see caveat).
     * Uses UTC boundaries — Angel's own REST candles do the same, so a live
     * bar and a REST-fetched bar end up with identical `ts` for the same
     * clock-instant.
     */
    static Instant barStart(Instant ts, Interval iv) {
        long durationSec = intervalDuration(iv).getSeconds();
        long epochSec = ts.getEpochSecond();
        long floored = (epochSec / durationSec) * durationSec;
        return Instant.ofEpochSecond(floored);
    }

    private static Duration intervalDuration(Interval iv) {
        return switch (iv) {
            case ONE_MINUTE       -> Duration.ofMinutes(1);
            case FIVE_MINUTE      -> Duration.ofMinutes(5);
            case FIFTEEN_MINUTE   -> Duration.ofMinutes(15);
            case THIRTY_MINUTE    -> Duration.ofMinutes(30);
            case ONE_HOUR         -> Duration.ofHours(1);
            case ONE_DAY          -> Duration.ofDays(1);
            default -> throw new IllegalArgumentException("Unsupported interval: " + iv);
        };
    }

    /** Mutable per-bar state kept in the {@link #current} map. */
    private static final class BarBuilder {
        final int      exchangeCode;
        final String   symbolToken;
        final Interval interval;
        final Instant  barStart;
        BigDecimal open;
        BigDecimal high;
        BigDecimal low;
        BigDecimal close;

        BarBuilder(int exchangeCode, String symbolToken, Interval interval, Instant barStart, BigDecimal firstPrice) {
            this.exchangeCode = exchangeCode;
            this.symbolToken  = symbolToken;
            this.interval     = interval;
            this.barStart     = barStart;
            this.open  = firstPrice;
            this.high  = firstPrice;
            this.low   = firstPrice;
            this.close = firstPrice;
        }

        void update(BigDecimal price) {
            if (price.compareTo(high) > 0) high = price;
            if (price.compareTo(low)  < 0) low  = price;
            close = price;
        }
    }
}
