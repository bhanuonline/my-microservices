package com.angle.trading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Trading / risk-management configuration.
 *
 * All numbers live here — no hardcoded lot sizes or risk %.
 * Change your risk profile without touching Java code.
 *
 * Example (application.properties):
 *   trading.capital=500000
 *   trading.risk-percent=1.0
 *   trading.lot-sizes.NIFTY=75
 *   trading.lot-sizes.BANKNIFTY=30
 */
@Data
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "trading")
public class TradingProperties {

    /** Trading capital in INR (used for position sizing). */
    private long capital = 500_000L;

    /** % of capital risked per trade (typical: 0.5 – 2.0). */
    private double riskPercent = 1.0;

    /**
     * Lot sizes per instrument name (as it appears in bias.instruments[i].symbol).
     * Case-insensitive lookup. "default" applies when no specific entry matches.
     */
    private Map<String, Integer> lotSizes = defaultLotSizes();

    /** Options-related config. */
    private Options options = new Options();

    private static Map<String, Integer> defaultLotSizes() {
        Map<String, Integer> m = new HashMap<>();
        m.put("nifty50", 75);
        m.put("banknifty", 30);
        m.put("finnifty", 40);
        m.put("niftynext50", 25);
        m.put("crudeoil", 100);
        m.put("gold", 100);
        m.put("silver", 5);
        m.put("default", 1);   // per-share for equities
        return m;
    }

    /**
     * Look up lot size by instrument name.
     * Normalizes the lookup key (lowercase + strip spaces) so config keys don't need to match
     * display names exactly — "Nifty 50" and "nifty50" resolve to the same entry.
     */
    public int lotSizeFor(String symbol) {
        if (symbol == null) return lotSizes.getOrDefault("default", 1);
        String key = symbol.toLowerCase().replace(" ", "");
        Integer size = lotSizes.get(key);
        return size != null ? size : lotSizes.getOrDefault("default", 1);
    }

    /**
     * Options config — drives the "Suggested Option" card.
     *
     * Strike selection uses 3 factors: confidence + VIX regime + days-to-expiry.
     *
     *   strikeInterval             — round spot to nearest N for ATM (Nifty=50 or 100).
     *   preferredDaysToExpiry      — pick the expiry closest to this many days out.
     *                                Higher = safer (less theta), lower = more delta bang.
     *                                60 = "roughly 2 months out" (safer for learning).
     *   nearExpiryDays             — if the nearest listed expiry is ≤ this, force ITM (theta risk).
     *   nearExpiryItmOffset        — how far ITM when near expiry (negative points).
     *
     *   VIX + confidence → OTM offset (points from ATM). Higher offset = further OTM.
     *   ELEVATED VIX → premiums inflated → prefer further OTM.
     *   CALM VIX     → premiums cheap → prefer ATM.
     */
    @Data
    public static class Options {
        private int strikeInterval = 100;
        private int preferredDaysToExpiry = 60;
        private int nearExpiryDays        = 1;
        private int nearExpiryItmOffset   = -100;

        // CALM VIX (< 13)
        private int calmStrongOffset   = 0;
        private int calmModerateOffset = 0;
        private int calmWeakOffset     = 50;

        // NORMAL VIX (13-18)
        private int normalStrongOffset   = 0;
        private int normalModerateOffset = 50;
        private int normalWeakOffset     = 100;

        // ELEVATED VIX (> 18)
        private int elevatedStrongOffset   = 0;
        private int elevatedModerateOffset = 100;
        private int elevatedWeakOffset     = 200;
    }
}
