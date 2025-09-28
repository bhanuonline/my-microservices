package com.example.userservice.controller;

import com.example.userservice.dto.UserDto;
import com.example.userservice.kafka.producer.UserEventProducer;
import com.example.userservice.kafka.producer.UserRegistrationProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserRegistrationController {

    @Autowired
    private UserEventProducer userEventProducer;

    @Autowired
    private  KafkaTemplate<String, UserDto> kafkaTemplate;

    //@Autowired
    //private StreamBridge streamBridge;
    @Autowired
    private UserRegistrationProducer userRegistrationProducer;

    @PostMapping("/createUser")
    public String createUser(@RequestParam String name) {
        // Save to DB (omitted for brevity)
        userEventProducer.sendUserCreatedEvent("New user created: " + name);
        return "User created & event published";
    }

    @PostMapping("/registerUser")
    public String registerUser(@RequestBody UserDto user) {
        // Publish event to Kafka topic via binding
        //streamBridge.send("userRegistrationOutput", user);
       // kafkaTemplate.send("user-registration", user);
        userRegistrationProducer.registerUser(user);
        return "User registered and event sent!";
    }
}
