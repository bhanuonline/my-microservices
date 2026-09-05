package com.angle.trading.report;

import com.angle.trading.paper.persistence.SessionEntity;
import com.angle.trading.paper.persistence.TradeEntity;
import com.angle.trading.strategy.model.IntentAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Builds a Markdown daily report from persisted sessions and trades.
 * Pure formatting — no I/O, no time queries. Given data → returns string.
 */
@Slf4j
@Component
public class DailyReportWriter {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DAY  = DateTimeFormatter.ofPattern("EEEE").withZone(IST);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(IST);

    public String render(LocalDate date, List<SessionEntity> sessions, Map<String, List<TradeEntity>> tradesBySession) {
        StringBuilder sb = new StringBuilder(4096);

        sb.append("# Trading Analysis — ").append(date)
                .append(" (").append(DAY.format(date.atStartOfDay(IST).toInstant())).append(")\n\n");

        // Overview
        int totalTrades = tradesBySession.values().stream().mapToInt(List::size).sum();
        long winners    = tradesBySession.values().stream().flatMap(List::stream).filter(t -> t.getPnl().signum() > 0).count();
        long losers     = tradesBySession.values().stream().flatMap(List::stream).filter(t -> t.getPnl().signum() < 0).count();
        BigDecimal netPnl = tradesBySession.values().stream().flatMap(List::stream)
                .map(TradeEntity::getPnl).reduce(BigDecimal.ZERO, BigDecimal::add);

        sb.append("## Overview\n");
        sb.append("- Sessions:    **").append(sessions.size()).append("**\n");
        sb.append("- Trades:      **").append(totalTrades).append("** (wins ").append(winners)
                .append(" / losses ").append(losers).append(")\n");
        sb.append("- Net P&L:     **").append(sign(netPnl)).append(fmt(netPnl)).append("**\n\n");

        // Sessions table
        sb.append("## Sessions\n\n");
        if (sessions.isEmpty()) {
            sb.append("_No sessions ran today._\n\n");
        } else {
            sb.append("| Session | Strategy | Source | Started | Status | Trades | Wins | Losses | Net |\n");
            sb.append("|---------|----------|--------|---------|--------|--------|------|--------|-----|\n");
            for (SessionEntity s : sessions) {
                sb.append("| ").append(shortId(s.getId()))
                        .append(" | ").append(s.getStrategyName())
                        .append(" | ").append(compactSource(s.getSourceName()))
                        .append(" | ").append(TIME.format(s.getStartedAt()))
                        .append(" | ").append(s.getStatus())
                        .append(" | ").append(s.getTotalTrades())
                        .append(" | ").append(s.getWinners())
                        .append(" | ").append(s.getLosers())
                        .append(" | ").append(sign(s.getNetPnl())).append(fmt(s.getNetPnl()))
                        .append(" |\n");
            }
            sb.append("\n");
        }

        // Trades (chronological across all sessions)
        sb.append("## Trades\n\n");
        List<TradeEntity> allTrades = tradesBySession.values().stream()
                .flatMap(List::stream)
                .sorted((a, b) -> a.getEntryTime().compareTo(b.getEntryTime()))
                .toList();

        if (allTrades.isEmpty()) {
            sb.append("_No trades executed today._\n\n");
        } else {
            for (int i = 0; i < allTrades.size(); i++) {
                TradeEntity t = allTrades.get(i);
                String side = t.getDirection() == IntentAction.ENTER_LONG ? "LONG" : "SHORT";
                String emoji = t.getPnl().signum() >= 0 ? "✅" : "❌";
                sb.append("### #").append(i + 1).append("  ").append(side)
                        .append("  ").append(emoji).append(" ").append(sign(t.getPnl())).append(fmt(t.getPnl())).append("\n");
                sb.append("- Session: `").append(shortId(t.getSessionId())).append("`\n");
                sb.append("- Entry:   ").append(fmt(t.getEntryPrice()))
                        .append(" @ ").append(TIME.format(t.getEntryTime())).append("\n");
                sb.append("- Exit:    ").append(fmt(t.getExitPrice()))
                        .append(" @ ").append(TIME.format(t.getExitTime()))
                        .append("  (").append(t.getExitReason()).append(")\n");
                sb.append("- Stop:    ").append(fmt(t.getStopLoss())).append("\n");
                sb.append("- Target:  ").append(fmt(t.getTarget())).append("\n");
                if (t.getRationale() != null && !t.getRationale().isBlank()) {
                    sb.append("- Why:     ").append(t.getRationale()).append("\n");
                }
                sb.append("\n");
            }
        }

        // Manual notes stub — user edits this section
        sb.append("## Manual Notes  _(edit this section yourself)_\n\n");
        sb.append("- [ ] Trades I actually took in Angel app: \n");
        sb.append("- [ ] Real P&L today: \n");
        sb.append("- [ ] Slippage / issues: \n");
        sb.append("- [ ] Ideas for tomorrow: \n\n");

        sb.append("---\n");
        sb.append("_Generated at ").append(TIME.format(Instant.now())).append(" IST_\n");
        return sb.toString();
    }

    // ---- helpers ----

    private static String shortId(String uuid) {
        return uuid == null ? "?" : uuid.substring(0, Math.min(8, uuid.length()));
    }

    private static String sign(BigDecimal v) {
        if (v == null || v.signum() == 0) return "";
        return v.signum() > 0 ? "+" : "";
    }

    private static String fmt(BigDecimal v) {
        return v == null ? "—" : v.toPlainString();
    }

    /** Trim the noisy poll-interval and size suffix so the source fits in a table cell. */
    private static String compactSource(String src) {
        if (src == null) return "";
        int comma = src.indexOf(',');
        int paren = src.indexOf('(');
        if (paren >= 0 && comma > paren) return src.substring(0, comma) + ")";
        return src;
    }
}
