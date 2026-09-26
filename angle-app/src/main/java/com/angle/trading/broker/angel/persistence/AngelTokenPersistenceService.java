package com.angle.trading.broker.angel.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Thin wrapper around {@link AngelTokenRepository} for the auth service.
 *
 * All methods are best-effort: DB errors are logged but never re-thrown
 * so a persistence hiccup doesn't block trading. Worst case the app
 * re-logs in — one extra TOTP burn, not a broken system.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AngelTokenPersistenceService {

    private final AngelTokenRepository repo;

    /**
     * Return the cached tokens for this client code, if any.
     *
     * We DON'T filter by expiry here — the caller may still want the
     * refreshToken even after the jwtToken has expired (to renew without TOTP).
     */
    @Transactional(readOnly = true)
    public Optional<CachedToken> load(String clientCode) {
        try {
            return repo.findById(clientCode)
                    .map(e -> new CachedToken(e.getJwtToken(), e.getRefreshToken(), e.getFeedToken(), e.getExpiresAt()));
        } catch (Exception ex) {
            log.warn("Failed to load Angel token from DB: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    @Transactional
    public void save(String clientCode, String jwt, String refreshToken, String feedToken, Instant expiresAt) {
        try {
            repo.save(new AngelTokenEntity(clientCode, jwt, refreshToken, feedToken, expiresAt));
            log.debug("Persisted Angel tokens for {} (expires {})", clientCode, expiresAt);
        } catch (Exception ex) {
            log.warn("Failed to persist Angel token: {}", ex.getMessage());
        }
    }

    @Transactional
    public void delete(String clientCode) {
        try {
            repo.deleteById(clientCode);
            log.debug("Deleted Angel token for {}", clientCode);
        } catch (Exception ex) {
            log.warn("Failed to delete Angel token: {}", ex.getMessage());
        }
    }

    public record CachedToken(String jwt, String refreshToken, String feedToken, Instant expiresAt) {
        public boolean jwtValid() {
            return jwt != null && expiresAt != null && Instant.now().isBefore(expiresAt);
        }
    }
}
