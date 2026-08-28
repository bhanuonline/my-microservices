package com.angle.trading.broker.angel;

import com.angle.trading.broker.angel.dto.AngelLoginResponse;
import com.angle.trading.config.BrokerProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Handles Angel One SmartAPI login and caches the JWT token in memory.
 *
 * Flow:
 *   1. Generate a fresh TOTP from the secret.
 *   2. POST client-code + password + totp to loginByPassword endpoint.
 *   3. Cache the returned JWT for ~7 hours (Angel tokens are valid ~8h).
 *   4. On the next call within TTL, return the cached token.
 *
 * NOTE: This is in-memory only — restart = re-login. For production you'd
 * persist the token (e.g. Redis) and refresh proactively before expiry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AngelAuthService {

    private static final String LOGIN_PATH  = "/rest/auth/angelbroking/user/v1/loginByPassword";
    private static final String LOGOUT_PATH = "/rest/secure/angelbroking/user/v1/logout";
    private static final Duration TOKEN_TTL = Duration.ofHours(7);

    private final RestClient restClient;
    private final BrokerProperties brokerProperties;

    private volatile String cachedJwt;
    private volatile Instant tokenExpiresAt;

    /** Returns a valid JWT, logging in first if we don't have one yet. */
    public synchronized String getJwtToken() {
        if (cachedJwt != null && tokenExpiresAt != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedJwt;
        }
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

        cachedJwt = response.data().jwtToken();
        tokenExpiresAt = Instant.now().plus(TOKEN_TTL);
        log.info("Angel login OK, JWT cached until {}", tokenExpiresAt);
        return cachedJwt;
    }

    /**
     * Ends the Angel session on their servers and clears the local cache.
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
        }
    }

    /**
     * Spring calls this automatically on application shutdown so the Angel
     * session is closed cleanly instead of lingering until natural expiry.
     */
    @PreDestroy
    public void onShutdown() {
        logout();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
