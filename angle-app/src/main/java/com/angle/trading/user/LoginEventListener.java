package com.angle.trading.user;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Hooks Spring Security's authentication events:
 *   • AuthenticationSuccessEvent         — reset fail counter, update last_login_at,
 *                                           write SUCCESS audit row
 *   • AuthenticationFailureBadCredentials — increment fail counter (lockout),
 *                                           write BAD_CREDENTIALS audit row
 *
 * The failure handler ({@code CustomAuthenticationFailureHandler}) also writes
 * audit rows for LOCKED / DISABLED cases where it has fuller context. We only
 * handle BAD_CREDENTIALS here because that's the event Spring fires for a
 * password mismatch — LockedException/DisabledException publish different
 * subclasses that we route via the failure handler instead.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginEventListener {

    private final UserService userService;
    private final LoginAuditService auditService;

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        if (username == null || username.isBlank()) return;
        try {
            userService.updateLastLogin(username);
        } catch (Exception e) {
            log.debug("Failed to update last-login for {}: {}", username, e.getMessage());
        }
        try {
            HttpServletRequest req = currentRequest();
            auditService.record(username, true, null, clientIp(req), userAgent(req));
        } catch (Exception e) {
            log.debug("Audit write failed for success {}: {}", username, e.getMessage());
        }
    }

    /**
     * Fires when remember-me resume succeeds. AuthenticationSuccessEvent does
     * NOT fire for cookie resumes, so without this listener auto-logins would
     * be invisible in the audit log and last_login_at would never update.
     */
    @EventListener
    public void onInteractiveSuccess(InteractiveAuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        if (username == null || username.isBlank()) return;
        // Only hop in for remember-me — form login already triggers
        // AuthenticationSuccessEvent, double-writing is pointless.
        boolean isRememberMe = event.getGeneratedBy() != null
                && event.getGeneratedBy().getSimpleName().contains("RememberMe");
        if (!isRememberMe) return;
        try {
            userService.updateLastLogin(username);
        } catch (Exception e) {
            log.debug("Failed to update last-login (remember-me) for {}: {}", username, e.getMessage());
        }
        try {
            HttpServletRequest req = currentRequest();
            auditService.record(username, true, null, clientIp(req), userAgent(req));
        } catch (Exception e) {
            log.debug("Audit write failed for remember-me success {}: {}", username, e.getMessage());
        }
    }

    @EventListener
    public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        String username = extractUsername(event);
        if (username == null || username.isBlank()) return;
        try {
            int count = userService.recordFailedLogin(username);
            if (count > 0) {
                log.info("Failed login for {} (attempt #{})", username, count);
            }
        } catch (Exception e) {
            log.debug("Failed to record bad-credentials for {}: {}", username, e.getMessage());
        }
        // Audit row is written by CustomAuthenticationFailureHandler where
        // IP+UA are already available on the request — avoid double writes.
    }

    private static String extractUsername(AuthenticationFailureBadCredentialsEvent event) {
        try {
            Object principal = event.getAuthentication().getPrincipal();
            return principal == null ? null : principal.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs == null ? null : attrs.getRequest();
        } catch (Exception e) {
            return null;
        }
    }

    private static String clientIp(HttpServletRequest req) {
        if (req == null) return "unknown";
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    private static String userAgent(HttpServletRequest req) {
        return req == null ? null : req.getHeader("User-Agent");
    }
}
