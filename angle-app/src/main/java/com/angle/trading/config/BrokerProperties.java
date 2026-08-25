package com.angle.trading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds all broker.* keys from application.properties into typed Java objects.
 * Injected wherever broker credentials are needed.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "broker")
public class BrokerProperties {

    private Angel angel = new Angel();
    private Upstox upstox = new Upstox();
    private Kite kite = new Kite();

    @Data
    public static class Angel {
        private boolean enabled;
        private String baseUrl;
        private String apiKey;
        private String clientCode;
        private String password;
        private String totpSecret;
    }

    @Data
    public static class Upstox {
        private boolean enabled;
        private String baseUrl;
        private String apiKey;
        private String apiSecret;
        private String redirectUri;
    }

    @Data
    public static class Kite {
        private boolean enabled;
        private String baseUrl;
        private String apiKey;
        private String apiSecret;
    }
}
