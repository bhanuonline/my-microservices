package notification.consumer.user.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import lombok.extern.slf4j.Slf4j;
import notification.consumer.dto.UserDto;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import java.util.function.Consumer;

@Component
@Slf4j
public class UserRegistrationHandler {

    @Bean
    public Consumer<UserDto> userRegistrationInput() {
        return user -> {
            log.info("Sending notification for new user: {}", user.getName());
            // e.g., send email or push notification
        };
    }
    //@KafkaListener(topics = "user-registration", groupId = "notification-user")
    public void consume(UserDto user) {
        log.info("Sending notification for new user: {}", user.getName());

        //spring.kafka.producer.properties.spring.json.add.type.headers=false
        //UserDto dto = new ObjectMapper().readValue(messageJson, UserDto.class);
        //process(dto);
    }

    @KafkaListener(topics = "user-registration", groupId = "notification-user")
    public void consume(Message<String> message) throws JsonProcessingException {
        String payload = message.getPayload();
        log.info("Raw payload received: {}", payload);
        DocumentContext jsonContext = getDocumentContext(payload);
        // Extract fields (adjust JSONPath expressions as per your payload structure)
        String email = jsonContext.read("$.userDto.email");
        String name  = jsonContext.read("$.userDto.name");
        log.info("Received user name:{} and email: {}", name,email);
    }

    private DocumentContext getDocumentContext(String payload) {
        return JsonPath.parse(payload);
    }

}