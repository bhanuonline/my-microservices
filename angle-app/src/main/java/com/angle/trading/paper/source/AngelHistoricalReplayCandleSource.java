package com.angle.trading.paper.source;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.marketdata.MarketDataService;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Fetches historical candles from Angel for a specific date range and
 * replays them at a configurable speed — as if they were live.
 *
 * Difference from {@link HistoricalReplayCandleSource}:
 *   - That one replays the bundled Nifty CSV
 *   - This one fetches ANY instrument's history from Angel
 *
 * Difference from {@link AngelLiveCandleSource}:
 *   - Live source polls forever during market hours
 *   - This source has a defined start and end — calls onComplete when done
 *
 * Use case: "run my strategy on Nifty for last May" or "replay a specific
 * crude-oil crash day at 20x speed to see what my strategy would have done."
 *
 * Requires Angel credentials. No market-hours awareness — historical data is
 * always available.
 */
@Slf4j
public class AngelHistoricalReplayCandleSource implements CandleSource {

    private static final String BROKER = "ANGEL";

    private final MarketDataService marketDataService;
    private final Exchange exchange;
    private final String symbolToken;
    private final Interval interval;
    private final LocalDate from;
    private final LocalDate to;
    private final int candlesPerSecond;

    private ScheduledExecutorService executor;
    private volatile boolean running;

    public AngelHistoricalReplayCandleSource(MarketDataService marketDataService,
                                             Exchange exchange, String symbolToken,
                                             Interval interval,
                                             LocalDate from, LocalDate to,
                                             int candlesPerSecond) {
        if (from == null || to == null) throw new IllegalArgumentException("from/to required");
        if (from.isAfter(to)) throw new IllegalArgumentException("from must be <= to");
        if (candlesPerSecond <= 0) throw new IllegalArgumentException("candlesPerSecond must be > 0");
        this.marketDataService = marketDataService;
        this.exchange          = exchange;
        this.symbolToken       = symbolToken;
        this.interval          = interval;
        this.from              = from;
        this.to                = to;
        this.candlesPerSecond  = candlesPerSecond;
    }

    @Override
    public String name() {
        return String.format("angel-historical-replay(%s:%s,%s,%s..%s,cps=%d)",
                exchange, symbolToken, interval, from, to, candlesPerSecond);
    }

    @Override
    public synchronized void start(Consumer<Candle> onCandle, Runnable onComplete) {
        if (running) return;
        running = true;

        // 1. Fetch the entire range from Angel up-front.
        List<Candle> candles;
        try {
            candles = marketDataService.getCandles(BROKER, exchange, symbolToken, interval, from, to);
        } catch (Exception e) {
            log.error("Angel historical fetch failed: {}", e.getMessage());
            running = false;
            try { onComplete.run(); } catch (Exception ignored) {}
            return;
        }

        if (candles.isEmpty()) {
            log.warn("Angel returned 0 candles for {}:{} {}..{} @ {}", exchange, symbolToken, from, to, interval);
            running = false;
            try { onComplete.run(); } catch (Exception ignored) {}
            return;
        }

        log.info("Angel historical replay: fetched {} candles for {}:{} {}..{} @ {}, replaying at {} cps",
                candles.size(), exchange, symbolToken, from, to, interval, candlesPerSecond);

        // 2. Replay at the configured speed.
        long delayMs = Math.max(1L, 1000L / candlesPerSecond);
        AtomicInteger cursor = new AtomicInteger(0);
        List<Candle> replay = List.copyOf(candles);

        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "paper-angel-hist-replay");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(() -> {
            if (!running) return;
            int i = cursor.getAndIncrement();
            if (i >= replay.size()) {
                stop();
                try { onComplete.run(); } catch (Exception e) {
                    log.warn("onComplete threw: {}", e.getMessage());
                }
                return;
            }
            try {
                onCandle.accept(replay.get(i));
            } catch (Exception e) {
                log.warn("onCandle threw at index {}: {}", i, e.getMessage());
            }
        }, 0L, delayMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void stop() {
        if (!running) return;
        running = false;
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
        log.debug("Angel historical replay stopped");
    }
}
