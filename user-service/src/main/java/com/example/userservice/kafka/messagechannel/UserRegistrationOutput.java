package com.example.userservice.kafka.messagechannel;


import org.springframework.messaging.MessageChannel;

public interface UserRegistrationOutput {
    //@Output("userRegistrationOutput")
    MessageChannel userRegistrationOutput();
}