package com.example.auth.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * One row in the {@code signing_key} table — an RSA key pair used to sign JWTs.
 *
 * <p>Multi-key model (introduced in Feature 3, migration V7):
 * <ul>
 *   <li>Exactly ONE row is PRIMARY — signs all new tokens.</li>
 *   <li>Zero or more SECONDARY rows — still in the JWKS response, verify tokens
 *       signed while they were PRIMARY. Never sign new tokens.</li>
 *   <li>Any number of RETIRED rows — removed from JWKS. Tokens that used them
 *       stop verifying. Kept in the table for audit only.</li>
 * </ul>
 *
 * <p>Both {@code active} and {@code status} columns exist for historical
 * reasons (V4 had only active; V7 added status). Meaning today:
 * {@code active=1} ⇔ key appears in JWKS (PRIMARY or SECONDARY).
 * {@code active=0} ⇔ RETIRED.
 *
 * <p>Feature 11 note: when the Vault backend is used, private keys don't live
 * in this table at all — Vault holds them. This entity is only meaningful on
 * the JPA backend.
 */
@Entity
@Table(name = "signing_key")
@Data
public class SigningKeyEntity {

    /**
     * Where in the lifecycle a key sits. Values follow the PRIMARY → SECONDARY →
     * RETIRED progression. See {@code V7__signing_key_multi.sql} for the DB
     * check-constraint values.
     */
    public enum Status {
        /** Signs all NEW tokens. Exactly one row at any time. */
        PRIMARY,
        /** Verifies EXISTING tokens only. Zero or more rows. In JWKS response. */
        SECONDARY,
        /** Removed from JWKS. Kept only for audit / forensic. */
        RETIRED
    }

    @Id
    @Column(length = 64)
    private String kid;

    @Column(name = "public_key", columnDefinition = "TEXT", nullable = false)
    private String publicKey;

    @Column(name = "private_key", columnDefinition = "TEXT", nullable = false)
    private String privateKey;

    /**
     * true → key appears in /oauth2/jwks (PRIMARY or SECONDARY)
     * false → RETIRED
     * Kept for backward compat with pre-V7 code + JPQL convenience.
     */
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    // columnDefinition pins the type to VARCHAR so Hibernate 6 doesn't try to
    // create/validate a MySQL native ENUM(...) column.
    @Column(length = 16, nullable = false, columnDefinition = "VARCHAR(16)")
    private Status status = Status.PRIMARY;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
