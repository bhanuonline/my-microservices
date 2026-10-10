package com.example.auth.service.admin;

import com.example.auth.config.FeatureFlags;
import com.example.auth.entity.AuditEntry;
import com.example.auth.repository.AuditEntryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Writes rows to the audit log. Every mutation from the admin UI or REST API
 * calls one of the three record* methods on this service.
 *
 * <p>Physical table: {@code client_audit} (misleading name — the V5 migration
 * created it for OAuth clients, V8 broadened it to hold users and keys too).
 *
 * <p>Each row captures:
 * <ul>
 *   <li>{@code actor}      — WHO did it (from SecurityContextHolder)</li>
 *   <li>{@code action}     — WHAT (CREATE / UPDATE / DELETE / ROTATE / LOCK / UNLOCK)</li>
 *   <li>{@code subject_type} — CLIENT / USER / KEY</li>
 *   <li>{@code subject_id} — WHICH one (client_id, username, or kid)</li>
 *   <li>{@code diff_json}  — snapshot of the state we're moving to (JSON)</li>
 *   <li>{@code changed_at} — WHEN (server clock, auto-set by MySQL)</li>
 * </ul>
 *
 * <p>Transactional guarantee:
 * the record* methods have no @Transactional annotation → they inherit the
 * caller's transaction (default propagation REQUIRED). So the audit INSERT and
 * the business INSERT/UPDATE either both commit or both roll back. You can't
 * end up with a change but no record of who did it, or vice versa.
 *
 * <p>Fail-closed on flag off:
 * every method short-circuits if {@code features.audit.enabled=false}. No rows
 * are written. Existing rows are never auto-deleted.
 *
 * <p>Credential hygiene:
 * callers use {@link #recordClient}, {@link #recordUser}, {@link #recordKey}
 * — never pass password fields into the snapshot. See how
 * {@code ClientAdminService.snapshotForAudit()} strips {@code clientSecret}
 * before calling us.
 */
@Service
@Profile("jdbc")
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    public static final String SUBJECT_CLIENT = "CLIENT";
    public static final String SUBJECT_USER = "USER";
    public static final String SUBJECT_KEY = "KEY";

    private final AuditEntryRepository repo;
    private final FeatureFlags flags;
    private final ObjectMapper mapper = new ObjectMapper();

    public AuditService(AuditEntryRepository repo, FeatureFlags flags) {
        this.repo = repo;
        this.flags = flags;
    }

    public void recordClient(String action, String clientId, Object snapshot) {
        record(action, SUBJECT_CLIENT, clientId, snapshot);
    }

    public void recordUser(String action, String username, Object snapshot) {
        record(action, SUBJECT_USER, username, snapshot);
    }

    public void recordKey(String action, String kid, Object snapshot) {
        record(action, SUBJECT_KEY, kid, snapshot);
    }

    private void record(String action, String subjectType, String subjectId, Object snapshot) {
        if (!flags.getAudit().isEnabled()) {
            return; // fail-closed: no rows written when disabled
        }
        AuditEntry e = new AuditEntry();
        e.setActor(currentActor());
        e.setAction(action);
        e.setSubjectType(subjectType);
        e.setSubjectId(subjectId);
        e.setDiffJson(toJson(snapshot));
        repo.save(e);
        log.info("AUDIT actor={} action={} {}={} ", e.getActor(), action, subjectType, subjectId);
    }

    private String currentActor() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(Authentication::getName)
                .orElse("system");
    }

    private String toJson(Object o) {
        if (o == null) return null;
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException ex) {
            // Never fail the business write because of serialization — record a marker.
            return "{\"_serializationError\":\"" + ex.getMessage().replace("\"", "'") + "\"}";
        }
    }
}
