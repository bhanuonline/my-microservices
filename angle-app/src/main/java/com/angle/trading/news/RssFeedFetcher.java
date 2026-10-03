package com.angle.trading.news;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tiny dependency-free RSS 2.0 parser.
 *
 * We only care about &lt;item&gt; blocks with &lt;title&gt;, &lt;link&gt;, and
 * &lt;pubDate&gt;. Regex-based; not a full XML parser. Perfectly adequate for
 * the well-formed RSS feeds from MoneyControl / ET / BS / Mint.
 *
 * If a feed changes format we simply get zero items back — safe degradation.
 */
@Slf4j
@Component
public class RssFeedFetcher {

    private static final Pattern ITEM_BLOCK  = Pattern.compile("<item.*?>(.*?)</item>",  Pattern.DOTALL);
    private static final Pattern TITLE_TAG   = Pattern.compile("<title>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</title>", Pattern.DOTALL);
    private static final Pattern LINK_TAG    = Pattern.compile("<link>(.*?)</link>",     Pattern.DOTALL);
    private static final Pattern PUBDATE_TAG = Pattern.compile("<pubDate>(.*?)</pubDate>", Pattern.DOTALL);

    private static final DateTimeFormatter RFC_822 = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z");

    public List<RssItem> fetch(String feedUrl) {
        try {
            String body = RestClient.builder().build()
                    .get()
                    .uri(feedUrl)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) return List.of();
            return parse(body);
        } catch (Exception e) {
            log.warn("RSS fetch failed for {}: {}", feedUrl, e.getMessage());
            return List.of();
        }
    }

    List<RssItem> parse(String xml) {
        List<RssItem> out = new ArrayList<>();
        Matcher itemMatcher = ITEM_BLOCK.matcher(xml);
        while (itemMatcher.find()) {
            String block = itemMatcher.group(1);
            String title = extract(block, TITLE_TAG);
            String link  = extract(block, LINK_TAG);
            String pub   = extract(block, PUBDATE_TAG);
            if (title == null || link == null || title.isBlank() || link.isBlank()) continue;
            out.add(new RssItem(
                    stripCdataAndHtml(title),
                    link.trim(),
                    parsePubDate(pub)
            ));
        }
        return out;
    }

    private static String extract(String block, Pattern p) {
        Matcher m = p.matcher(block);
        return m.find() ? m.group(1) : null;
    }

    private static String stripCdataAndHtml(String s) {
        return s.replaceAll("<!\\[CDATA\\[", "")
                .replaceAll("\\]\\]>", "")
                .replaceAll("<[^>]+>", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static Instant parsePubDate(String pub) {
        if (pub == null || pub.isBlank()) return null;
        try {
            return ZonedDateTime.parse(pub.trim(), RFC_822).toInstant();
        } catch (DateTimeParseException e) {
            // Some feeds use GMT or other zones. Best-effort: fallback to now.
            return null;
        }
    }

    public record RssItem(String title, String link, Instant publishedAt) {}
}
