package notification.core.artifact;

import notification.core.recipient.Recipient;

import java.util.Map;

public class DefaultPersonalizationArtifact
        implements PersonalizationArtifact {

    private final Map<String, Map<String, String>> store;

    public DefaultPersonalizationArtifact(
            Map<String, Map<String, String>> store) {
        this.store = store;
    }

    @Override
    public Map<String, String> variablesFor(Recipient recipient) {
        return store.getOrDefault(recipient.id(), Map.of());
    }
}
