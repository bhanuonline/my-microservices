package notification.core.recipient;

public interface Recipient {
    String id();   // for retry & tracking
    String language();

}
