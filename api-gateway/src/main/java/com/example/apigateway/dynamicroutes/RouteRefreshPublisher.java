package com.example.apigateway.dynamicroutes;

import com.example.apigateway.config.DynamicRoutesProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Publishes route-refresh notifications to the Redis pub/sub channel so peer
 * gateway instances can reload their route tables.
 *
 * Return value of convertAndSend() = number of subscribers that received the
 * message (0 = no peers listening; useful for observability).
 */
@Component
@EnableConfigurationProperties(DynamicRoutesProperties.class)
@ConditionalOnProperty(prefix = "gateway.dynamic-routes.pubsub", name = "enabled", havingValue = "true")
public class RouteRefreshPublisher {

    private static final Logger log = LoggerFactory.getLogger(RouteRefreshPublisher.class);

    private final ReactiveStringRedisTemplate redis;
    private final DynamicRoutesProperties props;
    private final ObjectMapper mapper;

    public RouteRefreshPublisher(ReactiveStringRedisTemplate redis,
                                 DynamicRoutesProperties props,
                                 ObjectMapper mapper) {
        this.redis = redis;
        this.props = props;
        this.mapper = mapper;
    }

    public Mono<Long> broadcast() {
        RouteRefreshMessage msg = RouteRefreshMessage.refresh(props.getPubsub().getInstanceId());
        String payload;
        try {
            payload = mapper.writeValueAsString(msg);
        } catch (Exception e) {
            return Mono.error(new IllegalStateException("Failed to serialize RouteRefreshMessage", e));
        }
        return redis.convertAndSend(props.getPubsub().getChannel(), payload)
                .doOnSuccess(n -> log.debug("Published route refresh to '{}' — {} subscriber(s)",
                        props.getPubsub().getChannel(), n))
                .doOnError(e -> log.warn("Failed to publish route refresh: {}", e.getMessage()));
    }
}
