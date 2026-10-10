package com.example.auth.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * One row in the audit table — a durable record of "who did what, when, to which
 * subject".
 *
 * <p>Physical table name is {@code client_audit} for backward compatibility. It
 * originally (V5) tracked OAuth client changes only; V8 broadened it to cover
 * users and signing keys too via the {@code subject_type} discriminator.
 *
 * <p>Fields:
 * <table>
 *   <tr><td>{@code actor}</td><td>Username (browser) or client_id (JWT bearer)</td></tr>
 *   <tr><td>{@code action}</td><td>CREATE / UPDATE / DELETE / ROTATE / RETIRE /
 *                                    LOCK / UNLOCK</td></tr>
 *   <tr><td>{@code subject_type}</td><td>CLIENT / USER / KEY</td></tr>
 *   <tr><td>{@code subject_id}</td><td>client_id / username / kid</td></tr>
 *   <tr><td>{@code changed_at}</td><td>DB-managed timestamp (never modified by app)</td></tr>
 *   <tr><td>{@code diff_json}</td><td>JSON snapshot of the new state, minus
 *                                       credentials</td></tr>
 * </table>
 *
 * <p>Writes always come through {@link com.example.auth.service.admin.AuditService}
 * — never directly. That's how we guarantee passwords and secrets never leak
 * into audit rows (the service strips them in {@code snapshotForAudit}).
 */
@Entity
@Table(name = "client_audit")
@Data
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 64, nullable = false)
    private String actor;

    @Column(length = 32, nullable = false)
    private String action;

    /** CLIENT / USER / KEY */
    @Column(name = "subject_type", length = 16, nullable = false)
    private String subjectType;

    @Column(name = "subject_id", length = 200)
    private String subjectId;

    @Column(name = "changed_at", insertable = false, updatable = false)
    private Instant changedAt;

    @Column(name = "diff_json", columnDefinition = "TEXT")
    private String diffJson;
}
