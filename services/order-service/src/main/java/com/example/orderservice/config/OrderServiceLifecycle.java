package com.example.orderservice.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Demonstrates @PostConstruct / @PreDestroy — Spring bean lifecycle hooks.
//
// Lifecycle order:
//   1. Constructor runs
//   2. Dependencies injected (@Autowired / constructor injection)
//   3. @PostConstruct runs  ← we are here, all beans are wired
//   4. Bean is used by the application
//   5. @PreDestroy runs     ← app is shutting down, still safe to call other beans
//   6. Bean is garbage collected
@Component
public class OrderServiceLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceLifecycle.class);

    @Value("${orderservice.maintenance.enabled:false}")
    private boolean maintenanceEnabled;

    @Value("${orderservice.ratelimit.max-requests-per-window:30}")
    private int rateLimitMax;

    // Called after all dependencies are injected — safe to read @Value fields here.
    // Use for: validation of config, warming up connections, logging startup state.
    @PostConstruct
    public void init() {
        log.info("OrderServiceLifecycle initialised — maintenance={} rateLimit={}/min",
                maintenanceEnabled, rateLimitMax);
        if (maintenanceEnabled) {
            log.warn("WARNING: order-service is starting in MAINTENANCE MODE — all API calls will return 503");
        }
    }

    // Called just before the bean is destroyed — app is shutting down.
    // Use for: closing resources, flushing buffers, logging graceful shutdown.
    @PreDestroy
    public void destroy() {
        log.info("OrderServiceLifecycle shutting down — releasing resources");
    }
}
