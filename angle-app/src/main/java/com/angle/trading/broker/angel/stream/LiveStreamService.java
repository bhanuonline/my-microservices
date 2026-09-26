package com.angle.trading.broker.angel.stream;

import com.angle.trading.broker.angel.stream.model.Tick;
import com.angle.trading.config.BrokerProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fan-out layer for SSE clients.
 *
 * Subscribes to internal {@link TickEvent} + {@link CandleClosedEvent}
 * (published by AngelStreamClient / CandleAggregator) and forwards them
 * to every connected browser via {@link SseEmitter}.
 *
 * Design points:
 *   - Per-client tick throttling: at most one tick per token every N ms per client.
 *     Candles bypass throttle (they arrive every 1M/5M/15M — no risk of flood).
 *   - Each browser tab holds its own {@link Subscriber} — cleaned up on
 *     disconnect / IO error / timeout.
 *   - Heartbeat comment sent every N seconds so intermediary proxies (Nginx,
 *     ELB, corporate WAF) don't reap idle connections.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveStreamService {

    private final BrokerProperties brokerProperties;
    private final ObjectMapper json;

    private final CopyOnWriteArrayList<Subscriber> subscribers = new CopyOnWriteArrayList<>();

    // ---------- subscribe ----------

    /**
     * Register a new SSE client. When {@code tokenFilter} is non-empty, only
     * ticks/candles for those symbol tokens are pushed to this client.
     */
    public SseEmitter subscribe(Set<String> tokenFilter) {
        // 0 timeout = never — we manage lifecycle via events.
        SseEmitter emitter = new SseEmitter(0L);
        Subscriber sub = new Subscriber(emitter, tokenFilter == null ? Set.of() : new HashSet<>(tokenFilter));
        subscribers.add(sub);

        emitter.onCompletion(() -> { subscribers.remove(sub); log.debug("SSE completed, subs left: {}", subscribers.size()); });
        emitter.onTimeout(()    -> { subscribers.remove(sub); emitter.complete(); });
        emitter.onError(err     -> subscribers.remove(sub));

        try {
            emitter.send(SseEmitter.event().name("hello")
                    .data(Map.of("clients", subscribers.size(), "message", "connected")));
        } catch (IOException ignored) {}

        log.info("SSE client connected — filter={}, total subs={}",
                tokenFilter == null || tokenFilter.isEmpty() ? "ALL" : tokenFilter, subscribers.size());
        return emitter;
    }

    // ---------- inbound events ----------

    @EventListener
    public void onTick(TickEvent e) {
        if (!brokerProperties.getAngel().getStream().getLiveStream().isEnabled()) return;
        if (subscribers.isEmpty()) return;
        Tick t = e.tick();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", t.symbolToken());
        payload.put("ltp",   t.ltp());
        payload.put("ts",    t.exchangeTime().toEpochMilli());
        long now = System.currentTimeMillis();
        int throttleMs = brokerProperties.getAngel().getStream().getLiveStream().getThrottleMs();
        for (Subscriber sub : subscribers) {
            if (!sub.matches(t.symbolToken())) continue;
            if (!sub.shouldSend(t.symbolToken(), now, throttleMs)) continue;
            send(sub, "tick", payload);
        }
    }

    @EventListener
    public void onCandleClosed(CandleClosedEvent e) {
        if (!brokerProperties.getAngel().getStream().getLiveStream().isEnabled()) return;
        if (subscribers.isEmpty()) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token",    e.symbolToken());
        payload.put("interval", e.interval().name());
        payload.put("ts",       e.candle().timestamp().toEpochMilli());
        payload.put("open",     e.candle().open());
        payload.put("high",     e.candle().high());
        payload.put("low",      e.candle().low());
        payload.put("close",    e.candle().close());
        for (Subscriber sub : subscribers) {
            if (!sub.matches(e.symbolToken())) continue;
            send(sub, "candle", payload);
        }
    }

    /** Keep-alive comment sent to every subscriber. Comments are ignored by EventSource. */
    @Scheduled(fixedRateString = "#{@brokerProperties.angel.stream.liveStream.heartbeatSeconds * 1000}")
    public void heartbeat() {
        if (subscribers.isEmpty()) return;
        for (Subscriber sub : subscribers) {
            try {
                sub.emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (Exception ex) {
                subscribers.remove(sub);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        for (Subscriber sub : subscribers) {
            try { sub.emitter.complete(); } catch (Exception ignored) {}
        }
        subscribers.clear();
    }

    // ---------- helpers ----------

    private void send(Subscriber sub, String eventName, Object payload) {
        try {
            sub.emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(json.writeValueAsString(payload)));
        } catch (Exception e) {
            subscribers.remove(sub);
            try { sub.emitter.complete(); } catch (Exception ignored) {}
        }
    }

    /** One connected SSE client. */
    private static final class Subscriber {
        final SseEmitter emitter;
        final Set<String> tokenFilter;    // empty = all tokens
        // last-sent timestamp per token, for throttling
        final ConcurrentHashMap<String, AtomicLong> lastSentAt = new ConcurrentHashMap<>();

        Subscriber(SseEmitter emitter, Set<String> tokenFilter) {
            this.emitter = emitter;
            this.tokenFilter = tokenFilter;
        }

        boolean matches(String token) {
            return tokenFilter.isEmpty() || tokenFilter.contains(token);
        }

        /** True if enough ms have elapsed since we last sent this token. */
        boolean shouldSend(String token, long now, int throttleMs) {
            AtomicLong last = lastSentAt.computeIfAbsent(token, k -> new AtomicLong(0));
            long prev = last.get();
            if (now - prev < throttleMs) return false;
            return last.compareAndSet(prev, now);
        }
    }
}
