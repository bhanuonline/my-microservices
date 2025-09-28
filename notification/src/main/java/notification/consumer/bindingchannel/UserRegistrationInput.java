package notification.consumer.bindingchannel;

import org.springframework.cloud.stream.annotation.Input;
import org.springframework.messaging.SubscribableChannel;

public interface UserRegistrationInput {
    //@Input("userRegistrationInput")
    SubscribableChannel userRegistrationInput();
}