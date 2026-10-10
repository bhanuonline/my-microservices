package com.angle.trading.security;

import com.angle.trading.persistence.AppUserEntity;
import com.angle.trading.persistence.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Second-stage authentication gate.
 *
 * Runs on every form-login-chain request. If the current user has 2FA
 * enabled AND the session hasn't passed the 2FA step, redirect to
 * {@value #TWO_FA_PATH}. Any attempt to reach a protected page before
 * verifying the code bounces back here.
 *
 *   session.TWO_FA_PASSED = true   → chain.doFilter(req, res)
 *   session.TWO_FA_PASSED = false  → redirect to /auth/2fa
 *
 * Spring Security form-login already treats the user as authenticated at
 * this point (so they can POST /auth/2fa with CSRF), but every other
 * request is intercepted until the flag is set.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TwoFactorAuthenticationFilter extends OncePerRequestFilter {

    public static final String SESSION_TWO_FA_PASSED = "TWO_FA_PASSED";
    public static final String TWO_FA_PATH = "/auth/2fa";

    /** Paths that must be reachable WITHOUT 2FA verification. */
    private static final Set<String> EXEMPT_EXACT = Set.of(
            TWO_FA_PATH,
            "/auth/login",
            "/auth/signup",
            "/auth/access-denied",
            "/logout",
            "/favicon.ico"
    );

    private static final Set<String> EXEMPT_PREFIX = Set.of(
            "/css/", "/js/", "/images/", "/webjars/",
            "/auth/",     // all other /auth/* (verify-email, forgot, 2fa post)
            "/api/",      // API chain is separate; stateless Basic Auth
            "/actuator/"  // separate chain
    );

    private final AppUserRepository userRepo;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (EXEMPT_EXACT.contains(path)) return true;
        for (String p : EXEMPT_PREFIX) {
            if (path.startsWith(p)) return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            // Not logged in — let Spring Security's normal auth flow run.
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        if (session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_TWO_FA_PASSED))) {
            chain.doFilter(request, response);
            return;
        }

        // Check whether this user actually has 2FA switched on.
        // One DB hit per request on authenticated users — cheap, indexed, and
        // avoids caching user state in the session (always fresh).
        AppUserEntity u = userRepo.findByUsername(auth.getName()).orElse(null);
        if (u == null || !u.isTotpEnabled()) {
            // No 2FA for this user — mark session so we don't query again.
            if (session == null) session = request.getSession(true);
            session.setAttribute(SESSION_TWO_FA_PASSED, true);
            chain.doFilter(request, response);
            return;
        }

        log.debug("User {} needs 2FA — redirecting from {} to {}",
                auth.getName(), request.getRequestURI(), TWO_FA_PATH);
        response.sendRedirect(TWO_FA_PATH);
    }
}
