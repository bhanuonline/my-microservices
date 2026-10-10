package com.example.common.logging;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter — populates the X-Correlation-Id header + MDC for every request.
 *
 * Reads incoming X-Correlation-Id (usually forwarded from the api-gateway).
 * If absent, generates a fresh UUID. Puts it into MDC under `correlationId` so
 * every log line for this request carries the id, and echoes it in the response
 * header so callers can correlate their side with our logs.
 *
 * Always cleans up MDC in `finally` — servlet threads are pooled, and a leaked
 * MDC entry would bleed correlation ids across unrelated requests.
 *
 * Registered automatically via CorrelationIdAutoConfiguration when common-lib is
 * on the classpath of a servlet-based Spring Boot app.
 */
public class CorrelationIdFilter implements Filter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) req;
        String cid = http.getHeader(HEADER);
        if (cid == null || cid.isBlank()) {
            cid = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, cid);
        ((HttpServletResponse) res).setHeader(HEADER, cid);
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
