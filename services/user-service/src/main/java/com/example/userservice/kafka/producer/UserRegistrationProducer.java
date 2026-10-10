package com.example.userservice.kafka.producer;

import com.example.userservice.dto.UserDto;
import com.example.userservice.kafka.event.UserRegistrationEvent;
import com.example.userservice.kafka.messagechannel.UserRegistrationOutput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.UUID;

//@EnableBinding(UserRegistrationOutput.class)
@Service
@Slf4j
public class UserRegistrationProducer {

    public static final String MESSAGE_IDEMPOTENCY_KEY = "MESSAGE_IDEMPOTENCY_KEY";

    @Autowired(required=true)
    private StreamBridge streamBridge;

    public void registerUser(UserDto dto) {

        // Wrap the UserDto inside Event object
        UserRegistrationEvent userRegistrationEvent = new UserRegistrationEvent();
        userRegistrationEvent.setUserDto(dto);

        // Build Spring Message with headers
        Message<UserRegistrationEvent> message =
                MessageBuilder.withPayload(userRegistrationEvent)
                        .setHeaderIfAbsent(MESSAGE_IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                        .setHeaderIfAbsent(HttpHeaders.ACCEPT_LANGUAGE,
                                LocaleContextHolder.getLocale())
                        .build();

        // Send to Kafka output binding (functional style)
        streamBridge.send("userRegistrationOutput", message);

        // Logging
        log.info("User sent to Kafka: {}", dto.getEmail());
    }

//    public void registerUser(UserDto dto) {
//
//        UserRegistrationEvent userRegistrationEvent = new UserRegistrationEvent();
//        userRegistrationEvent.setUserDto(dto);
//        Message<UserRegistrationEvent> message = org.springframework.integration.support.MessageBuilder.withPayload(userRegistrationEvent)
//                .setHeaderIfAbsent(MESSAGE_IDEMPOTENCY_KEY, UUID.randomUUID())
//                .setHeaderIfAbsent(HttpHeaders.ACCEPT_LANGUAGE,
//                        LocaleContextHolder.getLocale())
//                .build();
//        source.userRegistrationOutput().send(message);
//        log.info("User sent to Kafka: {}", dto.getEmail());
//    }

}