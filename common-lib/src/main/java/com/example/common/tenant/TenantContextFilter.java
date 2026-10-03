package com.example.common.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads the {@code X-Tenant-Id} header (injected by api-gateway's
 * AddTenantHeader filter from the JWT {@code tenant_id} claim) and stashes
 * it in {@link TenantContext} + log MDC for the lifetime of the request.
 *
 * Services that don't need tenant awareness still get the correlation key in
 * logs (set to "anonymous" when the header is missing) — low cost, high value
 * when correlating cross-service logs.
 */
public class TenantContextFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Tenant-Id";
    public static final String MDC_KEY = "tenantId";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String tenantId = req.getHeader(HEADER);
        TenantContext.set(tenantId);
        MDC.put(MDC_KEY, TenantContext.get());
        try {
            chain.doFilter(req, res);
        } finally {
            TenantContext.clear();
            MDC.remove(MDC_KEY);
        }
    }
}
