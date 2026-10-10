package com.angle.trading.security;

import com.angle.trading.config.RateLimitProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Token-bucket rate limiter in front of /api/**.
 *
 * One bucket per caller key. The key is computed from
 * {@link RateLimitProperties#getKeyStrategy()} — defaults to username+ip.
 *
 * Response headers written on every hit:
 *   X-RateLimit-Limit      — bucket capacity
 *   X-RateLimit-Remaining  — tokens left after this request
 *
 * On 429:
 *   Retry-After header (seconds) tells clients how long to back off.
 *
 * Note: this filter runs AFTER Spring Security auth on the apiFilterChain —
 * we rely on the Authentication being present to extract username. For an
 * unauthenticated hit we fall back to IP-only — but Spring Security would
 * have already returned 401 before this filter runs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties props;
    private final AntPathMatcher matcher = new AntPathMatcher();

    /** Bucket cache. 1-hour idle → bucket GC'd (fine; refills from empty). */
    private Cache<String, Bucket> buckets;

    @PostConstruct
    void init() {
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofHours(1))
                .maximumSize(10_000)
                .build();
        log.info("RateLimitFilter ready — enabled={} capacity={} refill={}/{}s key={} exempt={}",
                props.isEnabled(),
                props.getCapacity(),
                props.getRefillTokens(),
                props.getRefillPeriodSeconds(),
                props.getKeyStrategy(),
                props.getExemptPatterns());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!props.isEnabled()) return true;
        String path = request.getRequestURI();
        // Only /api/** is in scope; everything else passes through.
        if (!path.startsWith("/api/")) return true;
        // Exempt list — SSE streams and the like.
        for (String pattern : props.getExemptPatterns()) {
            if (matcher.match(pattern, path)) return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {

        String key = resolveKey(request);
        Bucket bucket = buckets.get(key, k -> newBucket());

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        response.setHeader("X-RateLimit-Limit", String.valueOf(props.getCapacity()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, probe.getRemainingTokens())));

        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }

        long retryAfterSec = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
        if (retryAfterSec < 1) retryAfterSec = 1;

        log.warn("Rate limit hit for key={} path={} — 429 (retry in {}s)",
                key, request.getRequestURI(), retryAfterSec);

        response.setStatus(429);  // Too Many Requests — not a constant in Jakarta Servlet 6
        response.setHeader("Retry-After", String.valueOf(retryAfterSec));
        response.setContentType("application/json");
        response.getWriter().write(String.format(
                "{\"status\":429,\"error\":\"Too Many Requests\"," +
                "\"message\":\"API rate limit exceeded. Retry in %d seconds.\"," +
                "\"retryAfterSeconds\":%d}",
                retryAfterSec, retryAfterSec));
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(
                props.getCapacity(),
                Refill.intervally(props.getRefillTokens(),
                                   Duration.ofSeconds(props.getRefillPeriodSeconds())));
        return Bucket.builder().addLimit(limit).build();
    }

    private String resolveKey(HttpServletRequest request) {
        String user = currentUsername();
        String ip = clientIp(request);
        return switch (props.getKeyStrategy()) {
            case USER        -> user == null ? "anon:" + ip : "u:" + user;
            case IP          -> "ip:" + ip;
            case USER_AND_IP -> (user == null ? "anon" : user) + ":" + ip;
        };
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        String name = auth.getName();
        return "anonymousUser".equals(name) ? null : name;
    }

    private static String clientIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
