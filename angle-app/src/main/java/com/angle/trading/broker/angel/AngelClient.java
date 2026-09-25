package com.angle.trading.broker.angel;

import com.angle.trading.broker.BrokerClient;
import com.angle.trading.broker.angel.dto.AngelCandleResponse;
import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.broker.model.Quote;
import com.angle.trading.config.BrokerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Angel One SmartAPI implementation of BrokerClient.
 *
 * Bean is created only when broker.angel.enabled=true so disabling Angel
 * doesn't force us to configure credentials.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AngelClient implements BrokerClient {

    private static final String CANDLE_PATH = "/rest/secure/angelbroking/historical/v1/getCandleData";
    private static final DateTimeFormatter ANGEL_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final RestClient restClient;
    private final BrokerProperties brokerProperties;
    private final AngelAuthService authService;

    @Override
    public String name() {
        return "ANGEL";
    }

    @Override
    public List<Candle> getCandles(Exchange exchange, String symbolToken,
                                   Interval interval, LocalDate from, LocalDate to) {
        BrokerProperties.Angel cfg = brokerProperties.getAngel();

        Map<String, Object> body = Map.of(
                "exchange", exchange.name(),                  // NSE / NFO / BSE / BFO / MCX / CDS
                "symboltoken", symbolToken,
                "interval", toAngelInterval(interval),
                "fromdate", from.atTime(9, 15).format(ANGEL_DATE_FMT),
                "todate", to.atTime(15, 30).format(ANGEL_DATE_FMT)
        );

        log.debug("Fetching Angel candles: {}", body);

        // Try once; if Angel says "Invalid Token" (AB1010) → drop JWT, retry ONCE.
        // The retry will hit the refresh-token flow (no TOTP burn).
        AngelCandleResponse response = callCandles(cfg, body);
        if (response != null && !response.status() && isInvalidTokenError(response)) {
            log.info("Angel returned invalid-token for {} — refreshing and retrying once", symbolToken);
            authService.invalidate();
            response = callCandles(cfg, body);
        }

        if (response == null || !response.status()) {
            String msg = response == null
                    ? "null response"
                    : (response.message() + " [errorcode=" + response.errorcode() + "]");
            throw new IllegalStateException("Angel candle fetch failed: " + msg);
        }

        return response.candles().stream().map(AngelClient::toCandle).toList();
    }

    /** Make ONE candle POST with a fresh JWT. Returns null on transport failure. */
    private AngelCandleResponse callCandles(BrokerProperties.Angel cfg, Map<String, Object> body) {
        String jwt = authService.getJwtToken();
        try {
            return restClient.post()
                    .uri(cfg.getBaseUrl() + CANDLE_PATH)
                    .headers(h -> AngelHeaders.apply(h, cfg.getApiKey(), jwt))
                    .body(body)
                    .retrieve()
                    .body(AngelCandleResponse.class);
        } catch (Exception e) {
            log.warn("Angel candle request threw for token {}: {}", body.get("symboltoken"), e.getMessage());
            return null;
        }
    }

    /** Angel signals a stale JWT via errorcode AB1010 or message containing "Invalid Token". */
    private static boolean isInvalidTokenError(AngelCandleResponse r) {
        if (r == null) return false;
        String code = r.errorcode();
        String msg  = r.message();
        return ("AB1010".equalsIgnoreCase(code))
                || (msg != null && msg.toLowerCase().contains("invalid token"));
    }

    @Override
    public Quote getQuote(Exchange exchange, String symbolToken) {
        // Angel exposes /rest/secure/angelbroking/order/v1/getLtpData for quotes.
        // Wire this up when needed.
        throw new UnsupportedOperationException("Angel getQuote not yet implemented");
    }

    private static Candle toCandle(List<Object> row) {
        // row: [timestamp, open, high, low, close, volume]
        return new Candle(
                OffsetDateTime.parse((String) row.get(0)).toInstant(),
                new BigDecimal(row.get(1).toString()),
                new BigDecimal(row.get(2).toString()),
                new BigDecimal(row.get(3).toString()),
                new BigDecimal(row.get(4).toString()),
                ((Number) row.get(5)).longValue()
        );
    }

    private static String toAngelInterval(Interval interval) {
        return switch (interval) {
            case ONE_MINUTE -> "ONE_MINUTE";
            case FIVE_MINUTE -> "FIVE_MINUTE";
            case FIFTEEN_MINUTE -> "FIFTEEN_MINUTE";
            case THIRTY_MINUTE -> "THIRTY_MINUTE";
            case ONE_HOUR -> "ONE_HOUR";
            case ONE_DAY -> "ONE_DAY";
        };
    }
}
