package com.angle.trading.markets;

import com.angle.trading.config.GlobalMarketsProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * Thin wrapper over Alpha Vantage REST endpoints.
 *
 * We only use TWO endpoints:
 *   GLOBAL_QUOTE     → single equity/index price + change
 *   CURRENCY_EXCHANGE_RATE → FX pair
 *
 * All other data (commodities, indices, VIX) come from GLOBAL_QUOTE with
 * appropriate ETF/futures tickers as proxies:
 *   Brent oil      → "BNO" (Brent Oil ETF, near-proxy)  or "USO" (WTI)
 *   Gold           → "GLD" (SPDR Gold Trust ETF)
 *   Dow Jones      → "DIA" (SPDR Dow Jones ETF)
 *   Nasdaq         → "QQQ" (Invesco QQQ ETF)
 *   Nikkei 225     → "EWJ" (iShares Japan ETF, close proxy)
 *   Hang Seng      → "EWH" (iShares Hong Kong ETF)
 *   VIX            → "^VIX" (may need TIME_SERIES_DAILY)
 *
 * Alpha Vantage's free tier does NOT include real futures/commodities directly.
 * ETFs are the standard workaround.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlphaVantageClient {

    private final GlobalMarketsProperties props;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * Fetch a single equity/ETF quote. Returns Optional.empty() on any error
     * so callers can degrade gracefully (dashboard shows "unavailable" card).
     */
    public Optional<QuoteRaw> globalQuote(String symbol) {
        try {
            RestClient rc = RestClient.builder().baseUrl(props.getBaseUrl()).build();
            String body = rc.get()
                    .uri(uri -> uri.path("/query")
                            .queryParam("function", "GLOBAL_QUOTE")
                            .queryParam("symbol", symbol)
                            .queryParam("apikey", props.getApiKey())
                            .build())
                    .retrieve()
                    .body(String.class);

            JsonNode root = json.readTree(body);
            JsonNode q = root.path("Global Quote");
            if (q.isMissingNode() || q.isEmpty()) {
                // Alpha Vantage returns {"Note": "..."} on rate limit or {"Information": "..."} on quota exceeded
                if (root.has("Note") || root.has("Information")) {
                    log.warn("Alpha Vantage limit/quota: {}", root.toString());
                }
                return Optional.empty();
            }
            double price       = parseD(q.path("05. price").asText());
            double change      = parseD(q.path("09. change").asText());
            double changePctS  = parseD(q.path("10. change percent").asText().replace("%",""));
            return Optional.of(new QuoteRaw(symbol, price, change, changePctS));
        } catch (Exception e) {
            log.warn("Alpha Vantage fetch failed for {}: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    /** FX pair — Alpha Vantage returns only current bid, not change. */
    public Optional<QuoteRaw> fxRate(String from, String to) {
        try {
            RestClient rc = RestClient.builder().baseUrl(props.getBaseUrl()).build();
            String body = rc.get()
                    .uri(uri -> uri.path("/query")
                            .queryParam("function", "CURRENCY_EXCHANGE_RATE")
                            .queryParam("from_currency", from)
                            .queryParam("to_currency", to)
                            .queryParam("apikey", props.getApiKey())
                            .build())
                    .retrieve()
                    .body(String.class);
            JsonNode root = json.readTree(body);
            JsonNode q = root.path("Realtime Currency Exchange Rate");
            if (q.isMissingNode() || q.isEmpty()) return Optional.empty();
            double price = parseD(q.path("5. Exchange Rate").asText());
            return Optional.of(new QuoteRaw(from + to, price, 0, 0));  // change n/a for FX endpoint
        } catch (Exception e) {
            log.warn("Alpha Vantage FX fetch failed {}/{}: {}", from, to, e.getMessage());
            return Optional.empty();
        }
    }

    private static double parseD(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0.0; }
    }

    /** Raw response — service layer wraps into MarketQuote with label/icon/category. */
    public record QuoteRaw(String symbol, double price, double change, double changePct) {}
}
