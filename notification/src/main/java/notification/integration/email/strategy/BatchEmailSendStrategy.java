package notification.integration.email.strategy;

import lombok.RequiredArgsConstructor;
import notification.dispatch.strategy.BatchSendStrategy;
import notification.dispatch.strategy.SendStrategy;
import notification.integration.email.EmailGateway;
import notification.integration.email.model.EmailPayload;
import org.springframework.stereotype.Component;

import java.util.List;

/**
📌 Strategy delegates
📌 Gateway executes

 4️⃣ Why NOT put sendBatch in Strategy?
 ❌ Strategy should not:
 Know SMTP
 Know HTTP clients
 Know vendor APIs
 ✔ Strategy only decides behavior
 */
@Component
@RequiredArgsConstructor
public class BatchEmailSendStrategy
        implements SendStrategy<EmailPayload>, BatchSendStrategy {

    private final EmailGateway emailGateway;

    @Override
    public void send(List<EmailPayload> payloads) {
        emailGateway.sendBatch(payloads);
    }
}