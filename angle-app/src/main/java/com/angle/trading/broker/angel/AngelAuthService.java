package com.angle.trading.broker.angel;

import com.angle.trading.broker.angel.dto.AngelLoginResponse;
import com.angle.trading.broker.angel.persistence.AngelTokenPersistenceService;
import com.angle.trading.broker.angel.persistence.AngelTokenPersistenceService.CachedToken;
import com.angle.trading.config.BrokerProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Handles Angel One SmartAPI authentication.
 *
 * Three token types are returned by Angel on login:
 *   jwtToken     — short-lived (~24h, forced logout at 8:30 AM IST daily).
 *                  Used as Bearer for all REST calls.
 *   refreshToken — long-lived (~30 days). Traded for a fresh JWT with NO TOTP burn.
 *   feedToken    — WebSocket streaming; ignored for now.
 *
 * Resolution flow on {@link #getJwtToken()}:
 *   1. Return in-memory JWT if still valid.
 *   2. Else hydrate from DB — if JWT still valid, use it.
 *   3. Else if a refreshToken exists (memory or DB), try {@link #refreshJwt(String)}.
 *   4. Else generate TOTP and do a full login (last resort — burns a TOTP).
 *
 * On a stale-JWT REST failure the caller invokes {@link #invalidate()},
 * then calls {@link #getJwtToken()} again — the resolver drops to step 3
 * and returns a fresh JWT with no manual intervention.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AngelAuthService {

    private static final String LOGIN_PATH   = "/rest/auth/angelbroking/user/v1/loginByPassword";
    private static final String REFRESH_PATH = "/rest/auth/angelbroking/jwt/v1/generateTokens";
    private static final String LOGOUT_PATH  = "/rest/secure/angelbroking/user/v1/logout";
    private static final Duration TOKEN_TTL  = Duration.ofHours(7);   // Angel says ~24h but 8:30 AM daily reset

    private final RestClient restClient;
    private final BrokerProperties brokerProperties;
    private final AngelTokenPersistenceService tokenPersistence;

    private volatile String cachedJwt;
    private volatile String cachedRefreshToken;
    private volatile String cachedFeedToken;
    private volatile Instant tokenExpiresAt;

    /** Feed token for WebSocket streaming. May be null if never logged in. */
    public String getFeedToken() { return cachedFeedToken; }

    /** Returns a valid JWT, hydrating from DB / refreshing / logging in as needed. */
    public synchronized String getJwtToken() {
        // Tier 1 — in-memory JWT
        if (cachedJwt != null && tokenExpiresAt != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedJwt;
        }

        // Tier 2 — DB (only relevant right after a restart)
        String clientCode = brokerProperties.getAngel().getClientCode();
        if (tokenPersistence != null && clientCode != null && !clientCode.isBlank()) {
            Optional<CachedToken> saved = tokenPersistence.load(clientCode);
            if (saved.isPresent()) {
                CachedToken t = saved.get();
                // Adopt refresh + feed tokens — useful even when the JWT is expired
                // (refresh renewals + WebSocket streaming both need them).
                cachedRefreshToken = t.refreshToken();
                cachedFeedToken    = t.feedToken();
                if (t.jwtValid()) {
                    cachedJwt      = t.jwt();
                    tokenExpiresAt = t.expiresAt();
                    log.info("Loaded cached Angel JWT from DB — valid until {} (feedToken: {})",
                            tokenExpiresAt, cachedFeedToken != null ? "present" : "MISSING");
                    return cachedJwt;
                }
                log.info("DB JWT expired; will attempt refresh-token renewal");
            }
        }

        // Tier 3 — refresh-token renewal (no TOTP burn)
        if (cachedRefreshToken != null && !cachedRefreshToken.isBlank()) {
            String renewed = refreshJwt(cachedRefreshToken);
            if (renewed != null) return renewed;
            // Refresh failed → refresh token is dead too; fall through to full login.
            log.warn("Refresh-token renewal failed; falling back to full TOTP login");
        }

        // Tier 4 — fresh login (burns a TOTP)
        return login();
    }

    /**
     * Force-drop the in-memory JWT so the next {@link #getJwtToken()} refetches.
     *
     * Called by API clients when they receive AB1010 (Invalid Token) from Angel.
     * The refreshToken is KEPT — the next resolution will use it to renew without TOTP.
     */
    public synchronized void invalidate() {
        log.info("Angel JWT invalidated (was valid until {})", tokenExpiresAt);
        cachedJwt      = null;
        tokenExpiresAt = null;
    }

    /**
     * Trade the given refresh token for a fresh JWT (no TOTP required).
     * Returns the new JWT or null on failure.
     */
    private String refreshJwt(String refreshToken) {
        BrokerProperties.Angel cfg = brokerProperties.getAngel();
        log.info("Refreshing Angel JWT via refresh-token endpoint");
        try {
            AngelLoginResponse response = restClient.post()
                    .uri(cfg.getBaseUrl() + REFRESH_PATH)
                    .headers(h -> AngelHeaders.apply(h, cfg.getApiKey(), null))
                    .body(Map.of("refreshToken", refreshToken))
                    .retrieve()
                    .body(AngelLoginResponse.class);

            if (response == null || !response.status() || response.data() == null
                    || response.data().jwtToken() == null) {
                String msg = response == null ? "null response" : response.message();
                log.warn("Refresh-token renewal returned no JWT: {}", msg);
                return null;
            }

            cachedJwt          = response.data().jwtToken();
            // Angel returns a NEW refresh token on renewal — rotate it.
            cachedRefreshToken = response.data().refreshToken() != null
                    ? response.data().refreshToken() : refreshToken;
            if (response.data().feedToken() != null) cachedFeedToken = response.data().feedToken();
            tokenExpiresAt     = Instant.now().plus(TOKEN_TTL);
            log.info("Angel JWT refreshed OK, valid until {}", tokenExpiresAt);
            persistTokens(cfg.getClientCode());
            return cachedJwt;
        } catch (Exception e) {
            log.warn("Refresh-token renewal threw: {}", e.getMessage());
            return null;
        }
    }

    private String login() {
        BrokerProperties.Angel cfg = brokerProperties.getAngel();
        if (!cfg.isEnabled()) {
            throw new IllegalStateException("Angel broker is disabled in config");
        }
        if (isBlank(cfg.getApiKey()) || isBlank(cfg.getClientCode())
                || isBlank(cfg.getPassword()) || isBlank(cfg.getTotpSecret())) {
            throw new IllegalStateException(
                    "Angel credentials missing. Set ANGEL_API_KEY, ANGEL_CLIENT_CODE, ANGEL_PASSWORD, ANGEL_TOTP_SECRET.");
        }

        String totp = TotpGenerator.generate(cfg.getTotpSecret());
        log.info("Logging in to Angel One as client {} (fresh TOTP)", cfg.getClientCode());

        AngelLoginResponse response = restClient.post()
                .uri(cfg.getBaseUrl() + LOGIN_PATH)
                .headers(h -> AngelHeaders.apply(h, cfg.getApiKey(), null))
                .body(Map.of(
                        "clientcode", cfg.getClientCode(),
                        "password", cfg.getPassword(),
                        "totp", totp
                ))
                .retrieve()
                .body(AngelLoginResponse.class);

        if (response == null || !response.status() || response.data() == null) {
            String msg = response == null ? "null response" : response.message();
            throw new IllegalStateException("Angel login failed: " + msg);
        }

        cachedJwt          = response.data().jwtToken();
        cachedRefreshToken = response.data().refreshToken();
        cachedFeedToken    = response.data().feedToken();
        tokenExpiresAt     = Instant.now().plus(TOKEN_TTL);
        log.info("Angel login OK, JWT cached until {}", tokenExpiresAt);
        persistTokens(cfg.getClientCode());
        return cachedJwt;
    }

    private void persistTokens(String clientCode) {
        if (tokenPersistence == null || clientCode == null) return;
        tokenPersistence.save(clientCode, cachedJwt, cachedRefreshToken, cachedFeedToken, tokenExpiresAt);
    }

    /**
     * Ends the Angel session on their servers and clears both caches.
     * Safe to call when not logged in — becomes a no-op.
     */
    public synchronized void logout() {
        if (cachedJwt == null) {
            return;
        }
        BrokerProperties.Angel cfg = brokerProperties.getAngel();
        String jwt = cachedJwt;
        try {
            restClient.post()
                    .uri(cfg.getBaseUrl() + LOGOUT_PATH)
                    .headers(h -> AngelHeaders.apply(h, cfg.getApiKey(), jwt))
                    .body(Map.of("clientcode", cfg.getClientCode()))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Angel session logged out for client {}", cfg.getClientCode());
        } catch (Exception e) {
            log.warn("Angel logout call failed (clearing local token anyway): {}", e.getMessage());
        } finally {
            cachedJwt          = null;
            cachedRefreshToken = null;
            tokenExpiresAt     = null;
            if (tokenPersistence != null && cfg.getClientCode() != null) {
                tokenPersistence.delete(cfg.getClientCode());
            }
        }
    }

    /**
     * Called by Spring on JVM shutdown.
     *
     * We deliberately do NOT call Angel logout here — that would invalidate
     * the JWT on Angel's side, forcing the next boot to burn a TOTP.
     * The token stays valid on Angel until its natural expiry and stays
     * cached in our DB, so restarts skip re-login.
     */
    @PreDestroy
    public void onShutdown() {
        if (cachedJwt != null && tokenExpiresAt != null) {
            log.info("App shutting down — Angel tokens kept in DB (JWT valid until {}), next boot will reuse",
                    tokenExpiresAt);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
