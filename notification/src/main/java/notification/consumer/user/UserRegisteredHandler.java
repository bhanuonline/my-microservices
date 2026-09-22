package notification.consumer.user;

import com.example.common.event.UserRegisteredEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * Consumes UserRegisteredEvent from Kafka topic user.registered.
 *
 * Bean name = "userRegistered" → Cloud Stream looks for binding
 * spring.cloud.stream.bindings.userRegistered-in-0 in yml.
 *
 * Naming convention: "<beanName>-in-0" for consumers, "<beanName>-out-0" for producers.
 */
@Configuration
@Slf4j
public class UserRegisteredHandler {

    @Bean
    public Consumer<UserRegisteredEvent> userRegistered() {
        return event -> {
            log.info("Received UserRegisteredEvent: userId={} email={}", event.userId(), event.email());
            // TODO: actual email dispatch — hook into your notification/dispatch/EmailDispatcher.
            // For now, just log to prove the wire is up.
        };
    }
}
