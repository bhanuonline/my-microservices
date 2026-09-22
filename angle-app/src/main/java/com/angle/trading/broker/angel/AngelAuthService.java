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
 * Handles Angel One SmartAPI login and caches the JWT token.
 *
 * Two-tier cache:
 *   1. In-memory volatile — fastest, used per-call within a JVM run
 *   2. Database (angel_token table) — survives restarts so we don't burn
 *      a TOTP re-login every time the app starts
 *
 * Resolution flow on {@link #getJwtToken()}:
 *   1. Return in-memory token if still valid
 *   2. Else try DB — hydrate memory + return if still valid
 *   3. Else generate TOTP, POST login, save token to both caches
 *
 * NOTE: The persistence service is Spring-injected; if MySQL is down at
 * boot the app still runs — auth just falls back to per-restart re-login.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AngelAuthService {

    private static final String LOGIN_PATH  = "/rest/auth/angelbroking/user/v1/loginByPassword";
    private static final String LOGOUT_PATH = "/rest/secure/angelbroking/user/v1/logout";
    private static final Duration TOKEN_TTL = Duration.ofHours(7);   // Angel says ~8h; be conservative

    private final RestClient restClient;
    private final BrokerProperties brokerProperties;
    private final AngelTokenPersistenceService tokenPersistence;

    private volatile String cachedJwt;
    private volatile Instant tokenExpiresAt;

    /** Returns a valid JWT, hydrating from DB / logging in as needed. */
    public synchronized String getJwtToken() {
        // Tier 1 — in-memory
        if (cachedJwt != null && tokenExpiresAt != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedJwt;
        }

        // Tier 2 — DB (only relevant right after a restart)
        String clientCode = brokerProperties.getAngel().getClientCode();
        if (tokenPersistence != null && clientCode != null && !clientCode.isBlank()) {
            Optional<CachedToken> saved = tokenPersistence.load(clientCode);
            if (saved.isPresent()) {
                cachedJwt      = saved.get().jwt();
                tokenExpiresAt = saved.get().expiresAt();
                log.info("Loaded cached Angel JWT from DB — valid until {}", tokenExpiresAt);
                return cachedJwt;
            }
        }

        // Tier 3 — fresh login (burns a TOTP)
        return login();
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
        log.info("Logging in to Angel One as client {}", cfg.getClientCode());

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

        cachedJwt      = response.data().jwtToken();
        tokenExpiresAt = Instant.now().plus(TOKEN_TTL);
        log.info("Angel login OK, JWT cached until {}", tokenExpiresAt);

        // Persist so a restart can skip re-login.
        if (tokenPersistence != null) {
            tokenPersistence.save(cfg.getClientCode(), cachedJwt, tokenExpiresAt);
        }
        return cachedJwt;
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
            cachedJwt = null;
            tokenExpiresAt = null;
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
     *
     * Call {@link #logout()} explicitly (via a REST endpoint or a stop
     * command) when you truly want to end the Angel session.
     */
    @PreDestroy
    public void onShutdown() {
        if (cachedJwt != null && tokenExpiresAt != null) {
            log.info("App shutting down — Angel JWT kept in DB (valid until {}), next boot will reuse it",
                    tokenExpiresAt);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
