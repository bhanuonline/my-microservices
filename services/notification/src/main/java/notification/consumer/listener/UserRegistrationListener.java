package notification.consumer.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

/**
 * Legacy @StreamListener-era class. The functional-model
 * `UserRegisteredHandler` replaced this. Kept as reference; no beans wired.
 */
@Service
@Slf4j
public class UserRegistrationListener {

    public void handleUserRegistration(Message<String> message) throws JsonProcessingException {
        String payload = message.getPayload();
        log.info("Raw payload received: {}", payload);
        DocumentContext jsonContext = getDocumentContext(payload);
        String email = jsonContext.read("$.userDto.email");
        String name  = jsonContext.read("$.userDto.name");
        log.info("Received user name:{} and email: {}", name, email);
    }

    private DocumentContext getDocumentContext(String payload) {
        return JsonPath.parse(payload);
    }
}
