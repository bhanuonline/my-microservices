package com.angle.trading.user;

import com.angle.trading.config.SecurityAuditProperties;
import com.angle.trading.persistence.AppUserRepository;
import com.angle.trading.persistence.LoginAuditEntity;
import com.angle.trading.persistence.LoginAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Write + query API for the login audit trail.
 *
 * Writes are best-effort — if audit fails, login still succeeds.
 * (We never want logging to break authentication.)
 *
 * Pruning runs on a schedule driven by {@code security.audit.prune-cron}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAuditService {

    private final LoginAuditRepository repo;
    private final AppUserRepository userRepo;
    private final SecurityAuditProperties props;

    /** Reason codes written into failure_reason. Kept short so the column stays indexable. */
    public static final String REASON_BAD_CREDENTIALS = "BAD_CREDENTIALS";
    public static final String REASON_LOCKED          = "LOCKED";
    public static final String REASON_DISABLED        = "DISABLED";
    public static final String REASON_NOT_FOUND       = "NOT_FOUND";
    public static final String REASON_UNKNOWN         = "UNKNOWN";

    @Transactional
    public void record(String username, boolean success, String failureReason,
                       String ipAddress, String userAgent) {
        if (!props.isEnabled()) return;
        try {
            LoginAuditEntity row = new LoginAuditEntity();
            row.setUsername(trim(username, 64));
            row.setUserId(username == null ? null :
                    userRepo.findByUsername(username).map(u -> u.getId()).orElse(null));
            row.setSuccess(success);
            row.setFailureReason(success ? null : (failureReason == null ? REASON_UNKNOWN : failureReason));
            row.setIpAddress(trim(ipAddress == null ? "unknown" : ipAddress, 45));
            row.setUserAgent(trim(userAgent, 255));
            row.setCreatedAt(Instant.now());
            repo.save(row);
        } catch (Exception e) {
            // Don't let an audit write failure break authentication flow.
            log.warn("Failed to write audit row for {}: {}", username, e.getMessage());
        }
    }

    // ---------- read-side helpers for the admin UI ----------

    public Page<LoginAuditEntity> findRecent(Pageable pageable) {
        return repo.findAllByOrderByCreatedAtDesc(pageable);
    }

    public Page<LoginAuditEntity> findForUser(String username, Pageable pageable) {
        return repo.findByUsernameOrderByCreatedAtDesc(username, pageable);
    }

    public Page<LoginAuditEntity> findBySuccess(boolean success, Pageable pageable) {
        return repo.findBySuccessOrderByCreatedAtDesc(success, pageable);
    }

    public Page<LoginAuditEntity> findSince(Instant since, Pageable pageable) {
        return repo.findByCreatedAtAfterOrderByCreatedAtDesc(since, pageable);
    }

    public Page<LoginAuditEntity> findFiltered(String username, Boolean success, Pageable pageable) {
        if (username != null && !username.isBlank() && success != null) {
            return repo.findByUsernameAndSuccessOrderByCreatedAtDesc(username.trim(), success, pageable);
        }
        if (username != null && !username.isBlank()) {
            return repo.findByUsernameOrderByCreatedAtDesc(username.trim(), pageable);
        }
        if (success != null) {
            return repo.findBySuccessOrderByCreatedAtDesc(success, pageable);
        }
        return repo.findAllByOrderByCreatedAtDesc(pageable);
    }

    /** IPs that have failed in the last 24h and never succeeded — flagged in the UI. */
    public Set<String> suspiciousIps() {
        Instant since = Instant.now().minus(Duration.ofHours(24));
        return Set.copyOf(repo.findIpsWithOnlyFailuresSince(since));
    }

    // ---------- scheduled prune ----------

    @Scheduled(cron = "#{@securityAuditProperties.pruneCron}")
    @Transactional
    public void pruneOld() {
        if (!props.isEnabled() || props.getRetentionDays() <= 0) return;
        Instant cutoff = Instant.now().minus(Duration.ofDays(props.getRetentionDays()));
        int deleted = repo.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("Audit prune: removed {} rows older than {} days", deleted, props.getRetentionDays());
        }
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
