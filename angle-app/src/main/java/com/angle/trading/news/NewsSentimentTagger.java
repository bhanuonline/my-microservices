package com.angle.trading.news;

import com.angle.trading.ai.AiProperties;
import com.angle.trading.ai.AnthropicClient;
import com.angle.trading.config.NewsFeedProperties;
import com.angle.trading.persistence.NewsHeadlineEntity;
import com.angle.trading.persistence.NewsHeadlineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ships un-tagged headlines to Claude in a single batch call and applies
 * the returned sentiment / sector to each row.
 *
 * Prompt format (strict, pipe-delimited to avoid JSON parsing errors):
 *
 *   Line N: BULLISH | BANKING | Rate cut boosts credit demand
 *
 * where N is 1-indexed matching the input headline order.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsSentimentTagger {

    private static final Pattern LINE_PATTERN = Pattern.compile(
            "^\\s*(\\d+)[.:\\)\\s]+([A-Z]+)\\s*\\|\\s*([A-Z_]+)\\s*\\|\\s*(.+?)\\s*$",
            Pattern.MULTILINE);

    private final NewsHeadlineRepository repo;
    private final AnthropicClient anthropic;
    private final AiProperties aiProps;
    private final NewsFeedProperties props;

    /**
     * Grab up to {@code aiTaggingBatchSize} untagged headlines, call Claude
     * once, and apply results.
     */
    @Transactional
    public int tagBatch() {
        if (!props.isAiTaggingEnabled()) return 0;
        if (aiProps == null || aiProps.getAnthropic() == null
                || aiProps.getAnthropic().getApiKey() == null
                || aiProps.getAnthropic().getApiKey().isBlank()) {
            log.debug("News tagger: skipping — no Claude API key");
            return 0;
        }

        List<NewsHeadlineEntity> batch = repo.findUntagged(PageRequest.of(0, props.getAiTaggingBatchSize()));
        if (batch.isEmpty()) return 0;

        String userPrompt = buildPrompt(batch);
        String raw;
        try {
            raw = anthropic.complete(systemPrompt(), userPrompt);
        } catch (Exception e) {
            log.warn("News tagger: Claude call failed — {}", e.getMessage());
            return 0;
        }

        List<Result> parsed = parseResponse(raw);
        int applied = 0;
        for (Result r : parsed) {
            if (r.index < 1 || r.index > batch.size()) continue;
            NewsHeadlineEntity h = batch.get(r.index - 1);
            h.setSentiment(r.sentiment);
            h.setSector(r.sector);
            h.setRationale(r.rationale);
            h.setAiTagged(true);
            applied++;
        }
        // Mark any un-answered rows as tagged anyway (so we don't retry forever).
        for (NewsHeadlineEntity h : batch) {
            if (!h.isAiTagged()) {
                h.setAiTagged(true);
                h.setSentiment("NEUTRAL");
                h.setSector("GENERAL");
                h.setRationale("(not tagged by AI)");
            }
        }
        repo.saveAll(batch);
        log.info("News tagger: applied {} of {} headlines", applied, batch.size());
        return applied;
    }

    // ---------- prompt ----------

    private String systemPrompt() {
        return """
                You are a financial-markets analyst. You will be given a numbered list of
                Indian news headlines. For EACH headline output ONE line in this EXACT format:

                    <n>. <SENTIMENT> | <SECTOR> | <one-sentence rationale>

                Where:
                  n         = the number of the headline (matching input)
                  SENTIMENT = BULLISH  (positive for Indian equities)
                              BEARISH  (negative for Indian equities)
                              NEUTRAL  (unclear or company-specific with no market impact)
                  SECTOR    = one of: BANKING, IT, OIL_GAS, AUTO, PHARMA, METALS,
                              FMCG, REALTY, TELECOM, POWER, INFRA, FINANCE, MEDIA,
                              INDEX, GLOBAL, GENERAL
                              (Use INDEX for Nifty/Sensex-level news, GLOBAL for
                              Fed/US/crude/geopolitics, GENERAL for anything else.)
                  rationale = 1 sentence, no pipes, no markdown

                Rules:
                  • Output exactly ONE line per input headline
                  • Number the lines to match the input
                  • No preamble, no summary, no code fences
                """;
    }

    private String buildPrompt(List<NewsHeadlineEntity> headlines) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tag these ").append(headlines.size()).append(" Indian financial headlines:\n\n");
        for (int i = 0; i < headlines.size(); i++) {
            sb.append(i + 1).append(". ").append(headlines.get(i).getTitle()).append("\n");
        }
        return sb.toString();
    }

    // ---------- parse ----------

    private List<Result> parseResponse(String raw) {
        List<Result> out = new ArrayList<>();
        Matcher m = LINE_PATTERN.matcher(raw);
        while (m.find()) {
            try {
                int idx        = Integer.parseInt(m.group(1));
                String sent    = m.group(2).toUpperCase();
                String sector  = m.group(3).toUpperCase();
                String note    = m.group(4).trim();
                if (!sent.matches("BULLISH|BEARISH|NEUTRAL")) sent = "NEUTRAL";
                out.add(new Result(idx, sent, sector, note));
            } catch (NumberFormatException ignored) { /* skip bad row */ }
        }
        return out;
    }

    private record Result(int index, String sentiment, String sector, String rationale) {}
}
