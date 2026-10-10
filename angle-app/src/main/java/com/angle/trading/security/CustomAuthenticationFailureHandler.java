package com.angle.trading.security;

import com.angle.trading.config.SecurityLockoutProperties;
import com.angle.trading.user.LoginAuditService;
import com.angle.trading.user.UserService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Routes login failures to a login-page URL that carries enough info for
 * the view to show a specific message:
 *
 *   /auth/login?error                       → generic "Invalid credentials"
 *   /auth/login?error&locked&minutes=14     → "Account locked for 14 min"
 *   /auth/login?error&ipBlocked             → "Too many attempts from your IP"
 *
 * Side effects
 *   • Increments the Caffeine per-IP failure counter (second lockout gate).
 *   • Writes a row to {@code user_login_audit} tagged with the failure reason
 *     (BAD_CREDENTIALS / LOCKED / DISABLED / NOT_FOUND / UNKNOWN).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final UserService userService;
    private final SecurityLockoutProperties lockoutProps;
    private final LoginAuditService auditService;

    /** Per-IP failure counter with a sliding TTL equal to the window length. */
    private Cache<String, AtomicInteger> ipFailures;

    @PostConstruct
    void init() {
        this.ipFailures = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(lockoutProps.getIp().getWindowMinutes()))
                .maximumSize(10_000)
                .build();
        log.info("LoginFailureHandler ready — lockout.enabled={} maxAttempts={} lockMinutes={} ipThrottle={} ipMax={}/{}min",
                lockoutProps.isEnabled(),
                lockoutProps.getMaxAttempts(),
                lockoutProps.getLockDurationMinutes(),
                lockoutProps.getIp().isEnabled(),
                lockoutProps.getIp().getMaxAttemptsPerWindow(),
                lockoutProps.getIp().getWindowMinutes());
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {

        String username = request.getParameter("username");
        String ip = clientIp(request);
        String ua = request.getHeader("User-Agent");

        // Per-IP gate: count this failure, maybe block.
        boolean ipBlocked = false;
        if (lockoutProps.isEnabled() && lockoutProps.getIp().isEnabled()) {
            int count = ipFailures.get(ip, k -> new AtomicInteger()).incrementAndGet();
            if (count > lockoutProps.getIp().getMaxAttemptsPerWindow()) {
                ipBlocked = true;
                log.warn("IP {} exceeded login-failure window ({} > {})",
                        ip, count, lockoutProps.getIp().getMaxAttemptsPerWindow());
            }
        }

        // Classify so audit + UI both get the right reason.
        String reason = classify(exception);
        boolean accountLocked = (exception instanceof LockedException);

        // Belt-and-braces: BadCredentials counter may have just crossed the
        // threshold on THIS attempt. Ask the service.
        if (!accountLocked && username != null && !username.isBlank()
                && lockoutProps.isEnabled() && userService.isLocked(username)) {
            accountLocked = true;
            reason = LoginAuditService.REASON_LOCKED;
        }

        // Audit the failure (don't let it break the response).
        try {
            auditService.record(username == null ? "" : username, false, reason, ip, ua);
        } catch (Exception e) {
            log.debug("Audit write failed: {}", e.getMessage());
        }

        StringBuilder url = new StringBuilder("/auth/login?error");
        if (ipBlocked) {
            url.append("&ipBlocked");
        } else if (accountLocked && username != null) {
            long mins = userService.minutesUntilUnlock(username);
            url.append("&locked&minutes=").append(mins);
        }
        if (username != null && !username.isBlank()) {
            url.append("&username=")
               .append(URLEncoder.encode(username, StandardCharsets.UTF_8));
        }
        response.sendRedirect(url.toString());
    }

    private static String classify(AuthenticationException ex) {
        if (ex instanceof LockedException)             return LoginAuditService.REASON_LOCKED;
        if (ex instanceof DisabledException)           return LoginAuditService.REASON_DISABLED;
        if (ex instanceof UsernameNotFoundException)   return LoginAuditService.REASON_NOT_FOUND;
        // BadCredentialsException + anything else maps to BAD_CREDENTIALS by default.
        return LoginAuditService.REASON_BAD_CREDENTIALS;
    }

    private static String clientIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
