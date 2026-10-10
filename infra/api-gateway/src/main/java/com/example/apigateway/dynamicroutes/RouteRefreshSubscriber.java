package com.example.apigateway.dynamicroutes;

import com.example.apigateway.config.DynamicRoutesProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * Subscribes to the Redis pub/sub channel and re-publishes local
 * RefreshRoutesEvent so this instance's CachingRouteLocator rebuilds.
 *
 * Self-echo filter: messages tagged with our own instanceId are skipped
 * (we already refreshed locally when we published).
 *
 * Reconnect: any error on the subscription triggers exponential-backoff
 * retry via Reactor's Retry.backoff — survives Redis restarts + network blips.
 */
@Component
@EnableConfigurationProperties(DynamicRoutesProperties.class)
@ConditionalOnProperty(prefix = "gateway.dynamic-routes.pubsub", name = "enabled", havingValue = "true")
public class RouteRefreshSubscriber {

    private static final Logger log = LoggerFactory.getLogger(RouteRefreshSubscriber.class);

    private final ReactiveStringRedisTemplate redis;
    private final DynamicRoutesProperties props;
    private final ApplicationEventPublisher events;
    private final ObjectMapper mapper;

    private volatile Disposable subscription;

    public RouteRefreshSubscriber(ReactiveStringRedisTemplate redis,
                                  DynamicRoutesProperties props,
                                  ApplicationEventPublisher events,
                                  ObjectMapper mapper) {
        this.redis = redis;
        this.props = props;
        this.events = events;
        this.mapper = mapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void subscribe() {
        String channel = props.getPubsub().getChannel();
        log.info("Subscribing to route-refresh channel '{}' as instance '{}'",
                channel, props.getPubsub().getInstanceId());

        subscription = redis.listenToChannel(channel)
                .doOnNext(msg -> handle(msg.getMessage()))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(30))
                        .doBeforeRetry(sig -> log.warn(
                                "Route refresh subscription failed (attempt {}): {} — retrying",
                                sig.totalRetries() + 1,
                                sig.failure() == null ? "unknown" : sig.failure().getMessage())))
                .subscribe();
    }

    private void handle(String payload) {
        try {
            RouteRefreshMessage msg = mapper.readValue(payload, RouteRefreshMessage.class);
            if (props.getPubsub().getInstanceId().equals(msg.sourceId())) {
                log.debug("Skipping self-published refresh from '{}'", msg.sourceId());
                return;
            }
            log.info("Received route refresh from '{}' (action={})", msg.sourceId(), msg.action());
            events.publishEvent(new RefreshRoutesEvent(this));
        } catch (Exception e) {
            log.warn("Invalid route refresh payload dropped: {} — {}", payload, e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
            log.info("Route refresh subscription disposed");
        }
    }
}
