package com.angle.trading.broker.angel;

import com.angle.trading.config.BrokerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Angel SmartAPI quote lookup — single-token LTP + previous close.
 *
 * Separate from AngelClient so stream-side code can depend on quote lookups
 * without pulling the full historical-candle client.
 *
 * Endpoint: /rest/secure/angelbroking/order/v1/getLtpData
 * Body: { exchange, tradingsymbol, symboltoken }
 * Response data: { exchange, tradingsymbol, symboltoken, open, high, low, close, ltp }
 *
 * The `close` field in the response is yesterday's close (what every broker
 * shows as the baseline for "today's change"). `ltp` is the current price.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AngelLtpClient {

    private static final String LTP_PATH = "/rest/secure/angelbroking/order/v1/getLtpData";

    private final RestClient restClient;
    private final BrokerProperties brokerProperties;
    private final AngelAuthService authService;

    /** Returns yesterday's close for the token, or empty on any failure. */
    public Optional<BigDecimal> fetchPreviousClose(String exchange, String tradingSymbol, String symbolToken) {
        LtpResponse resp = call(exchange, tradingSymbol, symbolToken);
        if (resp != null && !resp.status && isInvalidToken(resp)) {
            log.info("Angel LTP invalid-token for {} — refreshing JWT and retrying once", symbolToken);
            authService.invalidate();
            resp = call(exchange, tradingSymbol, symbolToken);
        }
        if (resp == null || !resp.status || resp.data == null) {
            String msg = resp == null ? "null response" : (resp.message + " [" + resp.errorcode + "]");
            log.warn("Angel LTP fetch failed for {}:{}: {}", exchange, symbolToken, msg);
            return Optional.empty();
        }
        Object close = resp.data.get("close");
        if (close == null) return Optional.empty();
        try {
            return Optional.of(new BigDecimal(close.toString()));
        } catch (NumberFormatException e) {
            log.warn("Angel LTP close not parseable for {}: {}", symbolToken, close);
            return Optional.empty();
        }
    }

    private LtpResponse call(String exchange, String tradingSymbol, String symbolToken) {
        BrokerProperties.Angel cfg = brokerProperties.getAngel();
        String jwt = authService.getJwtToken();
        Map<String, Object> body = Map.of(
                "exchange", exchange,
                "tradingsymbol", tradingSymbol == null ? "" : tradingSymbol,
                "symboltoken", symbolToken
        );
        try {
            return restClient.post()
                    .uri(cfg.getBaseUrl() + LTP_PATH)
                    .headers(h -> AngelHeaders.apply(h, cfg.getApiKey(), jwt))
                    .body(body)
                    .retrieve()
                    .body(LtpResponse.class);
        } catch (Exception e) {
            log.warn("Angel LTP request threw for token {}: {}", symbolToken, e.getMessage());
            return null;
        }
    }

    private static boolean isInvalidToken(LtpResponse r) {
        return "AB1010".equalsIgnoreCase(r.errorcode)
                || (r.message != null && r.message.toLowerCase().contains("invalid token"));
    }

    /** Minimal response shape — we only need `data.close`. */
    public static class LtpResponse {
        public boolean status;
        public String message;
        public String errorcode;
        public Map<String, Object> data;
    }
}
