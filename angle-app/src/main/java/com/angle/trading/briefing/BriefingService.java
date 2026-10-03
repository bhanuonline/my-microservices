package com.angle.trading.briefing;

import com.angle.trading.ai.AiProperties;
import com.angle.trading.ai.AnthropicClient;
import com.angle.trading.config.BriefingProperties;
import com.angle.trading.markets.GlobalMarketService;
import com.angle.trading.markets.MarketQuote;
import com.angle.trading.news.NewsHeadlineService;
import com.angle.trading.persistence.BriefingEntity;
import com.angle.trading.persistence.BriefingRepository;
import com.angle.trading.persistence.NewsHeadlineEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates the daily pre-market briefing.
 *
 * Flow:
 *   1. Snapshot global-markets quotes (Phase 1)
 *   2. Grab last 24h of AI-tagged news headlines (Phase 2)
 *   3. Build a compact prompt (~1500 tokens)
 *   4. Call Claude for a structured summary
 *   5. Parse response into BIAS + KEY_DRIVERS + SUMMARY
 *   6. Persist to DB (dedupe by forDate — only ONE briefing per day)
 *   7. Optionally email/telegram (currently log-only stubs)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BriefingService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final BriefingProperties  props;
    private final GlobalMarketService marketsService;
    private final NewsHeadlineService newsService;
    private final AnthropicClient     anthropic;
    private final AiProperties        aiProps;
    private final BriefingRepository  repo;
    private final ObjectMapper        json = new ObjectMapper();

    // ---------- scheduled trigger ----------

    @Scheduled(cron = "#{@briefingProperties.generateCron}", zone = "Asia/Kolkata")
    public void scheduledGenerate() {
        if (!props.isEnabled()) return;
        try { generate(LocalDate.now(IST)); }
        catch (Exception e) { log.error("Briefing scheduled generate failed", e); }
    }

    /** Manual trigger — used by admin endpoint. */
    @Transactional
    public BriefingEntity generate(LocalDate forDate) {
        if (aiProps == null || aiProps.getAnthropic() == null
                || aiProps.getAnthropic().getApiKey() == null
                || aiProps.getAnthropic().getApiKey().isBlank()) {
            throw new IllegalStateException("Cannot generate briefing — no Claude API key configured");
        }

        // 1. Snapshot markets
        List<MarketQuote> quotes = marketsService.allQuotes();

        // 2. Grab news
        List<NewsHeadlineEntity> headlines = newsService.findRecent(
                24, null, null, null, props.getMaxHeadlinesInPrompt());

        // 3. Build prompt
        String userPrompt = buildPrompt(quotes, headlines);

        // 4. Call Claude
        String raw = anthropic.complete(systemPrompt(), userPrompt);

        // 5. Parse
        Parsed parsed = parseResponse(raw);

        // 6. Persist (upsert by forDate)
        BriefingEntity b = repo.findByForDate(forDate).orElseGet(BriefingEntity::new);
        b.setForDate(forDate);
        b.setGeneratedAt(Instant.now());
        b.setBias(parsed.bias);
        b.setSummary(parsed.summary);
        b.setKeyDrivers(parsed.keyDrivers);
        b.setModel(aiProps.getAnthropic().getModel());
        try {
            b.setMarketsJson(json.writeValueAsString(quotes));
            b.setNewsJson(json.writeValueAsString(
                    headlines.stream().map(NewsHeadlineEntity::getTitle).toList()));
        } catch (Exception ignore) { /* audit trail, non-critical */ }

        b = repo.save(b);
        log.info("Briefing generated for {} — bias={}, {} headlines, model={}",
                forDate, parsed.bias, headlines.size(), b.getModel());

        // 7. Deliver (log-only for now)
        maybeSendEmail(b);
        maybeSendTelegram(b);
        return b;
    }

    // ---------- retention ----------

    @Scheduled(cron = "0 20 0 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void purgeOld() {
        if (!props.isEnabled()) return;
        Instant cutoff = Instant.now().minusSeconds(props.getRetentionDays() * 86400L);
        int deleted = repo.deleteOlderThan(cutoff);
        if (deleted > 0) log.info("Briefing: purged {} old rows", deleted);
    }

    // ---------- queries ----------

    public BriefingEntity latest() {
        return repo.findTop30ByOrderByForDateDesc().stream().findFirst().orElse(null);
    }

    public List<BriefingEntity> history() {
        return repo.findTop30ByOrderByForDateDesc();
    }

    // ---------- prompt ----------

    private String systemPrompt() {
        return """
                You are a pre-market analyst writing a concise briefing for an Indian trader
                who trades Nifty and Bank Nifty.

                Given:
                  • Global market snapshot (oil, gold, USD/INR, US indices, Asian indices, VIX)
                  • ~25 tagged financial news headlines from the last 24 hours

                Output in this EXACT format (no markdown, no preamble):

                BIAS: <BULLISH|BEARISH|NEUTRAL|CAUTIOUS>

                DRIVERS:
                - <bullet 1>
                - <bullet 2>
                - <bullet 3>
                - <bullet 4 (optional)>
                - <bullet 5 (optional)>

                SUMMARY:
                <150-200 word paragraph. Talk directly to the trader. Cover:
                  1. Overnight global sentiment (did US close up/down? Asia opening well?)
                  2. Commodities/FX moves that matter (oil, dollar, gold)
                  3. Key India-specific news (RBI, earnings, policy)
                  4. What to watch today (levels, sectors, catalysts)
                  5. Suggested bias with reasoning
                Be specific with numbers where you have them. Not generic. No hedging fluff.>

                Rules:
                  • Use the DATA I give you — do not invent numbers
                  • If data is missing or "unavailable", say so
                  • No disclaimers ("this is not financial advice")
                  • No promises ("Nifty will hit X")
                """;
    }

    private String buildPrompt(List<MarketQuote> quotes, List<NewsHeadlineEntity> headlines) {
        StringBuilder sb = new StringBuilder();
        sb.append("Today is ").append(LocalDate.now(IST))
          .append(" (IST). Generate the pre-market briefing.\n\n");

        sb.append("GLOBAL MARKETS SNAPSHOT:\n");
        for (MarketQuote q : quotes) {
            sb.append("  • ").append(q.label()).append(": ");
            if (q.price() == 0) {
                sb.append("unavailable\n");
            } else {
                sb.append(String.format("%.2f", q.price()))
                  .append(" (")
                  .append(q.changePct() >= 0 ? "+" : "")
                  .append(String.format("%.2f%%", q.changePct()))
                  .append(")");
                if (q.note() != null && !q.note().isEmpty()) sb.append(" — ").append(q.note());
                sb.append("\n");
            }
        }

        sb.append("\nLAST 24H HEADLINES (sentiment | sector):\n");
        if (headlines.isEmpty()) {
            sb.append("  (no headlines available)\n");
        } else {
            for (NewsHeadlineEntity h : headlines) {
                sb.append("  • [")
                  .append(h.getSentiment() != null ? h.getSentiment() : "UNTAGGED")
                  .append(" | ")
                  .append(h.getSector() != null ? h.getSector() : "GENERAL")
                  .append("] ")
                  .append(h.getTitle())
                  .append("\n");
            }
        }
        return sb.toString();
    }

    // ---------- parse ----------

    private Parsed parseResponse(String raw) {
        Parsed p = new Parsed();
        String[] lines = raw.split("\\r?\\n");
        StringBuilder drivers = new StringBuilder();
        StringBuilder summary = new StringBuilder();
        String section = null;

        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (t.startsWith("BIAS:")) {
                p.bias = t.substring(5).trim().toUpperCase().split("\\s+")[0];
                if (!p.bias.matches("BULLISH|BEARISH|NEUTRAL|CAUTIOUS")) p.bias = "NEUTRAL";
                continue;
            }
            if (t.startsWith("DRIVERS:"))  { section = "DRIVERS"; continue; }
            if (t.startsWith("SUMMARY:"))  { section = "SUMMARY"; continue; }
            if ("DRIVERS".equals(section)) {
                if (drivers.length() > 0) drivers.append('\n');
                drivers.append(t.startsWith("-") ? t : "- " + t);
            } else if ("SUMMARY".equals(section)) {
                if (summary.length() > 0) summary.append(' ');
                summary.append(t);
            }
        }
        p.keyDrivers = drivers.length() == 0 ? null : drivers.toString();
        p.summary    = summary.length() == 0 ? raw     : summary.toString();
        if (p.bias == null) p.bias = "NEUTRAL";
        return p;
    }

    private static class Parsed {
        String bias;
        String keyDrivers;
        String summary;
    }

    // ---------- delivery stubs ----------

    private void maybeSendEmail(BriefingEntity b) {
        if (!props.isEmailEnabled() || props.getEmailTo() == null || props.getEmailTo().isBlank()) return;
        log.info("Briefing: email delivery would fire to {} (not implemented — configure SMTP first)",
                props.getEmailTo());
        // TODO: wire Spring Mail (JavaMailSender). Then set b.setEmailSent(true) + repo.save(b)
    }

    private void maybeSendTelegram(BriefingEntity b) {
        if (!props.isTelegramEnabled() || props.getTelegramBotToken() == null
                || props.getTelegramBotToken().isBlank()) return;
        log.info("Briefing: telegram delivery would fire (not implemented — configure bot token first)");
        // TODO: POST to https://api.telegram.org/bot<TOKEN>/sendMessage
    }
}
