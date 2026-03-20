package notification.core.artifact;

import notification.core.recipient.Recipient;

import java.util.Map;

public interface PersonalizationArtifact extends Artifact {
    Map<String, String> variablesFor(Recipient recipient);
}
