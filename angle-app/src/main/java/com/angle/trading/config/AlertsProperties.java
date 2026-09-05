package com.angle.trading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Alerts configuration.
 *
 * Master switch: {@code alerts.enabled=false} kills all alerts everywhere.
 * Per-event toggles let you silence noisy events (e.g. no ping for HOLDs)
 * while keeping critical ones on (errors, trade opens/closes).
 *
 * Channel-specific settings live under {@code alerts.<channel>.*}.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "alerts")
public class AlertsProperties {

    private boolean enabled = false;

    private boolean onTradeOpen  = true;
    private boolean onTradeClose = true;
    private boolean onSessionEnd = true;
    private boolean onErrors     = true;

    private Telegram telegram = new Telegram();
    private WhatsApp whatsapp = new WhatsApp();

    @Data
    public static class Telegram {
        private boolean enabled = false;
        private String  botToken;
        private String  chatId;
    }

    /**
     * WhatsApp via CallMeBot (unofficial free gateway).
     *
     * One-time setup:
     *   1. Save +34 644 51 95 23 to your contacts as "CallMeBot"
     *   2. Send "I allow callmebot to send me messages" from your WhatsApp
     *   3. Wait ~2 minutes for the API key reply
     *   4. Set env vars WHATSAPP_PHONE (with country code, e.g. 919876543210)
     *      and WHATSAPP_API_KEY (7-digit key)
     */
    @Data
    public static class WhatsApp {
        private boolean enabled = false;
        private String  phone;    // e.g. 919876543210 (no leading +)
        private String  apiKey;   // 7-digit key from CallMeBot
    }
}
