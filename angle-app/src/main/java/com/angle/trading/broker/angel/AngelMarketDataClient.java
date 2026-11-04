package com.angle.trading.broker.angel;

import com.angle.trading.broker.model.Exchange;
import com.angle.trading.config.BrokerProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Angel SmartAPI Market Data client — fetches full quote including Open Interest.
 *
 * Endpoint: POST /rest/secure/angelbroking/market/v1/quote
 * Mode: FULL returns OI along with OHLC, circuit limits, buy/sell quantities.
 *
 * OI is only meaningful for F&O instruments (NFO futures/options, MCX commodities).
 * For equity (NSE/BSE) OI will be zero or absent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AngelMarketDataClient {

    private static final String MARKET_QUOTE_PATH = "/rest/secure/angelbroking/market/v1/quote";

    private final RestClient         restClient;
    private final BrokerProperties   brokerProperties;
    private final AngelAuthService   authService;

    /**
     * Fetches full market quote including Open Interest for a single token.
     *
     * @param exchange    the exchange (NSE, MCX, NFO etc.)
     * @param symbolToken Angel's symbol token
     * @return OI data, or empty if unavailable or instrument is not F&O
     */
    public Optional<OiData> fetchOi(Exchange exchange, String symbolToken) {
        BrokerProperties.Angel cfg = brokerProperties.getAngel();
        String jwt = authService.getJwtToken();

        Map<String, Object> body = Map.of(
                "mode", "FULL",
                "exchangeTokens", Map.of(exchange.name(), List.of(symbolToken))
        );

        try {
            MarketQuoteResponse resp = restClient.post()
                    .uri(cfg.getBaseUrl() + MARKET_QUOTE_PATH)
                    .headers(h -> AngelHeaders.apply(h, cfg.getApiKey(), jwt))
                    .body(body)
                    .retrieve()
                    .body(MarketQuoteResponse.class);

            if (resp == null) { log.warn("Market quote: null response for {}:{}", exchange, symbolToken); return Optional.empty(); }
            log.info("Market quote response: status={} message={}", resp.status, resp.message);
            if (!resp.status || resp.data == null || resp.data.fetched == null || resp.data.fetched.isEmpty()) {
                log.warn("Market quote: no data for {}:{} — unfetched={}", exchange, symbolToken,
                        resp.data != null ? resp.data.unfetched : "null");
                return Optional.empty();
            }

            QuoteEntry entry = resp.data.fetched.get(0);
            log.debug("Market quote OI for {}:{} → opnInterest={}", exchange, symbolToken, entry.opnInterest);

            if (entry.opnInterest == null || entry.opnInterest.compareTo(BigDecimal.ZERO) == 0) {
                log.debug("Market quote: OI is zero/null for {}:{}", exchange, symbolToken);
                return Optional.empty();
            }

            // Angel doesn't return OI change directly — compute from netChange context
            // netChange here is price change, not OI change. OI change needs prev session comparison.
            return Optional.of(new OiData(entry.opnInterest, null, entry.percentChange));

        } catch (Exception e) {
            log.warn("Market quote fetch failed for {}:{}: {}", exchange, symbolToken, e.getMessage());
            return Optional.empty();
        }
    }

    /** Parsed OI data from the market quote response. */
    public record OiData(
            BigDecimal openInterest,
            BigDecimal oiChange,
            BigDecimal oiChangePercent
    ) {}

    // ── Response DTOs ─────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MarketQuoteResponse {
        public boolean    status;
        public String     message;
        public String     errorcode;
        public DataBlock  data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataBlock {
        public List<QuoteEntry> fetched;
        public List<Object>     unfetched;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuoteEntry {
        public String     symbolToken;
        public String     tradingSymbol;
        public String     exchange;
        public BigDecimal ltp;
        public BigDecimal open;
        public BigDecimal high;
        public BigDecimal low;
        public BigDecimal close;
        public BigDecimal netChange;       // price net change
        public BigDecimal percentChange;   // price % change
        public BigDecimal avgPrice;
        public BigDecimal tradeVolume;
        public BigDecimal opnInterest;     // ← Angel's actual field name for OI
        public BigDecimal lowerCircuit;
        public BigDecimal upperCircuit;
    }
}
