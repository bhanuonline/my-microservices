package com.angle.trading.broker.angel.stream;

import com.angle.trading.broker.angel.AngelAuthService;
import com.angle.trading.broker.angel.stream.model.Tick;
import com.angle.trading.config.BrokerProperties;
import com.angle.trading.service.InstrumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Angel SmartStream WebSocket client.
 *
 * On boot (if enabled):
 *   1. Wait for {@link ApplicationReadyEvent} so other beans are up.
 *   2. Get feedToken + JWT from {@link AngelAuthService}.
 *   3. Open WebSocket to Angel with the 4 required auth headers.
 *   4. Send subscribe message with all enabled instrument tokens.
 *   5. On every binary frame → decode → publish {@link TickEvent}.
 *
 * Reconnects with a fixed backoff if the WS drops. Instrument list is re-read
 * on each connect, so new instruments added via /admin/instruments start
 * streaming after the next reconnect (or app restart).
 *
 * Heartbeat sent every 30 sec — Angel drops idle connections after ~60 sec.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AngelStreamClient {

    private final BrokerProperties brokerProperties;
    private final AngelAuthService authService;
    private final InstrumentService instrumentService;
    private final ApplicationEventPublisher events;
    private final ObjectMapper json = new ObjectMapper();

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private WebSocketSession session;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "angel-stream");
        t.setDaemon(true);
        return t;
    });

    // Angel exchangeType numeric codes for the subscribe message.
    private static final Map<String, Integer> EXCHANGE_CODE = Map.of(
            "NSE", 1,   // NSE_CM equity + indices
            "NFO", 2,   // NSE F&O
            "BSE", 3,
            "BFO", 4,
            "MCX", 5,
            "NCX", 7,
            "CDS", 13
    );

    // ---------- lifecycle ----------

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        BrokerProperties.Stream cfg = brokerProperties.getAngel().getStream();
        if (!cfg.isEnabled()) {
            log.info("AngelStreamClient disabled (broker.angel.stream.enabled=false)");
            return;
        }
        if (!brokerProperties.getAngel().isEnabled()) {
            log.warn("AngelStreamClient: Angel broker not enabled — cannot stream");
            return;
        }
        scheduler.execute(this::connectLoop);
    }

    @PreDestroy
    public void stop() {
        shuttingDown.set(true);
        closeSession("shutdown");
        scheduler.shutdownNow();
    }

    // ---------- connect loop ----------

    private void connectLoop() {
        while (!shuttingDown.get()) {
            try {
                connectOnce();
                // Successfully connected — sleep until session ends. Reconnect below on close.
                while (connected.get() && !shuttingDown.get()) {
                    Thread.sleep(2000);
                }
            } catch (Exception e) {
                log.warn("AngelStreamClient connect failed: {}", e.getMessage());
            }
            if (shuttingDown.get()) return;
            int wait = brokerProperties.getAngel().getStream().getReconnectSeconds();
            log.info("Reconnecting to Angel stream in {}s", wait);
            try { Thread.sleep(wait * 1000L); } catch (InterruptedException ie) { return; }
        }
    }

    private void connectOnce() throws Exception {
        // Force auth (may trigger refresh/login) so JWT + feedToken are fresh.
        String jwt = authService.getJwtToken();
        String feed = authService.getFeedToken();
        if (feed == null || feed.isBlank()) {
            throw new IllegalStateException("No feedToken available — login first");
        }
        BrokerProperties.Angel cfg = brokerProperties.getAngel();
        BrokerProperties.Stream sCfg = cfg.getStream();

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Authorization", "Bearer " + jwt);
        headers.add("x-api-key",     cfg.getApiKey());
        headers.add("x-client-code", cfg.getClientCode());
        headers.add("x-feed-token",  feed);

        StandardWebSocketClient client = new StandardWebSocketClient();
        session = client.execute(handler(), headers, URI.create(sCfg.getUrl())).get(10, TimeUnit.SECONDS);
        // Subscribe once the handler.afterConnectionEstablished fires — handled there.
    }

    // ---------- handler ----------

    private WebSocketHandler handler() {
        return new WebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession s) throws Exception {
                connected.set(true);
                log.info("Angel stream connected to {}", s.getUri());
                sendSubscribe(s);
                // Heartbeat every 30 sec — Angel disconnects idle sessions after ~60.
                scheduler.scheduleAtFixedRate(() -> heartbeat(s), 30, 30, TimeUnit.SECONDS);
            }

            @Override
            public void handleMessage(WebSocketSession s, WebSocketMessage<?> msg) {
                if (msg instanceof BinaryMessage bm) {
                    Tick t = AngelTickDecoder.decode(bm.getPayload().array());
                    if (t != null) events.publishEvent(new TickEvent(t));
                } else if (msg instanceof TextMessage tm) {
                    // Angel sends text pong / control messages occasionally.
                    log.debug("Angel stream text: {}", tm.getPayload());
                }
            }

            @Override
            public void handleTransportError(WebSocketSession s, Throwable ex) {
                log.warn("Angel stream transport error: {}", ex.getMessage());
            }

            @Override
            public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
                connected.set(false);
                log.warn("Angel stream closed: {}", status);
            }

            @Override public boolean supportsPartialMessages() { return false; }
        };
    }

    // ---------- messages ----------

    private void sendSubscribe(WebSocketSession s) throws Exception {
        // Group instruments by exchange code for Angel's expected shape.
        Map<Integer, List<String>> byExchange = new LinkedHashMap<>();
        instrumentService.listEnabled().forEach(ins -> {
            Integer code = EXCHANGE_CODE.get(ins.getExchange());
            if (code == null) {
                log.warn("Unknown Angel exchange '{}' for {}, skipping in subscribe", ins.getExchange(), ins.getSymbol());
                return;
            }
            byExchange.computeIfAbsent(code, k -> new ArrayList<>()).add(ins.getSymbolToken());
        });
        if (byExchange.isEmpty()) {
            log.warn("Angel stream: no instruments to subscribe");
            return;
        }
        List<Map<String, Object>> tokenList = byExchange.entrySet().stream()
                .map(e -> Map.<String, Object>of("exchangeType", e.getKey(), "tokens", e.getValue()))
                .collect(Collectors.toList());

        BrokerProperties.Stream cfg = brokerProperties.getAngel().getStream();
        Map<String, Object> params = new HashMap<>();
        params.put("mode", cfg.getMode());
        params.put("tokenList", tokenList);
        Map<String, Object> subscribeMsg = new LinkedHashMap<>();
        subscribeMsg.put("correlationID", cfg.getCorrelationId());
        subscribeMsg.put("action", 1);   // 1 = subscribe
        subscribeMsg.put("params", params);

        String payload = json.writeValueAsString(subscribeMsg);
        s.sendMessage(new TextMessage(payload));
        log.info("Angel stream subscribed to {} tokens across {} exchanges (mode={})",
                byExchange.values().stream().mapToInt(List::size).sum(),
                byExchange.size(),
                cfg.getMode());
    }

    private void heartbeat(WebSocketSession s) {
        if (!s.isOpen()) return;
        try {
            s.sendMessage(new TextMessage("ping"));
        } catch (Exception e) {
            log.warn("Heartbeat failed: {}", e.getMessage());
        }
    }

    private void closeSession(String reason) {
        try {
            if (session != null && session.isOpen()) session.close();
        } catch (Exception ignored) {}
        connected.set(false);
    }
}
