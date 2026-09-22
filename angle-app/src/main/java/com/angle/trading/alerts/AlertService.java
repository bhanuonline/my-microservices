package com.angle.trading.alerts;

import com.angle.trading.config.AlertsProperties;
import com.angle.trading.paper.model.PaperPosition;
import com.angle.trading.strategy.model.IntentAction;
import com.angle.trading.strategy.model.Trade;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Fan-out alerts to every enabled {@link AlertChannel}.
 *
 * Concurrency:
 *   All sends run on a single daemon thread via {@code executor}. Calls
 *   from trading threads (session, poller) are fire-and-forget — they
 *   return in microseconds regardless of network latency. If Telegram
 *   is slow or down, trading is never blocked.
 *
 * Best-effort:
 *   The alert executor swallows any exception a channel throws — one
 *   broken channel doesn't stop the others.
 *
 * Convenience methods (notifyTradeOpen, notifyTradeClose, ...) apply the
 * per-event toggle before enqueueing, so you can silence noisy events
 * via config without changing calling code.
 */
@Slf4j
@Service
public class AlertService {

    private final AlertsProperties  props;
    private final List<AlertChannel> channels;
    private final ExecutorService   executor;

    public AlertService(AlertsProperties props, List<AlertChannel> channels) {
        this.props    = props;
        this.channels = channels;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "alert-sender");
            t.setDaemon(true);
            return t;
        });
        log.info("AlertService initialised: enabled={}, channels={}",
                props.isEnabled(),
                channels.stream().map(AlertChannel::name).toList());
    }

    // -------------------- Public per-event convenience --------------------

    public void notifyTradeOpen(String sessionShortId, String strategyName, String sourceName,
                                PaperPosition pos) {
        if (!props.isOnTradeOpen()) return;
        String emoji = pos.direction() == IntentAction.ENTER_LONG ? "🟢" : "🔴";
        String side  = pos.direction() == IntentAction.ENTER_LONG ? "LONG"  : "SHORT";
        String text = String.format(
                "%s OPEN %s%n" +
                        "Strategy: %s%n" +
                        "Source: %s%n" +
                        "Entry: %s%n" +
                        "Stop: %s%n" +
                        "Target: %s%n" +
                        "Session: %s%n" +
                        "Reason: %s",
                emoji, side,
                strategyName,
                sourceName,
                fmt(pos.entry()),
                fmt(pos.stop()),
                fmt(pos.target()),
                sessionShortId,
                pos.rationale() == null ? "-" : pos.rationale()
        );
        enqueue(AlertLevel.INFO, text);
    }

    public void notifyTradeClose(String sessionShortId, String strategyName, String sourceName,
                                 Trade t) {
        if (!props.isOnTradeClose()) return;
        boolean win = t.pnl().signum() >= 0;
        String emoji = win ? "✅" : "❌";
        String side  = t.direction() == IntentAction.ENTER_LONG ? "LONG" : "SHORT";
        String pnl   = (win ? "+" : "") + fmt(t.pnl());
        String text = String.format(
                "%s CLOSE %s (%s)%n" +
                        "Strategy: %s%n" +
                        "Source: %s%n" +
                        "Exit: %s%n" +
                        "P&L: %s%n" +
                        "Session: %s",
                emoji, side, t.exitReason(),
                strategyName,
                sourceName,
                fmt(t.exitPrice()),
                pnl,
                sessionShortId
        );
        enqueue(AlertLevel.INFO, text);
    }

    public void notifySessionEnd(String sessionShortId, String strategyName, String sourceName,
                                 int totalTrades, int winners, int losers, BigDecimal netPnl) {
        if (!props.isOnSessionEnd()) return;
        String sign = netPnl.signum() >= 0 ? "+" : "";
        String text = String.format(
                "🏁 SESSION END%n" +
                        "Strategy: %s%n" +
                        "Source: %s%n" +
                        "Trades: %d (wins %d / losses %d)%n" +
                        "Net: %s%s%n" +
                        "Session: %s",
                strategyName,
                sourceName,
                totalTrades, winners, losers,
                sign, fmt(netPnl),
                sessionShortId
        );
        enqueue(AlertLevel.INFO, text);
    }

    public void notifyError(String context, String message) {
        if (!props.isOnErrors()) return;
        enqueue(AlertLevel.ERROR, "⚠️ ERROR — " + context + "\n" + message);
    }

    // -------------------- Core enqueue --------------------

    private void enqueue(AlertLevel level, String text) {
        if (!props.isEnabled()) return;
        List<AlertChannel> active = channels.stream().filter(AlertChannel::enabled).toList();
        if (active.isEmpty()) return;
        executor.submit(() -> {
            for (AlertChannel ch : active) {
                try {
                    ch.send(level, text);
                } catch (Exception e) {
                    log.warn("Alert channel {} threw: {}", ch.name(), e.getMessage());
                }
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String fmt(BigDecimal v) {
        return v == null ? "-" : v.toPlainString();
    }
}
