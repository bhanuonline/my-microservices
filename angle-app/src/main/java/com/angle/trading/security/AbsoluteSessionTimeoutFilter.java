package com.angle.trading.security;

import com.angle.trading.config.SessionProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Enforces an ABSOLUTE session cap on top of the idle timeout.
 *
 *   • Servlet idle timeout alone lets a session live forever as long as
 *     the user keeps clicking — a stolen cookie never expires that way.
 *   • This filter stamps {@code SESSION_LOGIN_TIME} on first use and
 *     invalidates the session once {@code session.absolute-timeout-minutes}
 *     has elapsed, regardless of activity.
 *
 * On invalidation we clear the SecurityContext and redirect to the
 * configured invalid-session URL (same place Spring Security sends a user
 * whose session cookie no longer exists).
 *
 * Disabled when {@code session.absolute-timeout-minutes = 0}.
 */
@Slf4j
@Order(1)
@Component
@RequiredArgsConstructor
public class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

    public static final String SESSION_LOGIN_TIME_ATTR = "SESSION_LOGIN_TIME";

    private final SessionProperties sessionProps;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {

        int capMin = sessionProps.getAbsoluteTimeoutMinutes();
        if (capMin <= 0) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            chain.doFilter(request, response);
            return;
        }

        Long loginTime = (Long) session.getAttribute(SESSION_LOGIN_TIME_ATTR);
        if (loginTime == null) {
            // First time this filter sees the session — stamp it. We prefer the
            // session's creation time to "now" so a session that pre-dates a
            // deploy still ages correctly.
            loginTime = session.getCreationTime();
            session.setAttribute(SESSION_LOGIN_TIME_ATTR, loginTime);
        }

        long ageMillis = System.currentTimeMillis() - loginTime;
        long capMillis = capMin * 60_000L;

        if (ageMillis > capMillis) {
            String username = SecurityContextHolder.getContext().getAuthentication() == null
                    ? "unknown"
                    : SecurityContextHolder.getContext().getAuthentication().getName();
            log.info("Session for {} exceeded absolute cap ({} min) — invalidating",
                    username, capMin);
            SecurityContextHolder.clearContext();
            session.invalidate();
            response.sendRedirect(sessionProps.getInvalidSessionUrl());
            return;
        }

        chain.doFilter(request, response);
    }
}
