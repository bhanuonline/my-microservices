package com.example.auth.api.v1.dto;

import com.example.auth.entity.AuditEntry;

import java.time.Instant;

/**
 * JSON representation of one audit row. Returned as an array by
 * {@code GET /api/v1/admin/audit?page=N} (newest first, 50 per page).
 */
public record AuditResponse(
        Long id,
        String actor,
        String action,
        String subjectType,
        String subjectId,
        Instant changedAt,
        String diffJson
) {
    public static AuditResponse from(AuditEntry e) {
        return new AuditResponse(
                e.getId(),
                e.getActor(),
                e.getAction(),
                e.getSubjectType(),
                e.getSubjectId(),
                e.getChangedAt(),
                e.getDiffJson()
        );
    }
}
