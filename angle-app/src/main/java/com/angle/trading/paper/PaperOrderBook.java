package com.angle.trading.paper;

import com.angle.trading.paper.model.PaperPosition;
import com.angle.trading.strategy.model.ExitReason;
import com.angle.trading.strategy.model.IntentAction;
import com.angle.trading.strategy.model.Trade;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory position + trade log for one paper-trading session.
 *
 * Concurrency: writes happen on the session's own thread (either the
 * replay executor or the live-poll executor). Reads happen on the HTTP
 * thread (GET /sessions/{id}). All public methods are synchronized so
 * readers see a consistent snapshot.
 *
 * One position at a time (matches the backtester). If you want stacked
 * positions later, the book needs a list of open positions instead of
 * a single field.
 */
public class PaperOrderBook {

    private PaperPosition open;
    private final List<Trade> trades = new ArrayList<>();
    private BigDecimal netPnl = BigDecimal.ZERO;
    private int winners;
    private int losers;

    public synchronized PaperPosition openPosition(IntentAction direction, Instant time,
                                                    BigDecimal entry, BigDecimal stop,
                                                    BigDecimal target, String rationale) {
        if (open != null) throw new IllegalStateException("Already have an open position");
        this.open = new PaperPosition(direction, time, entry, stop, target, rationale);
        return this.open;
    }

    public synchronized Trade close(Instant exitTime, BigDecimal exitPrice, ExitReason reason) {
        if (open == null) throw new IllegalStateException("No open position to close");
        BigDecimal pnl = (open.direction() == IntentAction.ENTER_LONG)
                ? exitPrice.subtract(open.entry())
                : open.entry().subtract(exitPrice);
        Trade t = new Trade(
                open.direction(),
                open.entryTime(), open.entry(),
                exitTime, exitPrice,
                open.stop(), open.target(),
                reason, pnl, open.rationale()
        );
        trades.add(t);
        netPnl = netPnl.add(pnl);
        if (pnl.signum() > 0) winners++;
        else if (pnl.signum() < 0) losers++;
        open = null;
        return t;
    }

    public synchronized PaperPosition getOpen() {
        return open;
    }

    public synchronized List<Trade> snapshotTrades() {
        return List.copyOf(trades);
    }

    public synchronized BigDecimal getNetPnl() {
        return netPnl;
    }

    public synchronized int getWinners() { return winners; }
    public synchronized int getLosers()  { return losers;  }
    public synchronized int getTradeCount() { return trades.size(); }
}
