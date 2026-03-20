package notification.integration.email.artifact;

import lombok.Getter;
import notification.core.artifact.Artifact;
import notification.core.recipient.Recipient;

import java.util.HashMap;
import java.util.Map;

@Getter
public class RetryArtifact implements Artifact {

    private final Map<String, Integer> retryCount = new HashMap<>();

    public int increment(Recipient recipient) {
        return retryCount.merge(recipient.id(), 1, Integer::sum);
    }

    public int getCount(Recipient recipient) {
        return retryCount.getOrDefault(recipient.id(), 0);
    }
}
