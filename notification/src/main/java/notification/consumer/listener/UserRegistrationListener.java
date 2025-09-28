package notification.consumer.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import lombok.extern.slf4j.Slf4j;
import notification.consumer.bindingchannel.UserRegistrationInput;
import notification.consumer.dto.UserDto;

import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

//@EnableBinding(UserRegistrationInput.class)
@Service
@Slf4j
public class UserRegistrationListener {

    //@StreamListener("userRegistrationInput")
    public void handleUserRegistration(Message<String> message) throws JsonProcessingException {
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