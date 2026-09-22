package com.angle.trading.alerts;

import com.angle.trading.config.AlertsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Sends alerts to WhatsApp via CallMeBot (unofficial free gateway).
 *
 * Endpoint:
 *   GET https://api.callmebot.com/whatsapp.php?phone=<PHONE>&text=<URL-ENCODED>&apikey=<KEY>
 *
 * Notes:
 *   - CallMeBot is unofficial and free — perfect for personal use, don't
 *     use for production business alerts.
 *   - Rate limit: ~10 messages/minute per phone number (undocumented).
 *   - Text must be URL-encoded; emojis + newlines survive fine.
 *   - Phone must include country code (e.g. 919876543210 for India).
 *
 * See {@link AlertsProperties.WhatsApp} for the one-time setup steps.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppAlertChannel implements AlertChannel {

    private static final String API_BASE = "https://api.callmebot.com/whatsapp.php";

    private final RestClient restClient;
    private final AlertsProperties props;

    @Override
    public String name() {
        return "whatsapp";
    }

    @Override
    public boolean enabled() {
        AlertsProperties.WhatsApp cfg = props.getWhatsapp();
        return cfg.isEnabled()
                && cfg.getPhone()  != null && !cfg.getPhone().isBlank()
                && cfg.getApiKey() != null && !cfg.getApiKey().isBlank();
    }

    @Override
    public void send(AlertLevel level, String text) {
        AlertsProperties.WhatsApp cfg = props.getWhatsapp();
        try {
            String url = API_BASE
                    + "?phone="  + URLEncoder.encode(cfg.getPhone(),  StandardCharsets.UTF_8)
                    + "&text="   + URLEncoder.encode(text,            StandardCharsets.UTF_8)
                    + "&apikey=" + URLEncoder.encode(cfg.getApiKey(), StandardCharsets.UTF_8);
            restClient.get()
                    .uri(url)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("WhatsApp send failed: {}", e.getMessage());
        }
    }
}
