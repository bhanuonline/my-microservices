package com.angle.trading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * News-headlines feed config (separate from the news-blackout calendar).
 *
 * Scrapes RSS feeds every N minutes, dedupes by URL, optionally tags each
 * headline with Claude for bullish/bearish sentiment + sector.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "news-feed")
public class NewsFeedProperties {

    /** Master switch. false = no fetches, no page. */
    private boolean enabled = true;

    /** Fetch cadence in minutes. */
    private int refreshMinutes = 15;

    /** How many days of headlines to retain in DB (older = auto-deleted). */
    private int retentionDays = 30;

    /**
     * RSS/Atom feed URLs to scrape.
     * Default set covers Indian markets + global business news.
     */
    private List<Source> sources = defaultSources();

    /** Whether to call Claude on each new headline to tag sentiment + sector. */
    private boolean aiTaggingEnabled = true;

    /** Batch size for the AI tagger — tags up to this many untagged headlines per run. */
    private int aiTaggingBatchSize = 10;

    /** Auto-refresh cadence on the /news page (seconds). */
    private int pageRefreshSeconds = 300;

    @Data
    public static class Source {
        private String name;    // display name shown on cards
        private String url;     // RSS/Atom feed URL
        private String category = "GENERAL";   // MARKETS / ECONOMY / COMPANY / GLOBAL
    }

    private static List<Source> defaultSources() {
        List<Source> list = new ArrayList<>();
        list.add(mk("MoneyControl Markets", "https://www.moneycontrol.com/rss/marketreports.xml",        "MARKETS"));
        list.add(mk("MoneyControl Business", "https://www.moneycontrol.com/rss/business.xml",            "GENERAL"));
        list.add(mk("Economic Times Markets", "https://cfo.economictimes.indiatimes.com/rss/topstories", "MARKETS"));
        list.add(mk("Business Standard Markets", "https://www.business-standard.com/rss/markets-106.rss","MARKETS"));
        list.add(mk("LiveMint Markets", "https://www.livemint.com/rss/markets",                          "MARKETS"));
        return list;
    }

    private static Source mk(String name, String url, String category) {
        Source s = new Source();
        s.setName(name);
        s.setUrl(url);
        s.setCategory(category);
        return s;
    }
}
