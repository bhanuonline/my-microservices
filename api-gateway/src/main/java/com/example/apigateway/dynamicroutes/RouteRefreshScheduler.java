package com.example.apigateway.dynamicroutes;

import com.example.apigateway.config.DynamicRoutesProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically triggers RefreshRoutesEvent so multiple gateway replicas
 * eventually converge on DB state. Simple + coarse — for real production
 * you'd use Redis pub/sub or a lightweight change-notification channel.
 *
 * Fires at gateway.dynamic-routes.refresh-interval (default 30s).
 */
@Component
@EnableConfigurationProperties(DynamicRoutesProperties.class)
@ConditionalOnProperty(prefix = "gateway.dynamic-routes", name = "enabled", havingValue = "true")
public class RouteRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(RouteRefreshScheduler.class);

    private final ApplicationEventPublisher events;

    public RouteRefreshScheduler(ApplicationEventPublisher events) {
        this.events = events;
    }

    @Scheduled(fixedDelayString = "${gateway.dynamic-routes.refresh-interval:30s}")
    public void refresh() {
        log.debug("Publishing scheduled RefreshRoutesEvent");
        events.publishEvent(new RefreshRoutesEvent(this));
    }
}
