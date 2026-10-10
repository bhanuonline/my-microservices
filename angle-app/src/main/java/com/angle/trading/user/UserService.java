package com.angle.trading.user;

import com.angle.trading.config.SecurityLockoutProperties;
import com.angle.trading.persistence.AppUserEntity;
import com.angle.trading.persistence.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * CRUD + password lifecycle for {@link AppUserEntity}.
 *
 * Passwords are ALWAYS hashed via the injected {@link PasswordEncoder} (bcrypt).
 * Plaintext passwords never touch the entity or DB.
 *
 * All business rules live here so controllers stay thin.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository repo;
    private final PasswordEncoder passwordEncoder;
    private final SecurityLockoutProperties lockoutProps;
    private final TotpService totpService;

    // ---------- queries ----------

    public List<AppUserEntity> findAll() {
        return repo.findAll();
    }

    public Optional<AppUserEntity> findById(Long id) {
        return repo.findById(id);
    }

    public Optional<AppUserEntity> findByUsername(String username) {
        return repo.findByUsername(username);
    }

    public boolean usernameExists(String username) {
        return repo.existsByUsername(username);
    }

    public boolean emailExists(String email) {
        return email != null && !email.isBlank() && repo.existsByEmail(email);
    }

    // ---------- create / update ----------

    @Transactional
    public AppUserEntity create(String username, String rawPassword, String email, String role, boolean enabled) {
        if (usernameExists(username)) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }
        String normalisedRole = normaliseRole(role);
        AppUserEntity u = new AppUserEntity();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(rawPassword));
        u.setEmail(email == null || email.isBlank() ? null : email.trim().toLowerCase());
        u.setRole(normalisedRole);
        u.setEnabled(enabled);
        u.setEmailVerified(false);
        u.setCreatedAt(Instant.now());
        u.setPasswordChangedAt(Instant.now());
        AppUserEntity saved = repo.save(u);
        log.info("User created: {} role={} enabled={}", username, normalisedRole, enabled);
        return saved;
    }

    @Transactional
    public AppUserEntity update(Long id, String email, String role, Boolean enabled) {
        AppUserEntity u = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        if (email != null) u.setEmail(email.isBlank() ? null : email.trim().toLowerCase());
        if (role != null) u.setRole(normaliseRole(role));
        if (enabled != null) u.setEnabled(enabled);
        return repo.save(u);
    }

    /** Admin resets any user's password. */
    @Transactional
    public void resetPassword(Long userId, String newRawPassword) {
        AppUserEntity u = repo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        u.setPassword(passwordEncoder.encode(newRawPassword));
        u.setPasswordChangedAt(Instant.now());
        repo.save(u);
        log.info("Admin reset password for user: {}", u.getUsername());
    }

    /** User changes their own password after verifying current. */
    @Transactional
    public void changePassword(String username, String currentRawPassword, String newRawPassword) {
        AppUserEntity u = repo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        if (!passwordEncoder.matches(currentRawPassword, u.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        u.setPassword(passwordEncoder.encode(newRawPassword));
        u.setPasswordChangedAt(Instant.now());
        repo.save(u);
        log.info("User self-changed password: {}", username);
    }

    @Transactional
    public void updateLastLogin(String username) {
        repo.findByUsername(username).ifPresent(u -> {
            u.setLastLoginAt(Instant.now());
            // Any successful login clears the fail counter + any residual lock.
            u.setFailedLoginAttempts(0);
            u.setLockedUntil(null);
            repo.save(u);
        });
    }

    // ---------- lockout ----------

    /**
     * Called by {@link LoginEventListener} on every bad-password attempt.
     * Increments the counter; locks the account once it reaches the
     * configured threshold.
     *
     * Returns the resulting fail count (useful for logging).
     * No-op if lockout is disabled in config.
     */
    @Transactional
    public int recordFailedLogin(String username) {
        if (!lockoutProps.isEnabled()) return 0;
        return repo.findByUsername(username).map(u -> {
            int newCount = u.getFailedLoginAttempts() + 1;
            u.setFailedLoginAttempts(newCount);
            if (newCount >= lockoutProps.getMaxAttempts()) {
                Instant until = Instant.now().plus(Duration.ofMinutes(lockoutProps.getLockDurationMinutes()));
                u.setLockedUntil(until);
                log.warn("User {} LOCKED until {} after {} failed attempts",
                        username, until, newCount);
            }
            repo.save(u);
            return newCount;
        }).orElse(0);
    }

    /** Admin manually unlocks a user before the timer runs out. */
    @Transactional
    public void unlock(Long userId) {
        AppUserEntity u = repo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        u.setFailedLoginAttempts(0);
        u.setLockedUntil(null);
        repo.save(u);
        log.info("Admin unlocked user: {}", u.getUsername());
    }

    /**
     * True if the account's lockedUntil is still in the future.
     * Used by LoginFailureHandler to choose between "wrong password" and
     * "account locked" error messages.
     */
    public boolean isLocked(String username) {
        return repo.findByUsername(username)
                .map(u -> u.getLockedUntil() != null && u.getLockedUntil().isAfter(Instant.now()))
                .orElse(false);
    }

    /** Minutes until the lock expires. 0 if not locked. */
    public long minutesUntilUnlock(String username) {
        return repo.findByUsername(username)
                .map(AppUserEntity::getLockedUntil)
                .filter(t -> t != null && t.isAfter(Instant.now()))
                .map(t -> Math.max(1, Duration.between(Instant.now(), t).toMinutes() + 1))
                .orElse(0L);
    }

    @Transactional
    public void delete(Long id) {
        AppUserEntity u = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        repo.deleteById(id);
        log.info("User deleted: {}", u.getUsername());
    }

    // ---------- TOTP (two-factor) ----------

    /**
     * Starts enrolment: generates a fresh secret, stores it on the user
     * (NOT enabled yet), and returns the data needed to render the QR page.
     *
     * Can be called repeatedly before confirm — generates a new secret each
     * time, invalidating previous enrolment attempts. Prevents a stale QR
     * from being scanned months later.
     */
    @Transactional
    public TotpEnrolment startTotpEnrolment(String username) {
        AppUserEntity u = repo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        if (u.isTotpEnabled()) {
            throw new IllegalArgumentException("2FA is already enabled; disable it first to re-enrol");
        }
        String secret = totpService.generateSecret();
        u.setTotpSecret(secret);
        u.setTotpEnabledAt(null);
        repo.save(u);
        String qr = totpService.generateQrDataUri(username, secret);
        String uri = totpService.otpAuthUri(username, secret);
        return new TotpEnrolment(secret, qr, uri);
    }

    /**
     * Confirms enrolment by verifying the first 6-digit code against the
     * stored (but not-yet-enabled) secret. Only on success does totpEnabled
     * flip to true — otherwise the user can try again without losing their
     * secret.
     */
    @Transactional
    public void confirmTotpEnrolment(String username, String code) {
        AppUserEntity u = repo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        if (u.getTotpSecret() == null) {
            throw new IllegalArgumentException("No pending 2FA enrolment — start setup first");
        }
        if (!totpService.verify(u.getTotpSecret(), code)) {
            throw new IllegalArgumentException("That code didn't match. Try the next one.");
        }
        u.setTotpEnabled(true);
        u.setTotpEnabledAt(Instant.now());
        repo.save(u);
        log.info("2FA enabled for {}", username);
    }

    /**
     * Self-service disable — requires the user to type a valid current code
     * (or their password; we take whichever the caller provided). This prevents
     * someone with a stolen session cookie from quietly disabling 2FA.
     */
    @Transactional
    public void disableTotp(String username, String currentCodeOrPassword) {
        AppUserEntity u = repo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        if (!u.isTotpEnabled()) {
            return;    // already off, nothing to do
        }
        boolean codeOk = u.getTotpSecret() != null
                && totpService.verify(u.getTotpSecret(), currentCodeOrPassword);
        boolean pwdOk  = passwordEncoder.matches(currentCodeOrPassword, u.getPassword());
        if (!codeOk && !pwdOk) {
            throw new IllegalArgumentException("Current password or 2FA code required to disable 2FA");
        }
        u.setTotpEnabled(false);
        u.setTotpSecret(null);
        u.setTotpEnabledAt(null);
        repo.save(u);
        log.info("2FA disabled for {}", username);
    }

    /** Admin override — clears another user's 2FA without any code/pw check. */
    @Transactional
    public void adminDisableTotp(Long userId) {
        AppUserEntity u = repo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (!u.isTotpEnabled() && u.getTotpSecret() == null) return;
        u.setTotpEnabled(false);
        u.setTotpSecret(null);
        u.setTotpEnabledAt(null);
        repo.save(u);
        log.info("Admin cleared 2FA for user: {}", u.getUsername());
    }

    public boolean verifyTotpCode(String username, String code) {
        return repo.findByUsername(username)
                .filter(AppUserEntity::isTotpEnabled)
                .map(u -> totpService.verify(u.getTotpSecret(), code))
                .orElse(false);
    }

    /** Return type for the enrolment page. Record keeps the three strings grouped. */
    public record TotpEnrolment(String secret, String qrDataUri, String otpAuthUri) { }

    /** Spring Security needs roles prefixed with "ROLE_". Normalise either form. */
    private static String normaliseRole(String role) {
        if (role == null || role.isBlank()) return "ROLE_USER";
        String up = role.trim().toUpperCase();
        return up.startsWith("ROLE_") ? up : "ROLE_" + up;
    }
}
