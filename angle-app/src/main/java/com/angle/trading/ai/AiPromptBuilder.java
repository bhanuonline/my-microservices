package com.angle.trading.ai;

import com.angle.trading.bias.model.BiasSheet;
import com.angle.trading.bias.model.TimeframeBias;
import com.angle.trading.bias.model.TrendFilters;
import org.springframework.stereotype.Component;

/**
 * Serialises a BiasSheet into the prompt text sent to the LLM.
 *
 * Kept dumb-and-deterministic on purpose — no branching, no formatting magic.
 * If we always send the same shape, LLM outputs stay consistent AND we get
 * clean cache hits (same input string → same cache key).
 */
@Component
public class AiPromptBuilder {

    private static final String SYSTEM = """
            You are a technical analyst specializing in Indian equity + F&O markets \
            (NSE / MCX). Given a real-time bias snapshot, produce a concise 3-sentence \
            trade recommendation. Be specific with price levels from the data — never \
            invent numbers. Admit uncertainty when signals conflict.

            Output format (exact):
            ACTION: <LONG|SHORT|WAIT|AVOID> (<high|medium|low> confidence)
            <sentence 1: the recommendation with prices>
            <sentence 2: main confirming signal>
            <sentence 3: main risk or caution>
            KEY: <short bullet> ; <short bullet> ; <short bullet>

            No preamble. No disclaimers. No markdown.""";

    public String system() {
        return SYSTEM;
    }

    /** Turn a bias sheet into a compact user-message payload. */
    public String user(BiasSheet sheet) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("Analyze this bias snapshot for ").append(sheet.symbol()).append(":\n\n");

        sb.append("CONSOLIDATED\n");
        if (sheet.consolidated() != null) {
            sb.append("  Recommendation: ").append(sheet.consolidated().recommendation()).append('\n');
            sb.append("  Score: ").append(sheet.consolidated().totalScore())
              .append(" (").append(sheet.consolidated().strength()).append(")\n");
            sb.append("  Confidence: ").append(sheet.consolidated().confidencePercent()).append("%\n");
        }
        sb.append('\n');

        sb.append("MULTI-TIMEFRAME BIAS\n");
        if (sheet.multiTfBias() != null) {
            for (TimeframeBias t : sheet.multiTfBias()) {
                sb.append("  ").append(t.interval()).append(": ")
                  .append(t.bias() == null ? "—" : t.bias().name())
                  .append(t.analystConfidence() == null ? "" : "  (conf " + t.analystConfidence() + ")")
                  .append('\n');
            }
        }
        sb.append('\n');

        if (sheet.trendFilters() != null) {
            TrendFilters t = sheet.trendFilters();
            sb.append("TREND FILTERS (intraday)\n");
            appendKv(sb, "EMA 9/20/50", t.ema9(), t.ema20(), t.ema50());
            appendKv(sb, "EMA 9 > 20",  t.ema9AboveEma20());
            appendKv(sb, "Price > EMA 50", t.priceAboveEma50());
            appendKv(sb, "VWAP", t.vwap());
            appendKv(sb, "Price > VWAP", t.priceAboveVwap());
            appendKv(sb, "ADX(14)", t.adx14() == null ? null : (t.adx14() + " " + t.adxStrength()));
            appendKv(sb, "ATR(14) %", t.atrPercentOfPrice());
            appendKv(sb, "RSI(14)", t.rsi14());
            appendKv(sb, "MACD hist", t.macdHistogram());
            appendKv(sb, "SuperTrend", t.superTrendLine() == null ? null
                    : (t.superTrendLine() + " " + (Boolean.TRUE.equals(t.superTrendBullish()) ? "BULLISH" : "BEARISH")));
            appendKv(sb, "Volume vs avg", t.volumeRatio() == null ? null : (t.volumeRatio() + "x"));
            sb.append('\n');
        }

        if (sheet.marketContext() != null) {
            sb.append("MARKET CONTEXT\n");
            appendKv(sb, "Current price", sheet.marketContext().currentPrice());
            appendKv(sb, "Prev close",    sheet.marketContext().previousClose());
            appendKv(sb, "Day high",      sheet.marketContext().dayHigh());
            appendKv(sb, "Day low",       sheet.marketContext().dayLow());
            appendKv(sb, "Gap %",         sheet.marketContext().gapPercent());
            sb.append('\n');
        }

        if (sheet.structure() != null && sheet.structure().lastEvent() != null) {
            sb.append("STRUCTURE\n");
            sb.append("  Last event: ").append(sheet.structure().lastEvent().type())
              .append(' ').append(sheet.structure().lastEvent().direction())
              .append(" @ ").append(sheet.structure().lastEvent().brokenLevel()).append('\n');
            appendKv(sb, "Latest swing high", sheet.structure().latestSwingHigh());
            appendKv(sb, "Latest swing low",  sheet.structure().latestSwingLow());
            sb.append('\n');
        }

        if (sheet.vix() != null && sheet.vix().value() != null) {
            sb.append("VIX: ").append(sheet.vix().value())
              .append(" (").append(sheet.vix().regime()).append(")\n");
        }
        if (sheet.correlated() != null && sheet.correlated().signal() != null) {
            sb.append("CORRELATED: ").append(sheet.correlated().signal()).append('\n');
        }
        if (sheet.breadth() != null && sheet.breadth().regime() != null) {
            sb.append("BREADTH: ").append(sheet.breadth().regime()).append('\n');
        }

        sb.append("\nReply in the exact format specified in the system prompt.");
        return sb.toString();
    }

    private static void appendKv(StringBuilder sb, String key, Object v) {
        if (v == null) return;
        sb.append("  ").append(key).append(": ").append(v).append('\n');
    }

    private static void appendKv(StringBuilder sb, String key, Object v1, Object v2, Object v3) {
        if (v1 == null && v2 == null && v3 == null) return;
        sb.append("  ").append(key).append(": ")
          .append(v1 == null ? "—" : v1).append(" / ")
          .append(v2 == null ? "—" : v2).append(" / ")
          .append(v3 == null ? "—" : v3).append('\n');
    }
}
