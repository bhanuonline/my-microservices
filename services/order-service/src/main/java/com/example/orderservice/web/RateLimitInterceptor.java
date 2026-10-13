package com.example.orderservice.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// Simple in-process per-IP rate limiter.
// Uses a sliding window: each IP gets a counter that resets every window (default 60s).
// For production use Spring Cloud Gateway's Redis-backed rate limiter instead —
// this one doesn't survive restarts or work across multiple instances.
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    @Value("${orderservice.ratelimit.max-requests-per-window:30}")
    private int maxRequests;

    @Value("${orderservice.ratelimit.window-seconds:60}")
    private long windowSeconds;

    // key = client IP, value = (counter, window-start-epoch-seconds)
    private final ConcurrentHashMap<String, long[]> counters = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String ip = getClientIp(request);
        long now = Instant.now().getEpochSecond();

        long[] state = counters.compute(ip, (key, existing) -> {
            if (existing == null || now - existing[1] >= windowSeconds) {
                return new long[]{1, now};      // new window: count=1, start=now
            }
            existing[0]++;
            return existing;
        });

        long count = state[0];
        response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, maxRequests - count)));

        if (count > maxRequests) {
            log.warn("Rate limit exceeded for IP={} count={}/{}", ip, count, maxRequests);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Rate limit exceeded. Max " + maxRequests + " requests per " + windowSeconds + "s.\"}");
            return false;
        }
        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();   // first IP in the chain = original client
        }
        return request.getRemoteAddr();
    }
}
