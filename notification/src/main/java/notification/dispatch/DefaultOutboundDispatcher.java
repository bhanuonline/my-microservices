package notification.dispatch;

import lombok.extern.slf4j.Slf4j;
import notification.core.NotificationContext;
import org.springframework.stereotype.Component;

/**
 * Minimal working dispatcher. Logs the dispatch and returns.
 *
 * TODO: real implementation should route by NotificationType (EMAIL, SMS, PUSH)
 * to a concrete gateway. For now, satisfies the @Component contract so
 * EmailChannelHandler can autowire it and the app starts.
 */
@Slf4j
@Component
public class DefaultOutboundDispatcher implements OutboundDispatcher {

    @Override
    public void dispatch(NotificationContext ctx) {
        log.info("[dispatch stub] type={} recipient={} message={}",
                ctx.getType(), ctx.getRecipient(), ctx.getMessage());
        // real logic later — for now just log
    }
}
