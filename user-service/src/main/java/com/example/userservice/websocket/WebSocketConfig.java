package com.example.userservice.websocket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the EchoWebSocketHandler at /ws/echo.
 *
 * setAllowedOriginPatterns("*"): tolerates any Origin during WebSocket handshake.
 * The gateway enforces CORS/origin rules on HTTP; the browser's WebSocket
 * constructor doesn't participate in the same-origin policy the same way as
 * fetch(), so a permissive setting here is standard.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(echoHandler(), "/ws/echo")
                .setAllowedOriginPatterns("*");
    }

    @Bean
    public EchoWebSocketHandler echoHandler() {
        return new EchoWebSocketHandler();
    }
}
