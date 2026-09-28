package com.angle.trading.ai;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.config.AiPickProperties;
import com.angle.trading.indicator.RelativeStrengthIndex;
import com.angle.trading.marketdata.MarketDataService;
import com.angle.trading.persistence.AiPickEntity;
import com.angle.trading.persistence.AiPickRepository;
import com.angle.trading.persistence.BiasInstrumentEntity;
import com.angle.trading.service.InstrumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates AI stock picks for the portfolio.
 *
 * Flow:
 *   1. Enumerate enabled instruments (bias watch-list).
 *   2. For each, fetch last N daily candles + compute a few metrics.
 *   3. Ship it all to Claude with a strict-format prompt.
 *   4. Parse the response into per-symbol picks.
 *   5. Dedupe against recent OPEN picks, save the rest.
 *
 * All persistence is here (via AiPickRepository); the controller just calls
 * {@link #generate()} and gets back a report of what happened.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPickService {

    private final AiPickProperties props;
    private final InstrumentService instrumentService;
    private final MarketDataService marketDataService;
    private final AnthropicClient anthropic;
    private final AiPickRepository repo;
    private final AiProperties aiProperties;

    private final RelativeStrengthIndex rsi14 = new RelativeStrengthIndex(14);

    // ============================================================
    // Entry point
    // ============================================================

    @Transactional
    public GenerateResult generate() {
        if (!props.isEnabled()) return GenerateResult.disabled("ai-picks.enabled=false");
        if (!aiEnabled())        return GenerateResult.disabled("AI provider disabled or missing key");

        List<BiasInstrumentEntity> universe = instrumentService.listEnabled().stream()
                .filter(i -> i.getExchange() != null)
                .limit(props.getMaxUniverseSize())
                .toList();
        if (universe.isEmpty()) return GenerateResult.error("No enabled instruments to scan");

        // 1. Gather price context for each stock
        List<StockContext> contexts = new ArrayList<>();
        for (BiasInstrumentEntity inst : universe) {
            try {
                StockContext ctx = buildContext(inst);
                if (ctx != null) contexts.add(ctx);
            } catch (Exception e) {
                log.debug("AI-picks: skipping {} — {}", inst.getSymbol(), e.getMessage());
            }
        }
        if (contexts.isEmpty()) return GenerateResult.error("No stocks had usable candle data");
        log.info("AI-picks: sending {} stocks to Claude for analysis", contexts.size());

        // 2. Ask Claude
        String rawResponse;
        try {
            rawResponse = anthropic.complete(systemPrompt(), userPrompt(contexts));
        } catch (Exception e) {
            log.warn("AI-picks: Claude call failed: {}", e.getMessage());
            return GenerateResult.error("Claude call failed: " + e.getMessage());
        }

        // 3. Parse response — one pick per line, pipe-delimited
        List<ParsedPick> parsed = parseResponse(rawResponse);
        if (parsed.isEmpty()) {
            log.warn("AI-picks: Claude returned zero parseable picks. Raw: {}",
                    rawResponse.length() > 400 ? rawResponse.substring(0, 400) : rawResponse);
            return GenerateResult.error("Claude response was empty or unparseable");
        }

        // 4. Filter by confidence + dedupe + save
        int minConf = confidenceLevel(props.getMinConfidence());
        Instant dedupeCutoff = Instant.now().minusSeconds(props.getDedupeDays() * 86400L);
        List<AiPickEntity> saved = new ArrayList<>();
        int skippedConfidence = 0;
        int skippedDedupe = 0;

        for (ParsedPick p : parsed) {
            if (confidenceLevel(p.confidence) < minConf) { skippedConfidence++; continue; }
            if ("HOLD".equalsIgnoreCase(p.action) || "AVOID".equalsIgnoreCase(p.action)) continue;

            StockContext ctx = contexts.stream()
                    .filter(c -> c.symbol.equalsIgnoreCase(p.symbol) || c.symbolToken.equals(p.symbol))
                    .findFirst().orElse(null);
            if (ctx == null) continue;

            if (repo.countRecentOpen(ctx.symbolToken, dedupeCutoff) > 0) {
                skippedDedupe++;
                continue;
            }

            AiPickEntity e = new AiPickEntity();
            e.setCreatedAt(Instant.now());
            e.setSymbolToken(ctx.symbolToken);
            e.setSymbol(ctx.symbol);
            e.setExchange(ctx.exchange);
            e.setAction(p.action.toUpperCase());
            e.setEntryPrice(p.entryPrice != null ? p.entryPrice : ctx.currentPrice);
            e.setTargetPrice(p.targetPrice);
            e.setStopLoss(p.stopLoss);
            e.setConfidence(p.confidence.toUpperCase());
            e.setHorizonDays(p.horizonDays);
            e.setRationale(clip(p.rationale, 1450));
            e.setModel(aiProperties.getAnthropic().getModel());
            repo.save(e);
            saved.add(e);
        }
        log.info("AI-picks: saved={} skipped-confidence={} skipped-dedupe={}",
                saved.size(), skippedConfidence, skippedDedupe);
        return GenerateResult.ok(contexts.size(), parsed.size(), saved.size(),
                skippedConfidence, skippedDedupe, saved);
    }

    // ============================================================
    // Prompt construction
    // ============================================================

    private String systemPrompt() {
        return """
                You are a disciplined equity analyst evaluating Indian stocks for a MEDIUM-TERM
                (1-3 month) investment horizon. You will receive a list of stocks with their
                recent price history and technical metrics.

                For EACH stock, output ONE line in this EXACT pipe-delimited format:

                    SYMBOL | ACTION | ENTRY | TARGET | STOP | CONFIDENCE | HORIZON_DAYS | RATIONALE

                Where:
                  SYMBOL       = the symbol given (verbatim, matches input)
                  ACTION       = BUY | HOLD | AVOID
                  ENTRY        = price you'd enter at (usually current price)
                  TARGET       = price to sell for profit (BUY only)
                  STOP         = stop-loss price (BUY only)
                  CONFIDENCE   = HIGH | MEDIUM | LOW
                  HORIZON_DAYS = 30 | 60 | 90
                  RATIONALE    = 1-2 sentence reason. No pipes in rationale.

                Rules:
                  • Recommend BUY only when you see a clear setup (support bounce,
                    breakout above resistance, oversold reversal, strong trend).
                  • Prefer HOLD when the stock is fine but not compelling right now.
                  • Use AVOID when there's clear downtrend or resistance overhead.
                  • Target should give at least 2:1 risk/reward vs stop.
                  • Do NOT include markdown, headers, or explanation before/after.
                  • One line per stock, nothing else.
                """;
    }

    private String userPrompt(List<StockContext> contexts) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analyze these Indian stocks. Output one line per stock in the required format.\n\n");
        for (StockContext c : contexts) {
            sb.append(c.symbol).append(" (").append(c.symbolToken).append(") · ").append(c.exchange).append("\n");
            sb.append("  Current: ₹").append(c.currentPrice).append("\n");
            sb.append("  60-day range: ₹").append(c.low60).append(" – ₹").append(c.high60).append("\n");
            sb.append("  Change 30d: ").append(c.change30dPct).append("%    90d: ").append(c.change90dPct).append("%\n");
            sb.append("  RSI(14): ").append(c.rsi14).append("    vs 50-day MA: ")
              .append(c.pctVs50ma).append("%\n");
            sb.append("\n");
        }
        return sb.toString();
    }

    // ============================================================
    // Context builder
    // ============================================================

    private StockContext buildContext(BiasInstrumentEntity inst) {
        Exchange ex;
        try { ex = Exchange.valueOf(inst.getExchange()); }
        catch (Exception e) { return null; }

        LocalDate to   = LocalDate.now();
        LocalDate from = to.minusDays(props.getCandlesPerStock() * 2 + 30); // buffer for weekends
        List<Candle> candles = marketDataService.getCandles(
                "ANGEL", ex, inst.getSymbolToken(), Interval.ONE_DAY, from, to);
        if (candles == null || candles.size() < 30) return null;

        int n = candles.size();
        BigDecimal cur = candles.get(n - 1).close();
        int start30 = Math.max(0, n - 30);
        int start60 = Math.max(0, n - 60);
        int start90 = Math.max(0, n - 90);
        int start50 = Math.max(0, n - 50);

        BigDecimal price30ago = candles.get(start30).close();
        BigDecimal price90ago = candles.get(start90).close();
        double change30 = pctChange(cur, price30ago);
        double change90 = pctChange(cur, price90ago);

        // 60-day high/low
        BigDecimal high = cur, low = cur;
        for (int i = start60; i < n; i++) {
            BigDecimal c = candles.get(i).close();
            if (c.compareTo(high) > 0) high = c;
            if (c.compareTo(low)  < 0) low  = c;
        }

        // 50-day moving average
        BigDecimal sum = BigDecimal.ZERO;
        int cnt = 0;
        for (int i = start50; i < n; i++) { sum = sum.add(candles.get(i).close()); cnt++; }
        BigDecimal ma50 = sum.divide(BigDecimal.valueOf(cnt), 2, RoundingMode.HALF_UP);
        double pctVs50 = pctChange(cur, ma50);

        // RSI on last candle
        List<BigDecimal> rsiSeries = rsi14.compute(candles);
        BigDecimal rsi = rsiSeries.isEmpty() ? BigDecimal.valueOf(50) : rsiSeries.get(rsiSeries.size() - 1);

        StockContext ctx = new StockContext();
        ctx.symbol       = inst.getSymbol();
        ctx.symbolToken  = inst.getSymbolToken();
        ctx.exchange     = inst.getExchange();
        ctx.currentPrice = cur.setScale(2, RoundingMode.HALF_UP);
        ctx.high60       = high.setScale(2, RoundingMode.HALF_UP);
        ctx.low60        = low.setScale(2, RoundingMode.HALF_UP);
        ctx.change30dPct = round2(change30);
        ctx.change90dPct = round2(change90);
        ctx.rsi14        = rsi == null ? "n/a" : rsi.setScale(1, RoundingMode.HALF_UP).toString();
        ctx.pctVs50ma    = round2(pctVs50);
        return ctx;
    }

    // ============================================================
    // Response parsing
    // ============================================================

    private List<ParsedPick> parseResponse(String raw) {
        List<ParsedPick> out = new ArrayList<>();
        for (String line : raw.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || !trimmed.contains("|")) continue;
            String[] parts = trimmed.split("\\|");
            if (parts.length < 6) continue;
            try {
                ParsedPick p = new ParsedPick();
                p.symbol      = parts[0].trim();
                p.action      = parts[1].trim().toUpperCase();
                p.entryPrice  = parseDecimalSafe(parts[2]);
                p.targetPrice = parseDecimalSafe(parts[3]);
                p.stopLoss    = parseDecimalSafe(parts[4]);
                p.confidence  = parts[5].trim().toUpperCase();
                p.horizonDays = parts.length > 6 ? parseIntSafe(parts[6], 60) : 60;
                p.rationale   = parts.length > 7 ? parts[7].trim() : "";
                if (p.symbol.isEmpty() || p.action.isEmpty()) continue;
                out.add(p);
            } catch (Exception e) { /* skip bad line */ }
        }
        return out;
    }

    // ============================================================
    // Helpers
    // ============================================================

    private boolean aiEnabled() {
        return aiProperties.isEnabled()
                && aiProperties.getAnthropic().getApiKey() != null
                && !aiProperties.getAnthropic().getApiKey().isBlank();
    }

    private static int confidenceLevel(String c) {
        return switch (c == null ? "" : c.toUpperCase()) {
            case "LOW" -> 1;
            case "MEDIUM" -> 2;
            case "HIGH" -> 3;
            default -> 2;
        };
    }

    private static double pctChange(BigDecimal now, BigDecimal then) {
        if (then == null || then.signum() == 0) return 0.0;
        return now.subtract(then).divide(then, 6, RoundingMode.HALF_UP)
                  .multiply(BigDecimal.valueOf(100)).doubleValue();
    }

    private static String round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).toString();
    }

    private static BigDecimal parseDecimalSafe(String s) {
        try {
            String cleaned = s.trim().replaceAll("[₹, ]", "").replaceAll("[^0-9.-]", "");
            return cleaned.isEmpty() ? null : new BigDecimal(cleaned);
        } catch (Exception e) { return null; }
    }

    private static int parseIntSafe(String s, int fallback) {
        try { return Integer.parseInt(s.trim().replaceAll("[^0-9-]", "")); }
        catch (Exception e) { return fallback; }
    }

    private static String clip(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    // ============================================================
    // Data classes
    // ============================================================

    /** Snapshot of one stock's context sent to Claude. */
    public static class StockContext {
        public String symbol, symbolToken, exchange, rsi14;
        public BigDecimal currentPrice, high60, low60;
        public String change30dPct, change90dPct, pctVs50ma;
    }

    /** One raw pick as parsed from Claude's response. */
    public static class ParsedPick {
        public String symbol, action, confidence, rationale;
        public BigDecimal entryPrice, targetPrice, stopLoss;
        public Integer horizonDays;
    }

    /** What the generate() endpoint returns. */
    public record GenerateResult(
            boolean ok, String message,
            int universeSize, int parsedPicks, int savedPicks,
            int skippedByConfidence, int skippedByDedupe,
            List<AiPickEntity> picks
    ) {
        static GenerateResult ok(int universe, int parsed, int saved, int skipConf, int skipDedupe, List<AiPickEntity> picks) {
            return new GenerateResult(true, "ok", universe, parsed, saved, skipConf, skipDedupe, picks);
        }
        static GenerateResult error(String msg) {
            return new GenerateResult(false, msg, 0, 0, 0, 0, 0, List.of());
        }
        static GenerateResult disabled(String msg) {
            return new GenerateResult(false, msg, 0, 0, 0, 0, 0, List.of());
        }
    }
}
