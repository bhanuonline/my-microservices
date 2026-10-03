package com.example.common.featureflag;

/**
 * Everything needed to decide whether a flag is on for a specific caller.
 * Keep this trivial — if a flag needs richer context, resolve it at call-site.
 */
public record EvalContext(String userId, String tenantId) {

    public static EvalContext of(String userId, String tenantId) {
        return new EvalContext(userId, tenantId);
    }

    /** For call sites that only have one of the two. */
    public static EvalContext user(String userId)      { return new EvalContext(userId, null); }
    public static EvalContext tenant(String tenantId)  { return new EvalContext(null,  tenantId); }
    public static EvalContext anonymous()              { return new EvalContext(null, null); }
}
