package com.example.auth.security;

import com.example.auth.config.FeatureFlags;
import com.example.auth.entity.AppUser;
import com.example.auth.repository.AppUserRepository;
import com.example.auth.service.admin.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Bookkeeper for account-lockout state. Called from
 * {@link LockoutAuthenticationHandler} which sits in Spring Security's
 * success/failure hooks.
 *
 * <p>Three verbs:
 * <ul>
 *   <li>{@link #onFailure(String)} — user typed wrong password. Bump the counter.
 *       Once at {@code maxAttempts}, set {@code locked_until = now + lockoutMinutes}.</li>
 *   <li>{@link #onSuccess(String)} — user got in. Reset counter to zero.</li>
 *   <li>{@link #unlock(String)} — admin manually cleared the lock via the UI.</li>
 * </ul>
 *
 * <p>Fail-closed: when {@code features.account-lockout.enabled=false} both onFailure
 * and onSuccess return immediately, so no new locks are ever written. Existing
 * {@code locked_until} rows keep their values — CustomUserDetails still respects
 * them until they expire naturally.
 *
 * <p>Silent on unknown username: if someone tries to log in as "nonexistent",
 * we return immediately without any DB write. Otherwise the response time
 * difference between "user exists, wrong password" and "user doesn't exist"
 * would leak usernames to attackers.
 */
@Component
@Profile("jdbc")
public class LockoutTracker {

    private static final Logger log = LoggerFactory.getLogger(LockoutTracker.class);

    private final AppUserRepository users;
    private final FeatureFlags flags;
    private final AuditService audit;
    private final com.example.auth.metrics.AuthMetrics metrics;

    public LockoutTracker(AppUserRepository users, FeatureFlags flags, AuditService audit,
                          com.example.auth.metrics.AuthMetrics metrics) {
        this.users = users;
        this.flags = flags;
        this.audit = audit;
        this.metrics = metrics;
    }

    @Transactional
    public void onFailure(String username) {
        metrics.loginAttempt("FAIL");
        if (!flags.getAccountLockout().isEnabled()) return;
        // Silent on unknown users — don't reveal whether the account exists.
        AppUser u = users.findByUsername(username).orElse(null);
        if (u == null) return;

        int next = u.getFailedAttempts() + 1;
        u.setFailedAttempts(next);
        int max = flags.getAccountLockout().getMaxAttempts();
        if (next >= max && (u.getLockedUntil() == null
                || u.getLockedUntil().isBefore(Instant.now()))) {
            Instant until = Instant.now()
                    .plus(flags.getAccountLockout().getLockoutMinutes(), ChronoUnit.MINUTES);
            u.setLockedUntil(until);
            log.warn("Locked account username={} until={} (attempts={})", username, until, next);
            audit.recordUser("LOCK", username, Map.of(
                    "attempts", next,
                    "lockedUntil", until.toString()));
            metrics.lockoutFired();
        }
        users.save(u);
    }

    @Transactional
    public void onSuccess(String username) {
        metrics.loginAttempt("SUCCESS");
        if (!flags.getAccountLockout().isEnabled()) return;
        AppUser u = users.findByUsername(username).orElse(null);
        if (u == null) return;
        if (u.getFailedAttempts() > 0 || u.getLockedUntil() != null) {
            u.setFailedAttempts(0);
            u.setLockedUntil(null);
            users.save(u);
            log.info("Reset lockout counter on successful login: username={}", username);
        }
    }

    @Transactional
    public void unlock(String username) {
        AppUser u = users.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("no user: " + username));
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        users.save(u);
        audit.recordUser("UNLOCK", username, Map.of("byAdmin", true));
        log.info("Admin unlocked username={}", username);
    }
}
