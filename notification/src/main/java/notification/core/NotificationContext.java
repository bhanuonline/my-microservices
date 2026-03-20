package notification.core;

import lombok.Builder;
import lombok.Data;
import notification.core.artifact.ArtifactStore;

import java.util.HashMap;
import java.util.Map;

/**
 * 📌 Context is mostly immutable
 * 📌 Only metadata remains mutable (intentionally)
 */
@Builder
@Data
public class NotificationContext {
    private final NotificationType type;
    private final NotificationTarget target;
    private final String message;

    /**
     * Shared mutable data across the pipeline
     * Used to store intermediate & final artifacts
     *
     * ❌ Problem with Map<String, Object>
     * Runtime casting
     * No compile-time safety
     * Hard to refactor
     * Easy to misuse
     */
    @Builder.Default
    //private Map<String, Object> metadata = new HashMap<>();
    //📌 No string keys
    //📌 Compile-time discoverability
    private final ArtifactStore artifacts = new ArtifactStore();
}
