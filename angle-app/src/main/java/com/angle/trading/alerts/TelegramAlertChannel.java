package com.angle.trading.alerts;

import com.angle.trading.config.AlertsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Sends alerts to a Telegram chat via the Bot API.
 *
 * Setup (one-time):
 *   1. Open Telegram, message @BotFather, /newbot, save the token
 *   2. Send any message to your new bot
 *   3. Open https://api.telegram.org/bot<TOKEN>/getUpdates in a browser
 *   4. Find chat.id in the response — that's your chat id
 *   5. Set env vars TELEGRAM_BOT_TOKEN + TELEGRAM_CHAT_ID
 *
 * Uses a plain HTTP POST to sendMessage with JSON body. Plain text only
 * (no Markdown parse mode) — avoids escaping headaches with emojis and
 * special characters.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramAlertChannel implements AlertChannel {

    private static final String API_BASE = "https://api.telegram.org";

    private final RestClient restClient;
    private final AlertsProperties props;

    @Override
    public String name() {
        return "telegram";
    }

    @Override
    public boolean enabled() {
        AlertsProperties.Telegram cfg = props.getTelegram();
        return cfg.isEnabled()
                && cfg.getBotToken() != null && !cfg.getBotToken().isBlank()
                && cfg.getChatId()   != null && !cfg.getChatId().isBlank();
    }

    @Override
    public void send(AlertLevel level, String text) {
        AlertsProperties.Telegram cfg = props.getTelegram();
        try {
            restClient.post()
                    .uri(API_BASE + "/bot" + cfg.getBotToken() + "/sendMessage")
                    .body(Map.of(
                            "chat_id",              cfg.getChatId(),
                            "text",                 text,
                            "disable_web_page_preview", true
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Telegram send failed: {}", e.getMessage());
        }
    }
}
