package notification.consumer.bindingchannel;

import org.springframework.messaging.SubscribableChannel;

/**
 * Legacy binding interface from the Cloud Stream 2.x/3.x era (@EnableBinding + @Input).
 * Not used anymore — the functional model (Consumer<T> beans) replaced this pattern.
 * Kept as historical reference; safe to delete.
 */
public interface UserRegistrationInput {
    SubscribableChannel userRegistrationInput();
}
