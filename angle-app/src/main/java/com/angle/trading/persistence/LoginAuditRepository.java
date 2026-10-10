package com.angle.trading.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Reads + writes for user_login_audit.
 *
 * Spring Data JPA synthesises method bodies from the names — no SQL needed
 * for the common cases. The @Query is only used for the nightly prune.
 */
public interface LoginAuditRepository extends JpaRepository<LoginAuditEntity, Long> {

    /** For /admin/audit paginated list. */
    Page<LoginAuditEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Per-user drill-down. */
    Page<LoginAuditEntity> findByUsernameOrderByCreatedAtDesc(String username, Pageable pageable);

    /** Filter by success/failure. */
    Page<LoginAuditEntity> findBySuccessOrderByCreatedAtDesc(boolean success, Pageable pageable);

    /** Compound filter — used from UI "username + success + since". */
    Page<LoginAuditEntity> findByUsernameAndSuccessOrderByCreatedAtDesc(
            String username, boolean success, Pageable pageable);

    /** Recent attempts since timestamp — the "last 24h" quick view. */
    Page<LoginAuditEntity> findByCreatedAtAfterOrderByCreatedAtDesc(Instant since, Pageable pageable);

    /**
     * IPs that have ONLY failed, never succeeded, in a given window.
     * These are the ones to flag as suspicious in the UI.
     */
    @Query("SELECT a.ipAddress FROM LoginAuditEntity a " +
           "WHERE a.createdAt > :since " +
           "GROUP BY a.ipAddress " +
           "HAVING MIN(CASE WHEN a.success = true THEN 1 ELSE 0 END) = 0")
    List<String> findIpsWithOnlyFailuresSince(@Param("since") Instant since);

    /** Nightly prune. */
    @Modifying
    @Transactional
    @Query("DELETE FROM LoginAuditEntity a WHERE a.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
