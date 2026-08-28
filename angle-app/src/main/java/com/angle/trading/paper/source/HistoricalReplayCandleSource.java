package com.angle.trading.paper.source;

import com.angle.trading.broker.model.Candle;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Replays a fixed list of candles at a configurable speed.
 *
 *   candlesPerSecond=100 → one candle every 10 ms  (500-candle CSV → 5 seconds)
 *   candlesPerSecond=1   → one candle every second (500 candles → 8 minutes)
 *
 * Emits from index 0 forwards. When the list is exhausted, calls
 * onComplete once and stops itself.
 *
 * Safe to call {@link #stop} multiple times. Not safe to call
 * {@link #start} twice on the same instance — create a new one.
 */
@Slf4j
public class HistoricalReplayCandleSource implements CandleSource {

    private final List<Candle> candles;
    private final int candlesPerSecond;
    private ScheduledExecutorService executor;
    private volatile boolean running;

    public HistoricalReplayCandleSource(List<Candle> candles, int candlesPerSecond) {
        if (candlesPerSecond <= 0) throw new IllegalArgumentException("candlesPerSecond must be > 0");
        this.candles = List.copyOf(candles);
        this.candlesPerSecond = candlesPerSecond;
    }

    @Override
    public String name() {
        return "replay(cps=" + candlesPerSecond + ", size=" + candles.size() + ")";
    }

    @Override
    public synchronized void start(Consumer<Candle> onCandle, Runnable onComplete) {
        if (running) return;
        running = true;
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "paper-replay");
            t.setDaemon(true);
            return t;
        });

        long delayMs = Math.max(1L, 1000L / candlesPerSecond);
        AtomicInteger cursor = new AtomicInteger(0);

        executor.scheduleAtFixedRate(() -> {
            if (!running) return;
            int i = cursor.getAndIncrement();
            if (i >= candles.size()) {
                stop();
                try { onComplete.run(); } catch (Exception e) {
                    log.warn("onComplete threw: {}", e.getMessage());
                }
                return;
            }
            try {
                onCandle.accept(candles.get(i));
            } catch (Exception e) {
                log.warn("onCandle threw at candle {}: {}", i, e.getMessage());
            }
        }, 0L, delayMs, TimeUnit.MILLISECONDS);

        log.info("Replay started: {} candles at {} cps", candles.size(), candlesPerSecond);
    }

    @Override
    public synchronized void stop() {
        if (!running) return;
        running = false;
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
        log.debug("Replay stopped");
    }
}
