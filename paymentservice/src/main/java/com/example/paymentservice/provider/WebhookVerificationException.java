package com.example.paymentservice.provider;

/**
 * Thrown when an inbound webhook payload fails signature verification. The
 * controller returns 400 — never 200 — because 200 tells the provider "stop
 * retrying" and we don't want to acknowledge requests we couldn't verify.
 */
public class WebhookVerificationException extends RuntimeException {
    public WebhookVerificationException(String message) {
        super(message);
    }

    public WebhookVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
