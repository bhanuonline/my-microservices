package notification.core.exception;

import notification.core.NotificationType;

public class TemplateNotFoundException extends RuntimeException {

    private final NotificationType type;
    private final String language;
    private final String version;
    private final String variant;

    public TemplateNotFoundException(
            NotificationType type,
            String language,
            String version,
            String variant) {

        super(buildMessage(type, language, version, variant));
        this.type = type;
        this.language = language;
        this.version = version;
        this.variant = variant;
    }

    private static String buildMessage(
            NotificationType type,
            String language,
            String version,
            String variant) {

        return String.format(
                "Template not found [type=%s, lang=%s, version=%s, variant=%s]",
                type, language, version, variant
        );
    }

    // Optional getters (useful for metrics / logging)
    public NotificationType getType() {
        return type;
    }

    public String getLanguage() {
        return language;
    }

    public String getVersion() {
        return version;
    }

    public String getVariant() {
        return variant;
    }
}
