package com.angle.trading.paper.source;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.marketdata.MarketDataService;
import lombok.extern.slf4j.Slf4j;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Polls Angel SmartAPI for the latest candles on a specific instrument and
 * pushes them into a paper-trading session.
 *
 *   1. On start, does a warmup pass — fetches the last N historical candles
 *      so the strategy has enough data to compute (SMAs, RSI, MACD, SMC).
 *   2. Then polls every {@code pollIntervalSeconds} seconds. New candles
 *      whose interval has already ENDED are emitted (partial in-progress
 *      bars are skipped so the strategy never sees mid-bar data).
 *   3. De-duplicates by timestamp — the same candle is only ever emitted once.
 *
 * Never calls onComplete: a live feed has no natural end. The session
 * stops via {@code stop()} (user action) or when the app shuts down.
 *
 * Market-hours awareness: none. Off-hours polling is cheap (Angel returns
 * yesterday's candles and dedup skips them). Add a market-hours gate later
 * if quota becomes a concern.
 */
@Slf4j
public class AngelLiveCandleSource implements CandleSource {

    private static final String BROKER = "ANGEL";
    private static final int WARMUP_DAYS_INTRADAY = 7;
    private static final int WARMUP_DAYS_HOURLY   = 60;
    private static final int WARMUP_DAYS_DAILY    = 400;

    // Trading hours in IST, per exchange segment
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /** Per-exchange trading window. Holidays not modelled — poll just returns empty on holidays. */
    private record TradingHours(LocalTime open, LocalTime close) {}
    private static final TradingHours HOURS_EQUITY    = new TradingHours(LocalTime.of(9, 15), LocalTime.of(15, 30));  // NSE, BSE, NFO, BFO
    private static final TradingHours HOURS_COMMODITY = new TradingHours(LocalTime.of(9,  0), LocalTime.of(23, 30));  // MCX non-agri (crude, gold, silver)
    private static final TradingHours HOURS_CURRENCY  = new TradingHours(LocalTime.of(9,  0), LocalTime.of(17,  0));  // CDS

    private final MarketDataService marketDataService;
    private final Exchange exchange;
    private final String symbolToken;
    private final Interval interval;
    private final int warmupCandles;
    private final int pollIntervalSeconds;

    private ScheduledExecutorService executor;
    private volatile boolean running;
    private volatile Instant lastEmittedTimestamp;

    public AngelLiveCandleSource(MarketDataService marketDataService,
                                 Exchange exchange, String symbolToken,
                                 Interval interval, int warmupCandles,
                                 int pollIntervalSeconds) {
        if (warmupCandles < 0) throw new IllegalArgumentException("warmupCandles must be >= 0");
        if (pollIntervalSeconds <= 0) throw new IllegalArgumentException("pollIntervalSeconds must be > 0");
        this.marketDataService   = marketDataService;
        this.exchange            = exchange;
        this.symbolToken         = symbolToken;
        this.interval            = interval;
        this.warmupCandles       = warmupCandles;
        this.pollIntervalSeconds = pollIntervalSeconds;
    }

    @Override
    public String name() {
        return String.format("angel-live(%s:%s,%s,poll=%ds)",
                exchange, symbolToken, interval, pollIntervalSeconds);
    }

    @Override
    public synchronized void start(Consumer<Candle> onCandle, Runnable onComplete) {
        if (running) return;
        running = true;

        // 1. Warmup — pre-load history so strategies have enough data to compute.
        try {
            warmup(onCandle);
        } catch (Exception e) {
            log.warn("Angel live warmup failed (session continues, first poll will retry): {}", e.getMessage());
        }

        // 2. Schedule the poller.
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "paper-angel-live");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(() -> pollOnce(onCandle),
                pollIntervalSeconds, pollIntervalSeconds, TimeUnit.SECONDS);

        log.info("Angel live source started: exchange={}, token={}, interval={}, warmup={}, pollSec={}",
                exchange, symbolToken, interval, warmupCandles, pollIntervalSeconds);
    }

    @Override
    public synchronized void stop() {
        if (!running) return;
        running = false;
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
        log.debug("Angel live source stopped");
    }

    private void warmup(Consumer<Candle> onCandle) {
        LocalDate to   = LocalDate.now();
        LocalDate from = to.minusDays(warmupDaysFor(interval));

        List<Candle> raw = marketDataService.getCandles(BROKER, exchange, symbolToken, interval, from, to);
        if (raw.isEmpty()) {
            log.warn("Warmup returned no candles for {}:{}", exchange, symbolToken);
            return;
        }
        // Keep only the trailing warmupCandles entries.
        int start = Math.max(0, raw.size() - warmupCandles);
        List<Candle> warmup = raw.subList(start, raw.size());

        Instant now = Instant.now();
        Duration bar = barDuration(interval);
        int emitted = 0;
        for (Candle c : warmup) {
            if (c.timestamp().plus(bar).isAfter(now)) continue; // skip currently-forming bar
            onCandle.accept(c);
            lastEmittedTimestamp = c.timestamp();
            emitted++;
        }
        log.info("Warmup emitted {} candles (fetched {}, most recent {})",
                emitted, raw.size(), lastEmittedTimestamp);
    }

    private void pollOnce(Consumer<Candle> onCandle) {
        if (!running) return;
        TradingHours hours = hoursFor(exchange);
        if (!isWithin(hours)) {
            log.debug("Market closed for {} (IST outside {}-{} or weekend) — skipping poll",
                    exchange, hours.open(), hours.close());
            return;
        }
        try {
            LocalDate to   = LocalDate.now();
            LocalDate from = to.minusDays(1);   // last 24h is plenty to catch any new bars

            List<Candle> latest = marketDataService.getCandles(BROKER, exchange, symbolToken, interval, from, to);
            Instant now = Instant.now();
            Duration bar = barDuration(interval);
            int emitted = 0;

            for (Candle c : latest) {
                if (lastEmittedTimestamp != null && !c.timestamp().isAfter(lastEmittedTimestamp)) continue;
                if (c.timestamp().plus(bar).isAfter(now)) continue; // still forming
                onCandle.accept(c);
                lastEmittedTimestamp = c.timestamp();
                emitted++;
            }
            if (emitted > 0) {
                log.debug("Live poll emitted {} new candle(s), latest {}", emitted, lastEmittedTimestamp);
            }
        } catch (Exception e) {
            log.warn("Live poll failed (will retry in {}s): {}", pollIntervalSeconds, e.getMessage());
        }
    }

    /** Weekday + time-in-window check against the given hours. */
    private static boolean isWithin(TradingHours hours) {
        ZonedDateTime now = ZonedDateTime.now(IST);
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) return false;
        LocalTime t = now.toLocalTime();
        return !t.isBefore(hours.open()) && !t.isAfter(hours.close());
    }

    private static TradingHours hoursFor(Exchange exchange) {
        return switch (exchange) {
            case NSE, BSE, NFO, BFO -> HOURS_EQUITY;
            case MCX                -> HOURS_COMMODITY;
            case CDS                -> HOURS_CURRENCY;
        };
    }

    private static int warmupDaysFor(Interval interval) {
        return switch (interval) {
            case ONE_MINUTE, FIVE_MINUTE, FIFTEEN_MINUTE, THIRTY_MINUTE -> WARMUP_DAYS_INTRADAY;
            case ONE_HOUR -> WARMUP_DAYS_HOURLY;
            case ONE_DAY  -> WARMUP_DAYS_DAILY;
        };
    }

    private static Duration barDuration(Interval interval) {
        return switch (interval) {
            case ONE_MINUTE     -> Duration.ofMinutes(1);
            case FIVE_MINUTE    -> Duration.ofMinutes(5);
            case FIFTEEN_MINUTE -> Duration.ofMinutes(15);
            case THIRTY_MINUTE  -> Duration.ofMinutes(30);
            case ONE_HOUR       -> Duration.ofHours(1);
            case ONE_DAY        -> Duration.ofDays(1);
        };
    }
}
