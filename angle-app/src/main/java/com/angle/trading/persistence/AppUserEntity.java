package com.angle.trading.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * App user — authentication + authorization + per-user metadata.
 *
 *   username        unique login handle
 *   password        bcrypt hash (NEVER plaintext — see UserService)
 *   email           optional, used later for password reset / notifications
 *   role            ROLE_ADMIN or ROLE_USER (prefixed as Spring Security expects)
 *   enabled         admin can disable a user without deleting
 *   emailVerified   phase F — new users start false if signup requires verify
 *
 * Table name {@code app_user} because "user" is a reserved word in some DBs.
 */
@Entity
@Table(
        name = "app_user",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_username", columnNames = "username"),
        indexes = {
                @Index(name = "idx_user_email", columnList = "email"),
                @Index(name = "idx_user_enabled", columnList = "enabled")
        }
)
@Data
@NoArgsConstructor
public class AppUserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String username;

    /** bcrypt hash — always starts with "$2a$", "$2b$", or "$2y$". */
    @Column(nullable = false, length = 100)
    private String password;

    @Column(length = 120)
    private String email;

    /** Must be prefixed with "ROLE_" — Spring Security convention. */
    @Column(nullable = false, length = 32)
    private String role = "ROLE_USER";

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    /**
     * Running count of consecutive failed login attempts.
     * Reset to 0 on any successful login or admin unlock.
     * When it hits security.lockout.max-attempts, {@link #lockedUntil}
     * is set and the account rejects logins until that time.
     */
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    /**
     * If non-null and in the future, the account is locked out.
     * Spring Security's accountNonLocked flag is derived from this
     * in {@code JpaUserDetailsService}.
     */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    // ---------- TOTP two-factor authentication ----------

    /**
     * Base32-encoded 160-bit secret. Set during enrolment, never shown
     * again after the QR page. Can be rotated by disabling + re-enabling.
     * Null when 2FA has never been set up.
     */
    @Column(name = "totp_secret", length = 64)
    private String totpSecret;

    /**
     * True once the user has scanned the QR AND typed a valid first code.
     * The filter only enforces 2FA when this flag is true — a half-finished
     * enrolment (secret stored but never verified) does NOT lock the user out.
     */
    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled = false;

    /** When the user successfully turned 2FA on. Null until confirmed. */
    @Column(name = "totp_enabled_at")
    private Instant totpEnabledAt;
}
