package com.example.auth.api.v1.dto;

import com.example.auth.entity.AppUser;

import java.time.Instant;
import java.util.Set;

/**
 * JSON body returned by GET / POST / PUT on /api/v1/admin/users.
 * <p>Password is deliberately absent — audit + REST clients never see it.
 * Feature 5 fields ({@code failedAttempts}, {@code lockedUntil}) are exposed so
 * SIEM / dashboards can spot lockouts.
 */
public record UserResponse(
        Long id,
        String username,
        String email,
        boolean enabled,
        Set<String> roles,
        int failedAttempts,
        Instant lockedUntil,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserResponse from(AppUser u) {
        return new UserResponse(
                u.getId(),
                u.getUsername(),
                u.getEmail(),
                u.isEnabled(),
                u.getRoles(),
                u.getFailedAttempts(),
                u.getLockedUntil(),
                u.getCreatedAt(),
                u.getUpdatedAt()
        );
    }
}
