package notification.core.template;

import lombok.Value;

@Value
public class TemplateContext {
    String version;   // e.g. "v2"
    public static TemplateContext defaultContext() {
        return new TemplateContext("v1");
    }
}
