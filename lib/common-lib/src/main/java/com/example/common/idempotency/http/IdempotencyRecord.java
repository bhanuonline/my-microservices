package com.example.common.idempotency.http;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * HTTP-layer idempotency record. One row per (key, endpoint, principal).
 *
 * The composite PK (key + endpoint) is intentional: client-provided keys only
 * need to be unique within an endpoint. Add `principal` once you have multi-tenant
 * callers so key `abc` from user A never collides with user B's key `abc`.
 *
 * Not a Lombok class — common-lib artefacts should stay compile-stable even if
 * annotation processors glitch in downstream services.
 */
@Entity
@Table(
    name = "idempotency_records",
    indexes = {
        @Index(name = "idx_idem_expires", columnList = "expires_at")
    }
)
@IdClass(IdempotencyRecord.Key.class)
public class IdempotencyRecord {

    @Id
    @Column(name = "idem_key", length = 128, nullable = false)
    private String key;

    @Id
    @Column(name = "endpoint", length = 255, nullable = false)
    private String endpoint;

    @Column(name = "principal", length = 128, nullable = false)
    private String principal;

    /** SHA-256 of the request body (hex). Guards against key reuse with different payload. */
    @Column(name = "request_hash", length = 64, nullable = false)
    private String requestHash;

    @Column(name = "status_code", nullable = false)
    private int statusCode;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Lob
    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecord() {}

    public IdempotencyRecord(String key, String endpoint, String principal, String requestHash,
                             int statusCode, String contentType, String responseBody,
                             Instant createdAt, Instant expiresAt) {
        this.key = key;
        this.endpoint = endpoint;
        this.principal = principal;
        this.requestHash = requestHash;
        this.statusCode = statusCode;
        this.contentType = contentType;
        this.responseBody = responseBody;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getKey() { return key; }
    public String getEndpoint() { return endpoint; }
    public String getPrincipal() { return principal; }
    public String getRequestHash() { return requestHash; }
    public int getStatusCode() { return statusCode; }
    public String getContentType() { return contentType; }
    public String getResponseBody() { return responseBody; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }

    public static class Key implements Serializable {
        private String key;
        private String endpoint;

        public Key() {}
        public Key(String key, String endpoint) { this.key = key; this.endpoint = endpoint; }

        @Override public boolean equals(Object o) {
            if (!(o instanceof Key k)) return false;
            return Objects.equals(key, k.key) && Objects.equals(endpoint, k.endpoint);
        }
        @Override public int hashCode() { return Objects.hash(key, endpoint); }
    }
}
