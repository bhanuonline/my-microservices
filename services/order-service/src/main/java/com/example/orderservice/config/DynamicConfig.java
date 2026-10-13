package com.example.orderservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

// @RefreshScope: when a busrefresh event arrives (via Spring Cloud Bus),
// Spring destroys and recreates this bean with the latest config values.
// Without @RefreshScope, @Value fields are fixed at startup and never update.
//
// How to trigger a refresh:
//   1. Change a value in the config-server (e.g. config-repo/order-service.yml)
//   2. POST http://localhost:8888/actuator/busrefresh
//   3. Cloud Bus broadcasts the event via Kafka to all instances
//   4. Every instance refreshes its @RefreshScope beans automatically
@RefreshScope
@Component
public class DynamicConfig {

    private static final Logger log = LoggerFactory.getLogger(DynamicConfig.class);

    @Value("${orderservice.maintenance.enabled:false}")
    private boolean maintenanceEnabled;

    @Value("${orderservice.ratelimit.max-requests-per-window:30}")
    private int rateLimitMax;

    @Value("${orderservice.batch.stale-order.fixed-delay-ms:300000}")
    private long batchDelayMs;

    public boolean isMaintenanceEnabled() { return maintenanceEnabled; }
    public int getRateLimitMax() { return rateLimitMax; }
    public long getBatchDelayMs() { return batchDelayMs; }

    // Called when the bean is refreshed — useful to log what changed
    @jakarta.annotation.PostConstruct
    public void logConfig() {
        log.info("DynamicConfig loaded — maintenance={} rateLimit={} batchDelay={}ms",
                maintenanceEnabled, rateLimitMax, batchDelayMs);
    }
}
