package com.angle.trading.markets;

import com.angle.trading.config.GlobalMarketsProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the list of global instruments and their latest quotes.
 *
 * Fetches on a schedule (default 15 min) to stay under Alpha Vantage's
 * 500 calls/day free-tier limit. Between fetches, the page and API return
 * the cached snapshot.
 *
 * Design choice: instrument list is HARDCODED here for now. Later could move
 * to a config-driven or DB-driven list. 8 instruments cover the market-moving
 * globals that matter for Nifty.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalMarketService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final GlobalMarketsProperties props;
    private final AlphaVantageClient client;

    /** Cached quotes by symbol. Updated by the scheduler. */
    private final Map<String, MarketQuote> quotes = new ConcurrentHashMap<>();

    /** The 8 instruments we track. Static list; edit here to add more. */
    private static final List<Definition> UNIVERSE = List.of(
            new Definition("COMMODITY",  "BNO",     "Brent Oil (BNO ETF)",  "🛢️", "USD"),
            new Definition("COMMODITY",  "GLD",     "Gold (SPDR)",           "🥇", "USD"),
            new Definition("FX",         "USDINR",  "USD / INR",             "💵", "INR"),
            new Definition("INDEX",      "DIA",     "Dow Jones (DIA ETF)",   "🇺🇸", "USD"),
            new Definition("INDEX",      "QQQ",     "Nasdaq (QQQ ETF)",      "🇺🇸", "USD"),
            new Definition("INDEX",      "EWJ",     "Nikkei (EWJ ETF)",      "🇯🇵", "USD"),
            new Definition("INDEX",      "EWH",     "Hang Seng (EWH ETF)",   "🇭🇰", "USD"),
            new Definition("VOLATILITY", "VXX",     "US VIX (VXX ETF)",      "😨", "USD")
    );

    @PostConstruct
    void init() {
        log.info("GlobalMarketService initialised — enabled={} refresh={}min symbols={}",
                props.isEnabled(), props.getRefreshMinutes(), UNIVERSE.size());
    }

    /** Snapshot of all quotes for the dashboard. Order matches UNIVERSE. */
    public List<MarketQuote> allQuotes() {
        List<MarketQuote> out = new ArrayList<>();
        for (Definition d : UNIVERSE) {
            MarketQuote q = quotes.get(d.symbol);
            out.add(q != null ? q : MarketQuote.unavailable(d.category, d.symbol, d.label, d.icon, d.currency));
        }
        return out;
    }

    /** Manual trigger — useful for testing without waiting for the scheduler. */
    public int refreshAll() {
        if (!props.isEnabled()) return 0;
        if (props.isFetchOnlyDuringMarketHours() && !isMarketHours()) {
            log.debug("Skipping refresh — outside market hours");
            return 0;
        }
        int refreshed = 0;
        for (Definition d : UNIVERSE) {
            var raw = "FX".equals(d.category)
                    ? client.fxRate("USD", "INR")
                    : client.globalQuote(d.symbol);
            if (raw.isPresent()) {
                var r = raw.get();
                MarketQuote q = new MarketQuote(
                        d.category, d.symbol, d.label, d.icon, d.currency,
                        r.price(), r.change(), r.changePct(), Instant.now(),
                        interpret(d, r.changePct()));
                quotes.put(d.symbol, q);
                refreshed++;
            }
            // Alpha Vantage free = 5 calls/min — pause 13s between calls to stay under
            try { Thread.sleep(13_000); } catch (InterruptedException ignored) { }
        }
        log.info("GlobalMarkets: refreshed {}/{} quotes", refreshed, UNIVERSE.size());
        return refreshed;
    }

    /** Scheduled refresh — default every 15 min. */
    @Scheduled(fixedRateString = "#{@globalMarketsProperties.refreshMinutes * 60 * 1000}")
    public void scheduledRefresh() {
        if (!props.isEnabled()) return;
        refreshAll();
    }

    /** Rule-based interpretation shown on each card. */
    private String interpret(Definition d, double changePct) {
        return switch (d.category) {
            case "COMMODITY" -> d.symbol.equals("BNO") && changePct > 1.5
                    ? "⚠️ oil spike — bearish for Nifty"
                    : d.symbol.equals("GLD") && changePct > 1.0
                    ? "safe-haven bid"
                    : "";
            case "FX" -> changePct > 0.3 ? "⚠️ weak rupee" : changePct < -0.3 ? "strong rupee" : "";
            case "INDEX" -> changePct > 1.0
                    ? "↑ positive open likely"
                    : changePct < -1.0
                    ? "↓ negative open likely"
                    : "";
            case "VOLATILITY" -> changePct > 5.0
                    ? "⚠️ fear rising — reduce risk"
                    : "";
            default -> "";
        };
    }

    private boolean isMarketHours() {
        LocalTime now = LocalTime.now(IST);
        return !now.isBefore(LocalTime.of(9, 0)) && !now.isAfter(LocalTime.of(16, 0));
    }

    /** Overall directional summary based on global data. */
    public String impactAnalysis() {
        var dow = quotes.get("DIA");
        var oil = quotes.get("BNO");
        var vix = quotes.get("VXX");
        StringBuilder sb = new StringBuilder();
        if (dow != null && dow.changePct() > 0.3)        sb.append("US closed higher (+").append(fmt(dow.changePct())).append("%). ");
        else if (dow != null && dow.changePct() < -0.3)  sb.append("US closed lower (").append(fmt(dow.changePct())).append("%). ");
        if (oil != null && Math.abs(oil.changePct()) > 1.0)
            sb.append("Oil ").append(oil.changePct() > 0 ? "up" : "down").append(" ")
              .append(fmt(oil.changePct())).append("%. ");
        if (vix != null && vix.changePct() > 3.0)  sb.append("Fear index rising. ");
        if (sb.isEmpty()) sb.append("Global markets are calm overnight.");
        return sb.toString();
    }

    private static String fmt(double v) {
        return String.format("%.1f", v);
    }

    /** UNIVERSE entry — a single instrument to track. */
    private record Definition(String category, String symbol, String label, String icon, String currency) {}

    /** Access for controllers. */
    public List<Definition> universe() { return Arrays.asList(UNIVERSE.toArray(new Definition[0])); }
}
