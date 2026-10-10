package notification.consumer.user;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UserEventConsumer {

    @KafkaListener(topics = "user-events", groupId = "notification-group")
    public void consumeUserEvent(String message) {
        log.info("📩 Received Kafka event: " + message);
        // Here you could send an actual email
    }
}