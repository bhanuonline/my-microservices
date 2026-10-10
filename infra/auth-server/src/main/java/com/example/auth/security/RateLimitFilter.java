package com.example.auth.security;

import com.example.auth.config.FeatureFlags;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Base64;

/**
 * Servlet filter that gates {@code /login} and {@code /oauth2/token} to prevent
 * flood attacks.
 *
 * <p>Runs BEFORE Spring Security's chain — that's the whole point. If a burst
 * of 500 requests comes in trying to brute-force a password, we want to reject
 * most of them (return 429) without spending CPU on BCrypt or JWT signing.
 *
 * <p>Per-request flow:
 * <pre>
 *   1. Feature flag off? → pass through, do nothing.
 *   2. Not POST /login or POST /oauth2/token? → pass through.
 *   3. Ask RateLimitService: "does this IP/client have a token in the bucket?"
 *   4. Token available → consume it, pass through.
 *   5. Bucket empty → respond 429 + Retry-After header, no downstream call.
 * </pre>
 *
 * <p>Why NOT {@code @Component}: Boot would auto-register it as a filter but we
 * couldn't control the order (Boot picks the default). {@link RateLimitFilterRegistration}
 * builds this filter explicitly with {@code Ordered.HIGHEST_PRECEDENCE} so it
 * runs before every Spring Security filter, including CSRF.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimitService service;
    private final FeatureFlags flags;
    private final com.example.auth.metrics.AuthMetrics metrics;

    public RateLimitFilter(RateLimitService service, FeatureFlags flags,
                           com.example.auth.metrics.AuthMetrics metrics) {
        this.service = service;
        this.flags = flags;
        this.metrics = metrics;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        if (!flags.getRateLimit().isEnabled()) {
            chain.doFilter(req, res);
            return;
        }

        Long retryAfter = null;
        String path = req.getRequestURI();

        if ("POST".equals(req.getMethod())) {
            if ("/login".equals(path)) {
                retryAfter = service.tryLogin(extractIp(req));
            } else if ("/oauth2/token".equals(path)) {
                retryAfter = service.tryToken(extractTokenKey(req));
            }
        }

        if (retryAfter != null) {
            log.warn("Rate limited: {} {} ip={} retryAfter={}s",
                    req.getMethod(), path, extractIp(req), retryAfter);
            metrics.rateLimitDenied(path);
            deny(res, retryAfter);
            return;
        }
        chain.doFilter(req, res);
    }

    private String extractIp(HttpServletRequest req) {
        if (flags.getRateLimit().isHonorXForwardedFor()) {
            String h = req.getHeader("X-Forwarded-For");
            if (h != null && !h.isBlank()) {
                // first hop only
                int comma = h.indexOf(',');
                return comma > 0 ? h.substring(0, comma).trim() : h.trim();
            }
        }
        return req.getRemoteAddr();
    }

    /** For /oauth2/token, prefer client_id from Basic auth; fall back to IP. */
    private String extractTokenKey(HttpServletRequest req) {
        String basic = req.getHeader("Authorization");
        if (basic != null && basic.startsWith("Basic ")) {
            try {
                String creds = new String(Base64.getDecoder().decode(basic.substring(6)));
                int colon = creds.indexOf(':');
                if (colon > 0) return "client:" + creds.substring(0, colon);
            } catch (IllegalArgumentException ignored) {
                // malformed header — fall through to IP
            }
        }
        return "ip:" + extractIp(req);
    }

    private void deny(HttpServletResponse res, long retryAfterSec) throws IOException {
        res.setStatus(429); // Too Many Requests — jakarta.servlet lacks SC_ constant for it
        res.setHeader("Retry-After", String.valueOf(retryAfterSec));
        res.setContentType("application/json");
        res.getWriter().write(String.format(
                "{\"error\":\"rate_limited\",\"retry_after\":%d}", retryAfterSec));
    }
}
