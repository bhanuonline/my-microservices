package com.example.userservice.service;

import com.example.common.event.UserRegisteredEvent;
import com.example.userservice.dto.CreateUserRequest;
import com.example.userservice.dto.UserMapper;
import com.example.userservice.dto.UserResponse;
import com.example.userservice.model.User;
import com.example.userservice.outbox.OutboxWriter;
import com.example.userservice.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class UserRegistrationService {

    private static final String TOPIC = "user.registered";
    private static final String AGGREGATE = "user";

    private final UserRepository userRepository;
    private final OutboxWriter outboxWriter;

    public UserRegistrationService(UserRepository userRepository, OutboxWriter outboxWriter) {
        this.userRepository = userRepository;
        this.outboxWriter = outboxWriter;
    }

    // Single transaction: user + outbox row commit together, or both roll back.
    // This is the whole point of the pattern.
    @Transactional
    public UserResponse register(CreateUserRequest req) {
        User saved = userRepository.save(UserMapper.toEntity(req));

        outboxWriter.write(AGGREGATE, TOPIC, new UserRegisteredEvent(
                saved.getId(),
                saved.getName(),
                saved.getEmail(),
                Instant.now()
        ));

        return UserMapper.toResponse(saved);
    }
}
