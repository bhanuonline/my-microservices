package com.example.auth.api.v1.dto;

import com.example.auth.entity.SigningKeyEntity;

import java.time.Instant;

/**
 * JSON body returned by GET / POST on /api/v1/admin/keys.
 * <p>Only the public half of the key is safe to expose. Private material stays
 * server-side (JPA backend: DB; Vault backend: Vault itself).
 */
public record KeyResponse(
        String kid,
        String status,
        boolean active,
        Instant createdAt
) {
    public static KeyResponse from(SigningKeyEntity e) {
        return new KeyResponse(
                e.getKid(),
                e.getStatus() != null ? e.getStatus().name() : null,
                e.isActive(),
                e.getCreatedAt()
        );
    }
}
