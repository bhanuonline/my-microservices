package com.example.auth.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Sits in Spring Security's login flow so we can count wins/losses per user.
 *
 * <p>Spring Security fires two hooks after every POST /login:
 * <ul>
 *   <li>{@link AuthenticationSuccessHandler#onAuthenticationSuccess} — password matched</li>
 *   <li>{@link AuthenticationFailureHandler#onAuthenticationFailure} — didn't match
 *       (BadCredentialsException) OR the account was already locked (LockedException)</li>
 * </ul>
 *
 * <p>This class implements BOTH so we get a single side-effect point:
 * <pre>
 *   success → tracker.onSuccess(username) → clear counter → redirect to /
 *   failure → tracker.onFailure(username) → bump counter, maybe lock →
 *               redirect to /login?error (or /login?locked if account is locked)
 * </pre>
 *
 * <p>Why the LockedException path is separate: when a user is already locked,
 * Spring throws LockedException BEFORE checking the password. We route those to
 * a distinct URL so the login page can show "account is locked" instead of
 * "bad credentials" — clearer UX for the user (and attacker gets the same
 * info they'd have gotten anyway).
 *
 * <p>Wired into the default filter chain via ObjectProvider in
 * {@code SecurityConfig.defaultSecurityFilterChain}. On inmemory profile the
 * bean is absent and Spring's default redirects are used.
 */
@Component
@Profile("jdbc")
public class LockoutAuthenticationHandler
        implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(LockoutAuthenticationHandler.class);

    private final LockoutTracker tracker;
    private final AuthenticationSuccessHandler successDelegate;
    private final AuthenticationFailureHandler failureDelegate;
    private final AuthenticationFailureHandler lockedDelegate;

    public LockoutAuthenticationHandler(LockoutTracker tracker) {
        this.tracker = tracker;
        this.successDelegate = new SavedRequestAwareAuthenticationSuccessHandler();
        this.failureDelegate = new SimpleUrlAuthenticationFailureHandler("/login?error");
        this.lockedDelegate = new SimpleUrlAuthenticationFailureHandler("/login?locked");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication)
            throws IOException, ServletException {
        tracker.onSuccess(authentication.getName());
        successDelegate.onAuthenticationSuccess(request, response, authentication);
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception)
            throws IOException, ServletException {
        String username = request.getParameter("username");
        if (username != null && !username.isBlank()) {
            tracker.onFailure(username);
        }
        // Spring throws LockedException BEFORE checking password, when isAccountNonLocked returns false.
        // Route to a distinct URL so the login page can show a specific message.
        if (exception instanceof LockedException) {
            log.warn("Login blocked: account locked for username={}", username);
            lockedDelegate.onAuthenticationFailure(request, response, exception);
        } else {
            failureDelegate.onAuthenticationFailure(request, response, exception);
        }
    }
}
