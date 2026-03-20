package notification.core.artifact;

import notification.core.template.TemplateContext;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ArtifactStore {

    private final Map<Class<? extends Artifact>, Artifact> store = new HashMap<>();

    public <T extends Artifact> void put(T artifact) {
        store.put(artifact.getClass(), artifact);
    }

    @SuppressWarnings("unchecked")
    public <T extends Artifact> T get(Class<T> type) {
        return (T) store.get(type);
    }

    public boolean contains(Class<? extends Artifact> type) {
        return store.containsKey(type);
    }


    // ✅ Get or default (THIS is what you asked)
    @SuppressWarnings("unchecked")
    public <T extends Artifact> T getOrDefault(
            Class<T> type,
            Supplier<T> defaultSupplier) {

        return (T) store.computeIfAbsent(
                type,
                k -> defaultSupplier.get()
        );
    }

}
