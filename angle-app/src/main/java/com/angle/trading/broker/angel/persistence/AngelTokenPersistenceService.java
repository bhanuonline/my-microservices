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
     * Return the cached token for this client code — but ONLY if it's
     * still valid. Expired tokens are ignored (and returned empty).
     */
    @Transactional(readOnly = true)
    public Optional<CachedToken> load(String clientCode) {
        try {
            return repo.findById(clientCode)
                    .filter(e -> Instant.now().isBefore(e.getExpiresAt()))
                    .map(e -> new CachedToken(e.getJwtToken(), e.getExpiresAt()));
        } catch (Exception ex) {
            log.warn("Failed to load Angel token from DB: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    @Transactional
    public void save(String clientCode, String jwt, Instant expiresAt) {
        try {
            repo.save(new AngelTokenEntity(clientCode, jwt, expiresAt));
            log.debug("Persisted Angel token for {} (expires {})", clientCode, expiresAt);
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

    public record CachedToken(String jwt, Instant expiresAt) {}
}
