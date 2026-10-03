package com.angle.trading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Config for the Global Markets dashboard.
 *
 * Fetches quotes for market-moving global instruments from Alpha Vantage:
 *   • Oil, Gold, USD/INR (commodities/FX — affect Indian markets directly)
 *   • Dow, Nasdaq, Nikkei, Hang Seng (indices — overnight sentiment)
 *   • VIX (US fear gauge)
 *
 * Alpha Vantage free tier limits: 5 calls/min, 500 calls/day.
 * We schedule fetches to stay well under both.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "global-markets")
public class GlobalMarketsProperties {

    /** Master switch. false = card + page hidden, no fetches. */
    private boolean enabled = true;

    /** Alpha Vantage API key. Get free at https://www.alphavantage.co/support/#api-key */
    private String apiKey = "demo";        // works with "demo" for AAPL only

    /** Alpha Vantage base URL. */
    private String baseUrl = "https://www.alphavantage.co";

    /** Refresh cadence (minutes). Keep >= 15 to stay under 500/day for 8 symbols. */
    private int refreshMinutes = 15;

    /** Only fetch during Indian market hours (9-4 IST). Saves half the daily quota. */
    private boolean fetchOnlyDuringMarketHours = true;

    /** Auto-refresh cadence on the /markets page (seconds). */
    private int pageRefreshSeconds = 60;
}
