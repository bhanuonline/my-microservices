package com.example.common.event;

import java.time.Instant;

/**
 * Published by user-service when a new user registers.
 * Consumed by notification-service (and anyone else who cares).
 *
 * As a shared contract, changes to this record are BREAKING for all consumers.
 * Treat it like a public API.
 */
public record UserRegisteredEvent(
        Long userId,
        String name,
        String email,
        Instant registeredAt
) {}
