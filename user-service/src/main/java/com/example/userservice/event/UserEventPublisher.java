package com.example.userservice.event;

import com.example.common.event.UserRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

@Component
public class UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);

    // Binding name — connects to yml at spring.cloud.stream.bindings.userRegistered-out-0
    private static final String BINDING = "userRegistered-out-0";

    private final StreamBridge streamBridge;

    public UserEventPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    public void publishUserRegistered(UserRegisteredEvent event) {
        log.info("Publishing UserRegisteredEvent for userId={}", event.userId());
        boolean sent = streamBridge.send(BINDING, event);
        if (!sent) {
            log.warn("Kafka publish returned false for userId={}", event.userId());
        }
    }
}
