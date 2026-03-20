package notification.core.feature;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "features.email")
@Getter
@Setter
public class EmailFeatureFlags {
    private boolean batchEnabled;
    private int batchThreshold;
}
