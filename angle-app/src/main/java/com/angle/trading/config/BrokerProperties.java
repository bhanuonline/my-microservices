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
        private Stream stream = new Stream();
    }

    /**
     * Angel SmartStream (WebSocket) config.
     *   enabled            — connect on boot?
     *   url                — wss endpoint. Angel default: wss://smartapisocket.angelone.in/smart-stream
     *   mode               — 1=LTP (recommended), 2=Quote, 3=SnapQuote
     *   reconnectSeconds   — backoff between reconnect attempts
     *   correlationId      — identifier Angel echoes back in messages
     */
    @Data
    public static class Stream {
        private boolean enabled = false;
        private String  url = "wss://smartapisocket.angelone.in/smart-stream";
        private int     mode = 1;
        private int     reconnectSeconds = 5;
        private String  correlationId = "angleapp";
        private Aggregate aggregate = new Aggregate();
        private LiveStream liveStream = new LiveStream();
    }

    /**
     * Candle aggregation from the tick stream (Phase 2).
     *   enabled       — master switch. When on, CandleAggregator builds bars and emits CandleClosedEvent
     *   intervals     — comma-separated list of intervals to aggregate in parallel (e.g. ONE_MINUTE,FIVE_MINUTE)
     *   sweepSeconds  — how often the emit-check runs. 1s is plenty; higher = more delay on close events
     */
    @Data
    public static class Aggregate {
        private boolean enabled = true;
        private String  intervals = "ONE_MINUTE,FIVE_MINUTE,FIFTEEN_MINUTE";
        private int     sweepSeconds = 1;
    }

    /**
     * Live SSE stream to browsers (Phase 3).
     *   enabled            — turn the /api/live/stream endpoint on
     *   throttleMs         — max 1 tick per token per this many ms (per client)
     *   heartbeatSeconds   — SSE keep-alive ping so proxies don't kill idle connections
     */
    @Data
    public static class LiveStream {
        private boolean enabled = true;
        private int     throttleMs = 500;
        private int     heartbeatSeconds = 30;
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
