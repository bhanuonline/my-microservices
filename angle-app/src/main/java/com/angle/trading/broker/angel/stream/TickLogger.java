package com.angle.trading.broker.angel.stream;

import com.angle.trading.broker.angel.stream.model.Tick;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sample listener: aggregates tick counts + last price per token, prints a
 * summary every 60 seconds. Proof that the WebSocket stream is alive.
 *
 * Any other component can subscribe to TickEvent the same way — the stream
 * is a first-class Spring event.
 */
@Slf4j
@Component
public class TickLogger {

    private final AtomicLong totalTicks = new AtomicLong();
    private final ConcurrentHashMap<String, Tick> latestByToken = new ConcurrentHashMap<>();

    @EventListener
    public void onTick(TickEvent e) {
        Tick t = e.tick();
        totalTicks.incrementAndGet();
        latestByToken.put(t.symbolToken(), t);
    }

    /** Public snapshot — any component (e.g. dashboard) can read latest prices. */
    public java.util.Map<String, Tick> latest() {
        return java.util.Map.copyOf(latestByToken);
    }

    @Scheduled(fixedRate = 60_000)
    public void logSummary() {
        long ticks = totalTicks.getAndSet(0);
        if (ticks == 0 && latestByToken.isEmpty()) return;
        log.info("AngelStream: {} ticks/min across {} tokens", ticks, latestByToken.size());
        latestByToken.forEach((token, t) ->
                log.debug("  {} {} @ {}", t.exchangeCode(), token, t.ltp())
        );
    }
}
