package notification.dispatch;

import lombok.RequiredArgsConstructor;
import notification.core.NotificationContext;
import notification.core.feature.EmailFeatureFlags;
import notification.core.feature.FeatureFlagService;
import notification.dispatch.strategy.SendStrategy;
import notification.integration.email.model.EmailPayload;
import notification.integration.email.strategy.BatchEmailSendStrategy;
import notification.integration.email.strategy.IndividualEmailSendStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class EmailDispatchStrategyResolver {

    @Value("${email.batch.threshold:10}")
    private int batchThreshold;

    private final IndividualEmailSendStrategy individual;
    private final BatchEmailSendStrategy batch;
    //private final EmailFeatureFlags flags;
    private final FeatureFlagService featureFlagService;


    public SendStrategy<EmailPayload> resolve(List<EmailPayload> payloads, NotificationContext context) {
//        if (!flags.isBatchEnabled()) {
//            return individual;
//        }

//        if (!featureFlagService.isEnabled("email.batch.send",context)) {
//            return individual;
//        }
        boolean enabled = featureFlagService.isEnabled(
                "email.batch.send",
                context
        );
        if (!enabled) return individual;
        return payloads.size() > 10 ? batch : individual;
    }
}
