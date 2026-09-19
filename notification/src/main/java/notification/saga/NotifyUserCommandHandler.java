package notification.saga;

import com.example.common.saga.NotifyUserCommand;
import com.example.common.saga.NotifyUserReply;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
@Slf4j
public class NotifyUserCommandHandler {

    private static final String REPLY_BINDING = "notifyUserReply-out-0";

    @Bean
    public Consumer<NotifyUserCommand> notifyUserCommand(StreamBridge streamBridge) {
        return cmd -> {
            log.info("Sending notification sagaId={} orderId={} msg={}",
                    cmd.sagaId(), cmd.orderId(), cmd.message());

            // Demo rule: fail if message contains "fail-me" — lets you trigger
            // the compensation branch on demand from the caller side.
            boolean success = cmd.message() == null || !cmd.message().contains("fail-me");

            NotifyUserReply reply = success
                    ? new NotifyUserReply(cmd.sagaId(), cmd.orderId(), true, null)
                    : new NotifyUserReply(cmd.sagaId(), cmd.orderId(), false, "smtp_unreachable");

            streamBridge.send(REPLY_BINDING, reply);
        };
    }
}
