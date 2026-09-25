package com.angle.trading.broker.angel.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Cached Angel SmartAPI tokens, keyed by client code.
 *
 * Persisting the tokens means an app restart doesn't have to burn a TOTP
 * code to re-authenticate — the still-valid JWT is loaded from DB on
 * the first {@code getJwtToken()} call after boot.
 *
 * Angel returns THREE tokens per login:
 *   jwtToken     — short-lived (~24h, forced logout at 8:30 AM IST daily).
 *                  Used as Bearer on every REST call.
 *   refreshToken — long-lived (~30 days). Traded for a fresh jwtToken via
 *                  /rest/auth/angelbroking/jwt/v1/generateTokens — no TOTP.
 *   feedToken    — WebSocket auth for SmartStream. Not stored (we don't stream yet).
 *
 * Security note: These are session-scoped credentials. Losing them lets
 * someone act as this client until they expire. Password and TOTP secret
 * NEVER go into the DB.
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

    /**
     * Nullable so older rows (before this column existed) still load;
     * a null forces us to fall back to TOTP login next time the JWT dies.
     */
    @Column(name = "refresh_token", length = 2048)
    private String refreshToken;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    public AngelTokenEntity(String clientCode, String jwtToken, String refreshToken, Instant expiresAt) {
        this.clientCode   = clientCode;
        this.jwtToken     = jwtToken;
        this.refreshToken = refreshToken;
        this.expiresAt    = expiresAt;
        this.savedAt      = Instant.now();
    }
}
