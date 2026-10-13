package com.example.orderservice.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Instant;

// Fires ONCE after the full ApplicationContext is refreshed and the embedded
// server is ready to accept requests. Safer than @PostConstruct for startup
// tasks that need the whole app wired (e.g. Feign clients, DB connections).
@Component
public class AppStartupListener {

    private static final Logger log = LoggerFactory.getLogger(AppStartupListener.class);

    private final Environment env;

    public AppStartupListener(Environment env) {
        this.env = env;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady(ApplicationReadyEvent event) {
        long uptimeMs = System.currentTimeMillis() - event.getTimestamp();
        log.info("========================================");
        log.info("  order-service is READY");
        log.info("  Port    : {}", env.getProperty("server.port", "8083"));
        log.info("  Profiles: {}", String.join(", ", env.getActiveProfiles().length > 0
                ? env.getActiveProfiles() : new String[]{"default"}));
        log.info("  Started : {}", Instant.now());
        log.info("  Boot time: {}ms", uptimeMs);
        log.info("========================================");
    }
}
