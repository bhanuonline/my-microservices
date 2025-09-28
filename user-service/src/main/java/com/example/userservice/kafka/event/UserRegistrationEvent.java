package com.example.userservice.kafka.event;

import com.example.userservice.dto.UserDto;
import lombok.Data;

@Data
public class UserRegistrationEvent {
    private UserDto userDto;
}
