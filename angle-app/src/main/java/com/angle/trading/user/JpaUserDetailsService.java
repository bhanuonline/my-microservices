package com.angle.trading.user;

import com.angle.trading.persistence.AppUserEntity;
import com.angle.trading.persistence.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Adapts {@link AppUserEntity} to Spring Security's {@link UserDetails} contract.
 *
 * Spring Security calls {@link #loadUserByUsername(String)} on every login
 * attempt — must be fast. Delegates to {@link AppUserRepository#findByUsername}
 * which uses the uk_user_username index.
 *
 * accountLocked() is derived from the entity's lockedUntil column: if that
 * timestamp is in the future, DaoAuthenticationProvider will throw
 * LockedException instead of even checking the password.
 */
@Service
@RequiredArgsConstructor
public class JpaUserDetailsService implements UserDetailsService {

    private final AppUserRepository repo;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUserEntity u = repo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        return User.builder()
                .username(u.getUsername())
                .password(u.getPassword())              // already bcrypt-hashed
                .authorities(List.of(new SimpleGrantedAuthority(u.getRole())))
                .disabled(!u.isEnabled())
                .accountExpired(false)
                .accountLocked(isLocked(u))
                .credentialsExpired(false)
                .build();
    }

    private static boolean isLocked(AppUserEntity u) {
        return u.getLockedUntil() != null && u.getLockedUntil().isAfter(Instant.now());
    }
}
