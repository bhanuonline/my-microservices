package com.angle.trading.broker.angel;

import org.springframework.http.HttpHeaders;

/**
 * Angel SmartAPI requires a fixed set of headers on every call. Centralising
 * them here keeps AngelAuthService and AngelClient short and consistent.
 *
 * Public/local IP and MAC are cosmetic for our use case — Angel just wants
 * them present. Real values are only relevant for compliance/audit logging.
 */
final class AngelHeaders {

    private AngelHeaders() {}

    static void apply(HttpHeaders h, String apiKey, String jwtToken) {
        h.set("X-UserType", "USER");
        h.set("X-SourceID", "WEB");
        h.set("X-ClientLocalIP", "127.0.0.1");
        h.set("X-ClientPublicIP", "127.0.0.1");
        h.set("X-MACAddress", "00:00:00:00:00:00");
        h.set("X-PrivateKey", apiKey);
        h.set("Accept", "application/json");
        h.set("Content-Type", "application/json");
        if (jwtToken != null && !jwtToken.isBlank()) {
            h.setBearerAuth(jwtToken);
        }
    }
}
