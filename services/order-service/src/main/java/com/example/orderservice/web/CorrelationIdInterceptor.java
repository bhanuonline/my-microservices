package com.example.orderservice.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

// Propagates a correlation ID across the request lifecycle.
// If the caller sends X-Correlation-ID, we reuse it (useful for tracing across services).
// If not, we generate one. The ID is:
//   - added to MDC  → every log.info/warn/error in this request automatically includes it
//   - echoed back in the response header → caller can correlate their logs with ours
@Component
public class CorrelationIdInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String correlationId = request.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);   // echo back so caller can correlate
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        MDC.remove(MDC_KEY);   // always clean up MDC — threads are reused from the pool
    }
}
