package notification.core.context;

import lombok.AllArgsConstructor;
import lombok.Getter;
import notification.core.artifact.Artifact;

@Getter
@AllArgsConstructor
public class TemplateContext implements Artifact {
    private final String version; // v1, v2
    // ✅ REQUIRED for method reference
    public static TemplateContext defaultContext() {
        return new TemplateContext("v1");
    }
}
