package com.example.auth.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * A user that can log into the auth-server — via /login page (browser) OR via
 * a resource-server that hits our /oauth2/authorize flow.
 *
 * <p>Password is stored BCrypt-hashed with a {@code {bcrypt}} prefix so
 * DelegatingPasswordEncoder knows which algorithm to verify with.
 *
 * <p>Roles ({@code Set<String>}) map to a many-to-many join table
 * {@code app_user_role(user_id, role)} via {@code @ElementCollection}. We use
 * value collection (not a full {@code @Entity Role}) because roles are just
 * names — no attributes, no independent lifecycle. Fetch is EAGER because
 * Spring Security's {@code getAuthorities()} is called during authentication,
 * before any @Transactional is open.
 *
 * <p>Timestamps ({@code created_at}, {@code updated_at}) are set by MySQL
 * defaults + ON UPDATE clauses — {@code insertable=false, updatable=false}
 * tells JPA not to touch them.
 *
 * <p>Feature 5 columns ({@code failed_attempts}, {@code locked_until}) added by
 * migration V9. Managed by {@link com.example.auth.security.LockoutTracker}.
 */
@Entity
@Table(name = "app_user")
@Data
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String password;
    private String email;
    private boolean enabled = true;

    /** FEATURE 5: consecutive failed logins since last success. */
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    /** FEATURE 5: when the current lockout expires. NULL → not locked. */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "app_user_role",
            joinColumns = @JoinColumn(name = "user_id")
    )
    @Column(name = "role")
    private Set<String> roles = new HashSet<>();
}
