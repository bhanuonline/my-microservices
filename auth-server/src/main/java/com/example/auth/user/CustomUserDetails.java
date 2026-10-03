package com.example.auth.user;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.example.auth.entity.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;


/**
 * Adapts our {@link AppUser} entity to Spring Security's {@link UserDetails}
 * contract. Spring's authentication code doesn't know about our tables — it
 * asks {@code UserDetailsService.loadUserByUsername()}, gets back a
 * {@code UserDetails}, and reads password + authorities from that.
 *
 * <p>Roles → authorities mapping: each role string (e.g. "ADMIN") becomes a
 * {@code SimpleGrantedAuthority("ROLE_ADMIN")} — Spring's convention is that
 * role authorities have the "ROLE_" prefix. That's how {@code hasRole("ADMIN")}
 * works in {@code SecurityFilterChain} configs.
 *
 * <p>{@link #isAccountNonLocked()} reads the entity's {@code lockedUntil}
 * field. Feature 5 relies on this: when the timestamp is in the future,
 * Spring throws {@code LockedException} before even checking the password.
 * When the flag is off, nothing writes to {@code lockedUntil}, so this method
 * always returns true — same behaviour as before Feature 5 shipped.
 */
public class CustomUserDetails implements UserDetails {

    private final AppUser appUser;

    public CustomUserDetails(AppUser appUser) {
        this.appUser = appUser;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return appUser.getRoles().stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    @Override
    public String getPassword() {
        return appUser.getPassword();
    }

    @Override
    public String getUsername() {
        return appUser.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        // FEATURE 5: honour locked_until timestamp.
        // Naturally flag-neutral — when the feature is off, nothing writes locked_until,
        // so it's always null → always non-locked.
        Instant until = appUser.getLockedUntil();
        return until == null || until.isBefore(Instant.now());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return appUser.isEnabled();
    }

    // Extra accessors
    public String getEmail() {
        return appUser.getEmail();
    }

    public Long getId() {
        return appUser.getId();
    }

    /** Full domain user — used by Feature 7 token customizer to enrich JWTs. */
    public com.example.auth.entity.AppUser getAppUser() {
        return appUser;
    }
}