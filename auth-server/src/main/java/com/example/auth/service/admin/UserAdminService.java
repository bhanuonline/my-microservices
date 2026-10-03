package com.example.auth.service.admin;

import com.example.auth.dto.UserForm;
import com.example.auth.entity.AppUser;
import com.example.auth.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * CRUD for app_user rows — used by both the browser admin UI and the REST API.
 *
 * <p>Translates the flat {@link com.example.auth.dto.UserForm} into an
 * {@link AppUser} entity and vice versa. Handles password hashing (BCrypt) and
 * "blank password on update means keep existing".
 *
 * <p>Also owns the {@link #unlock(Long)} action — clearing the failure counter
 * and lockout timestamp for a user (Feature 5). Under the hood this is a
 * simple set-to-zero, but exposed as a distinct method so callers can express
 * intent (and so the audit log records action=UNLOCK, not action=UPDATE).
 *
 * <p>Every mutation ({@link #save}, {@link #deleteById}, {@link #unlock})
 * writes to {@link AuditService} in the same transaction as the DB write.
 * Passwords are never included in audit snapshots.
 */
@Service
@Profile("jdbc")
public class UserAdminService {

    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);

    private final AppUserRepository repo;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public UserAdminService(AppUserRepository repo, PasswordEncoder passwordEncoder,
                             AuditService audit) {
        this.repo = repo;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    public List<AppUser> listAll() {
        return repo.findAll();
    }

    public AppUser findById(Long id) {
        return repo.findById(id).orElse(null);
    }

    public UserForm toForm(AppUser u) {
        UserForm f = new UserForm();
        f.setId(u.getId());
        f.setUsername(u.getUsername());
        f.setEmail(u.getEmail());
        f.setEnabled(u.isEnabled());
        f.setRoles(new LinkedHashSet<>(u.getRoles()));
        return f;
    }

    @Transactional
    public void save(UserForm form) {
        boolean isNew = form.getId() == null;
        AppUser u = isNew ? new AppUser() : repo.findById(form.getId())
                .orElseThrow(() -> new IllegalArgumentException("user " + form.getId() + " not found"));

        u.setUsername(form.getUsername());
        u.setEmail(form.getEmail());
        u.setEnabled(form.isEnabled());
        u.setRoles(new LinkedHashSet<>(form.getRoles()));

        if (isNew) {
            if (!StringUtils.hasText(form.getPassword())) {
                throw new IllegalArgumentException("password required on create");
            }
            u.setPassword(passwordEncoder.encode(form.getPassword()));
        } else if (StringUtils.hasText(form.getPassword())) {
            u.setPassword(passwordEncoder.encode(form.getPassword()));
        }
        // else: preserve existing hash

        repo.save(u);
        log.info("Saved user id={} username={} (isNew={})", u.getId(), u.getUsername(), isNew);

        audit.recordUser(isNew ? "CREATE" : "UPDATE", u.getUsername(),
                java.util.Map.of(
                        "id", u.getId(),
                        "username", u.getUsername(),
                        "email", u.getEmail() == null ? "" : u.getEmail(),
                        "enabled", u.isEnabled(),
                        "roles", u.getRoles()));
        // Password deliberately omitted from audit.
    }

    public void deleteById(Long id) {
        AppUser existing = repo.findById(id).orElse(null);
        String username = existing != null ? existing.getUsername() : String.valueOf(id);

        repo.deleteById(id);
        log.info("Deleted user id={}", id);

        audit.recordUser("DELETE", username, java.util.Map.of("id", id));
    }

    /** FEATURE 5: admin unlock — clears failure counter and lockout timestamp. */
    @Transactional
    public void unlock(Long id) {
        AppUser u = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("no user: " + id));
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        repo.save(u);
        log.info("Admin unlocked user id={} username={}", id, u.getUsername());
        audit.recordUser("UNLOCK", u.getUsername(), java.util.Map.of("byAdmin", true));
    }
}
