package com.angle.trading.alerts;

/**
 * One destination for alerts (Telegram, email, Slack, etc.).
 *
 * Implementations register as Spring components; {@link AlertService}
 * auto-picks up every AlertChannel bean and routes alerts to all
 * currently-enabled ones.
 *
 * {@link #enabled()} is checked per-call so config changes take effect
 * without restart.
 */
public interface AlertChannel {

    /** Short name for logs (e.g. "telegram"). */
    String name();

    /** True if this channel is configured and turned on. */
    boolean enabled();

    /**
     * Deliver a message. Must NOT throw — implementations should log any
     * failure and return normally, since the caller runs on the alert
     * executor and shouldn't propagate exceptions.
     */
    void send(AlertLevel level, String text);
}
