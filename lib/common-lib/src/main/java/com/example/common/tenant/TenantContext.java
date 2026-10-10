package com.example.common.tenant;

/**
 * Per-thread holder for the currently-processing tenant.
 *
 * Populated by {@link TenantContextFilter} at the top of every request from
 * the {@code X-Tenant-Id} header (injected by api-gateway from the JWT
 * {@code tenant_id} claim). Read by anything that needs tenant isolation:
 * JPA filters, metrics tags, cache keys, log MDC.
 *
 * Caveats:
 *  - ThreadLocal doesn't propagate to @Async or Project Reactor pipelines
 *    without explicit copying. Use {@link #snapshot()} to grab the current
 *    value and {@link #runAs} to apply it on another thread.
 *  - Always clear in a finally; the filter does this for request threads.
 */
public final class TenantContext {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    /** Reserved tenant id used when no header is present — never persist this. */
    public static final String ANONYMOUS = "anonymous";

    private TenantContext() {}

    public static void set(String tenantId) {
        HOLDER.set(tenantId == null || tenantId.isBlank() ? ANONYMOUS : tenantId);
    }

    /** @return the current tenant id, or {@link #ANONYMOUS} if nothing is set. */
    public static String get() {
        String v = HOLDER.get();
        return v != null ? v : ANONYMOUS;
    }

    public static String snapshot() { return HOLDER.get(); }

    public static void clear() { HOLDER.remove(); }

    /** Run {@code task} with {@code tenantId} as the current tenant, restoring on exit. */
    public static void runAs(String tenantId, Runnable task) {
        String prev = HOLDER.get();
        try {
            set(tenantId);
            task.run();
        } finally {
            if (prev == null) HOLDER.remove(); else HOLDER.set(prev);
        }
    }
}
