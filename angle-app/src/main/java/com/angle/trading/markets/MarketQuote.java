package com.angle.trading.markets;

import java.time.Instant;

/**
 * One live quote for a global instrument.
 *
 *   category  = COMMODITY | FX | INDEX | VOLATILITY
 *   symbol    = Alpha Vantage ticker or display slug (BRENT, GC, USDINR, DJI, ...)
 *   label     = human-readable name shown on the dashboard
 *   price     = latest price
 *   change    = absolute change vs previous close
 *   changePct = % change vs previous close
 *   icon      = emoji or SVG hint for the card
 *   currency  = display currency (USD, INR, JPY, HKD)
 *   asOf      = when this quote was fetched
 */
public record MarketQuote(
        String category,
        String symbol,
        String label,
        String icon,
        String currency,
        double price,
        double change,
        double changePct,
        Instant asOf,
        String note                 // optional interpretation (e.g. "weak USD")
) {
    /** For the "no data" / error state — still renders in the card. */
    public static MarketQuote unavailable(String category, String symbol, String label, String icon, String currency) {
        return new MarketQuote(category, symbol, label, icon, currency,
                0, 0, 0, Instant.now(), "data unavailable");
    }
}
