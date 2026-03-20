package notification.dispatch;

import lombok.RequiredArgsConstructor;
import notification.core.NotificationContext;
import notification.dispatch.strategy.SendStrategy;
import notification.integration.email.artifact.RetryArtifact;
import notification.integration.email.model.EmailPayload;
import notification.integration.email.strategy.BatchEmailSendStrategy;
import notification.integration.email.strategy.IndividualEmailSendStrategy;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class EmailDispatcher {

    private final IndividualEmailSendStrategy individualStrategy;
    private final BatchEmailSendStrategy batchStrategy;
    private final EmailDispatchStrategyResolver resolver;

    public void dispatch(List<EmailPayload> payloads, NotificationContext ctx) {
//        if (payloads.size() > 10) {
//            batchStrategy.send(payloads);
//        } else {
//            individualStrategy.send(payloads);
//        }

        //📌 Dispatcher does not know:
        //feature flags
        //thresholds
        //batch logic
        // ❌ “Why not if-else in dispatcher?”
        //👉 Because config + rollout logic belongs to decision layer, not execution.
        //📌 Individual vs Batch strategy determines how
        //📌 Dispatcher determines what to do on failure
        RetryArtifact retryArtifact =
                ctx.getArtifacts().get(RetryArtifact.class);

        SendStrategy<EmailPayload> strategy =
                resolver.resolve(payloads, ctx);

        try {
            strategy.send(payloads);
        } catch (Exception ex) {
            // batch-level failure
            payloads.forEach(p ->
                    retryArtifact.increment(p.getRecipient())
            );
            throw ex;
        }
    }
}
