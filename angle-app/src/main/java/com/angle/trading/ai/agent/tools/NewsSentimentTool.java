package com.angle.trading.ai.agent.tools;

import com.angle.trading.ai.agent.ResearchTool;
import com.angle.trading.persistence.NewsHeadlineEntity;
import com.angle.trading.persistence.NewsHeadlineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool: get_news_sentiment
 * Returns recent tagged news headlines from the DB.
 * Filtered by sector (optional) — helps Claude understand macro/sector mood.
 */
@Component
@RequiredArgsConstructor
public class NewsSentimentTool implements ResearchTool {

    private final NewsHeadlineRepository newsRepo;

    @Override public String name() { return "get_news_sentiment"; }

    @Override
    public String description() {
        return "Get recent Indian market news headlines with AI sentiment tags (POSITIVE/NEGATIVE/NEUTRAL). " +
               "Optionally filter by sector (e.g. BANKING, IT, PHARMA, AUTO). " +
               "Use this to understand market mood and sector-specific news.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("sector", Map.of("type", "string",
                "description", "Sector filter e.g. BANKING, IT, PHARMA, AUTO, FMCG. Leave empty for all sectors."));
        props.put("limit",  Map.of("type", "integer",
                "description", "Number of headlines to return (default 10, max 20)"));

        return Map.of(
                "type",       "object",
                "properties", props,
                "required",   new String[]{}
        );
    }

    @Override
    public String execute(Map<String, Object> args) {
        String sector = args.containsKey("sector") ? args.get("sector").toString().trim() : null;
        if (sector != null && sector.isBlank()) sector = null;

        int limit = args.containsKey("limit") ? Math.min(((Number) args.get("limit")).intValue(), 20) : 10;

        Instant cutoff = Instant.now().minus(3, ChronoUnit.DAYS);
        List<NewsHeadlineEntity> headlines = newsRepo.findFiltered(
                cutoff, null, null, sector, PageRequest.of(0, limit));

        if (headlines.isEmpty())
            return "No recent news found" + (sector != null ? " for sector: " + sector : "") + " in the last 3 days.";

        long positive = headlines.stream().filter(h -> "POSITIVE".equals(h.getSentiment())).count();
        long negative = headlines.stream().filter(h -> "NEGATIVE".equals(h.getSentiment())).count();
        long neutral  = headlines.stream().filter(h -> "NEUTRAL".equals(h.getSentiment())).count();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Recent news (%s): %d positive, %d negative, %d neutral\n\n",
                sector != null ? sector : "all sectors", positive, negative, neutral));

        for (NewsHeadlineEntity h : headlines) {
            sb.append(String.format("  [%s] %s\n", h.getSentiment() != null ? h.getSentiment() : "UNTAGGED", h.getTitle()));
        }
        return sb.toString().trim();
    }
}
