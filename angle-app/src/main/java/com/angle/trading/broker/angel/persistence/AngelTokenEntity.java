package com.angle.trading.broker.angel.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Cached Angel SmartAPI JWT token, keyed by client code.
 *
 * Persisting the JWT means an app restart doesn't have to burn a TOTP
 * code to re-authenticate — the still-valid JWT is loaded from DB on
 * the first {@code getJwtToken()} call after boot.
 *
 * Angel's JWT is typically valid ~8 hours; we conservatively cache for 7.
 *
 * Security note: JWT is a session token — losing it lets someone act
 * as this client until it expires. Same risk profile as any web session
 * cookie. Password and TOTP secret NEVER go into the DB.
 */
@Entity
@Table(name = "angel_token")
@Data
@NoArgsConstructor
public class AngelTokenEntity {

    @Id
    @Column(name = "client_code", length = 32)
    private String clientCode;

    @Column(name = "jwt_token", nullable = false, length = 2048)
    private String jwtToken;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    public AngelTokenEntity(String clientCode, String jwtToken, Instant expiresAt) {
        this.clientCode = clientCode;
        this.jwtToken   = jwtToken;
        this.expiresAt  = expiresAt;
        this.savedAt    = Instant.now();
    }
}
