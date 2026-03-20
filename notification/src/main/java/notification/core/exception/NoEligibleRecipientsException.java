package notification.core.exception;

//Fail Fast
public class NoEligibleRecipientsException extends RuntimeException {
    public NoEligibleRecipientsException(String message) {
        super(message);
    }
}
