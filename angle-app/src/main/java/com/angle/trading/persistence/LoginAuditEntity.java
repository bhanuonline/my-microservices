package com.angle.trading.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One row per login attempt — success OR failure.
 *
 * Written by {@code LoginAuditService} from the authentication event
 * listener. Used by:
 *   • /admin/audit   — human review of recent activity
 *   • Forensics      — "who logged in from where, when"
 *   • Nightly prune  — rows older than security.audit.retention-days are deleted
 *
 * Deliberately NOT a foreign key to {@code app_user}: we also log attempts
 * against unknown usernames (someone probing for valid names) and the user
 * might be deleted later while the audit should survive.
 */
@Entity
@Table(
        name = "user_login_audit",
        indexes = {
                @Index(name = "idx_audit_user_time", columnList = "username, createdAt DESC"),
                @Index(name = "idx_audit_ip_time",   columnList = "ipAddress, createdAt DESC"),
                @Index(name = "idx_audit_time",      columnList = "createdAt DESC"),
                @Index(name = "idx_audit_success",   columnList = "success")
        }
)
@Data
@NoArgsConstructor
public class LoginAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** As submitted — kept even if user doesn't exist (probing attempts). */
    @Column(nullable = false, length = 64)
    private String username;

    /** FK app_user.id if the username resolves; null for unknown usernames. */
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private boolean success;

    /**
     * One of: BAD_CREDENTIALS, LOCKED, DISABLED, NOT_FOUND, UNKNOWN.
     * Null on success rows.
     */
    @Column(name = "failure_reason", length = 32)
    private String failureReason;

    /** IPv4 or IPv6 — 45 chars is wide enough for IPv6 with scope id. */
    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    /** Truncated to 255 chars to keep index/row size reasonable. */
    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
