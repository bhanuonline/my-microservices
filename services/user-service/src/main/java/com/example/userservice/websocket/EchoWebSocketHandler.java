package com.example.userservice.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;

/**
 * Trivial echo handler for demonstrating WebSocket routing through the gateway.
 *
 * Every text message from the client is echoed back with a server timestamp.
 * The handshake `Authorization` header is inspected on connect so you can
 * confirm the gateway's TokenRelay filter is forwarding it.
 *
 * Path: registered at /ws/echo by WebSocketConfig.
 * Reached via gateway at:  ws://localhost:8080/ws/echo  (route: /ws/**)
 * Direct on this service:  ws://localhost:8090/ws/echo
 */
public class EchoWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(EchoWebSocketHandler.class);

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Object authHeader = session.getHandshakeHeaders().getFirst("Authorization");
        String remote = session.getRemoteAddress() == null ? "?" : session.getRemoteAddress().toString();
        log.info("WS connected: id={} remote={} auth={}",
                session.getId(),
                remote,
                authHeader != null ? "Bearer***" : "none");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String reply = "echo@" + Instant.now() + ": " + message.getPayload();
        session.sendMessage(new TextMessage(reply));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WS closed: id={} status={}", session.getId(), status);
    }
}
