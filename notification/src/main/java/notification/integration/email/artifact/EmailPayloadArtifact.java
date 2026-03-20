package notification.integration.email.artifact;

import lombok.AllArgsConstructor;
import lombok.Getter;
import notification.core.artifact.Artifact;
import notification.integration.email.model.EmailPayload;

import java.util.List;

@Getter
@AllArgsConstructor
public class EmailPayloadArtifact implements Artifact {
    private final List<EmailPayload> payloads;
}
