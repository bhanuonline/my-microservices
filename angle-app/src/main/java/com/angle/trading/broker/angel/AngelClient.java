package com.angle.trading.broker.angel;

import com.angle.trading.broker.BrokerClient;
import com.angle.trading.broker.angel.dto.AngelCandleResponse;
import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.broker.model.Quote;
import com.angle.trading.config.BrokerProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
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

    // Angel historical API: 3 requests/second. We use 2 to stay comfortably below.
    private final Bucket candleRateLimiter = Bucket.builder()
            .addLimit(Bandwidth.classic(2, Refill.greedy(2, Duration.ofSeconds(1))))
            .build();

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
        // Use 23:59 as the end time so the `to` date's candles are always included.
        //   • NSE/BSE equity: last bar is at 15:15 so 23:59 has no effect
        //   • MCX commodity:  session runs till ~23:30 so 23:59 captures evening candles
        //   • ONE_DAY candles: Angel timestamps the daily bar at 15:30 IST and treats
        //     todate as exclusive — using 23:59 ensures the `to` date's daily bar is returned
        return getCandlesInRange(exchange, symbolToken, interval,
                from.atTime(9, 15), to.atTime(23, 59));
    }

    /**
     * Variant that lets callers supply exact fromtime/totime — needed when the
     * default NSE 9:15–15:30 window doesn't match the instrument's exchange
     * (MCX opens 9:00, overnight sessions, etc.).
     *
     * Used by {@link com.angle.trading.orb.OrbService} for ORB backfill where
     * the opening-range window must start at the real session open.
     */
    public List<Candle> getCandlesInRange(Exchange exchange, String symbolToken,
                                          Interval interval,
                                          java.time.LocalDateTime fromDateTime,
                                          java.time.LocalDateTime toDateTime) {
        BrokerProperties.Angel cfg = brokerProperties.getAngel();

        Map<String, Object> body = Map.of(
                "exchange", exchange.name(),                  // NSE / NFO / BSE / BFO / MCX / CDS
                "symboltoken", symbolToken,
                "interval", toAngelInterval(interval),
                "fromdate", fromDateTime.format(ANGEL_DATE_FMT),
                "todate",   toDateTime.format(ANGEL_DATE_FMT)
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
        // Throttle to Angel's rate limit before sending.
        try {
            candleRateLimiter.asBlocking().consume(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        String jwt = authService.getJwtToken();
        try {
            return restClient.post()
                    .uri(cfg.getBaseUrl() + CANDLE_PATH)
                    .headers(h -> AngelHeaders.apply(h, cfg.getApiKey(), jwt))
                    .body(body)
                    .retrieve()
                    .body(AngelCandleResponse.class);
        } catch (RestClientResponseException e) {
            // Angel returns 403 plain-text for rate-limit; log the body so it's diagnosable.
            log.warn("Angel candle HTTP {} for token {}: {}",
                    e.getStatusCode().value(), body.get("symboltoken"), e.getResponseBodyAsString().trim());
            return null;
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
            case ONE_MINUTE     -> "ONE_MINUTE";
            case FIVE_MINUTE    -> "FIVE_MINUTE";
            case FIFTEEN_MINUTE -> "FIFTEEN_MINUTE";
            case THIRTY_MINUTE  -> "THIRTY_MINUTE";
            case ONE_HOUR       -> "ONE_HOUR";
            case TWO_HOUR       -> "TWO_HOUR";
            case THREE_HOUR     -> "THREE_HOUR";
            case FOUR_HOUR      -> "FOUR_HOUR";
            case ONE_DAY        -> "ONE_DAY";
        };
    }
}
