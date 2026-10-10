package com.example.apigateway.dynamicroutes;

import java.time.Instant;

/**
 * Cross-instance route-refresh notification sent over Redis pub/sub.
 *
 * Keep it small and versionable:
 *   sourceId  → per-JVM id; receivers skip messages matching their own (self-echo filter)
 *   timestamp → for logs / debugging
 *   action    → REFRESH | UPSERT | DELETE (currently only REFRESH is emitted)
 */
public record RouteRefreshMessage(
        String sourceId,
        Instant timestamp,
        String action
) {
    public static RouteRefreshMessage refresh(String sourceId) {
        return new RouteRefreshMessage(sourceId, Instant.now(), "REFRESH");
    }
}
