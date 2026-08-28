package com.angle.trading.paper;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.paper.model.PaperPosition;
import com.angle.trading.paper.model.SessionSnapshot;
import com.angle.trading.paper.model.SessionStatus;
import com.angle.trading.paper.source.CandleSource;
import com.angle.trading.strategy.Strategy;
import com.angle.trading.strategy.model.ExitReason;
import com.angle.trading.strategy.model.IntentAction;
import com.angle.trading.strategy.model.Trade;
import com.angle.trading.strategy.model.TradeIntent;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One paper-trading session — a strategy running against a live candle stream.
 *
 * Lifecycle:
 *   new PaperTradingSession(strategy, source) — RUNNING
 *   source.start(session::onCandle, session::onComplete)
 *   each new candle:
 *     - append to internal series
 *     - re-run strategy on full series
 *     - inspect the intent for the LATEST candle
 *     - book auto-exits (stop/target) first, then signal exits, then entries
 *   source finishes → status = COMPLETED (open position force-closed at last price)
 *   user calls stop() → status = STOPPED (open position force-closed at last price)
 *
 * Console output (all at INFO):
 *   session start / stop / complete
 *   every position OPEN                 → OPEN long @ ... stop ... target ...
 *   every position CLOSE with P&L       → CLOSE long @ ... (TARGET) pnl=+42.50
 *   periodic status every N candles     → status: candles=200 trades=3 wins=2 net=+85
 */
@Slf4j
public class PaperTradingSession {

    /** How often to print a running status line (in candles). */
    private static final int STATUS_EVERY_N_CANDLES = 50;

    private final String id;
    private final Strategy strategy;
    private final CandleSource source;
    private final PaperOrderBook book = new PaperOrderBook();

    private final List<Candle> candles = new ArrayList<>();
    private volatile SessionStatus status = SessionStatus.RUNNING;
    private volatile Instant startedAt;
    private volatile Instant lastCandleAt;
    private volatile TradeIntent lastIntent = TradeIntent.hold();

    public PaperTradingSession(Strategy strategy, CandleSource source) {
        this.id = UUID.randomUUID().toString();
        this.strategy = strategy;
        this.source = source;
    }

    public void start() {
        this.startedAt = Instant.now();
        source.start(this::processCandle, this::onSourceComplete);
        log.info("┌─ SESSION START [{}] strategy={} source={}",
                shortId(), strategy.name(), source.name());
    }

    /** User-initiated stop. Force-closes any open position at the last candle's close. */
    public synchronized void stop() {
        if (status != SessionStatus.RUNNING) return;
        source.stop();
        forceCloseAtLastPrice(ExitReason.SIGNAL_EXIT);
        status = SessionStatus.STOPPED;
        log.info("└─ SESSION STOPPED [{}] {}", shortId(), summaryLine());
    }

    private synchronized void onSourceComplete() {
        if (status != SessionStatus.RUNNING) return;
        forceCloseAtLastPrice(ExitReason.END_OF_SERIES);
        status = SessionStatus.COMPLETED;
        log.info("└─ SESSION COMPLETED [{}] {}", shortId(), summaryLine());
    }

    private void forceCloseAtLastPrice(ExitReason reason) {
        if (book.getOpen() == null || candles.isEmpty()) return;
        Candle last = candles.get(candles.size() - 1);
        Trade t = book.close(last.timestamp(), last.close(), reason);
        logClose(t);
    }

    private synchronized void processCandle(Candle c) {
        if (status != SessionStatus.RUNNING) return;
        candles.add(c);
        lastCandleAt = c.timestamp();

        // Re-run the strategy on the whole series. Simple, robust, quadratic in
        // candle count — fine for study-sized data.
        List<TradeIntent> intents = strategy.evaluate(candles);
        TradeIntent intent = intents.get(intents.size() - 1);
        this.lastIntent = intent;

        // 1. Auto-exit (stop / target) on any open position
        PaperPosition open = book.getOpen();
        if (open != null) {
            BigDecimal exitPrice = checkAutoExitPrice(open, c);
            if (exitPrice != null) {
                ExitReason reason = exitPriceIsStop(open, exitPrice) ? ExitReason.STOP : ExitReason.TARGET;
                Trade t = book.close(c.timestamp(), exitPrice, reason);
                logClose(t);
            }
        }

        // 2. Signal-based exit — EXIT intent, or opposite-direction entry
        open = book.getOpen();
        if (open != null) {
            boolean closeSignal = intent.action() == IntentAction.EXIT
                    || (open.direction() == IntentAction.ENTER_LONG
                            && intent.action() == IntentAction.ENTER_SHORT)
                    || (open.direction() == IntentAction.ENTER_SHORT
                            && intent.action() == IntentAction.ENTER_LONG);
            if (closeSignal) {
                Trade t = book.close(c.timestamp(), c.close(), ExitReason.SIGNAL_EXIT);
                logClose(t);
            }
        }

        // 3. Open a new position if flat + entry intent
        if (book.getOpen() == null
                && (intent.action() == IntentAction.ENTER_LONG
                        || intent.action() == IntentAction.ENTER_SHORT)) {
            BigDecimal entry = intent.entry() != null ? intent.entry() : c.close();
            PaperPosition opened = book.openPosition(intent.action(), c.timestamp(), entry,
                    intent.stop(), intent.target(), intent.rationale());
            logOpen(opened);
        }

        // Periodic status
        if (candles.size() % STATUS_EVERY_N_CANDLES == 0) {
            log.info("│  [{}] {}", shortId(), summaryLine());
        }
    }

    private void logOpen(PaperPosition pos) {
        String side = pos.direction() == IntentAction.ENTER_LONG ? "LONG " : "SHORT";
        log.info("│  [{}] OPEN  {} @ {}  stop={} target={}  :: {}",
                shortId(), side,
                fmt(pos.entry()),
                fmt(pos.stop()),
                fmt(pos.target()),
                pos.rationale());
    }

    private void logClose(Trade t) {
        String side = t.direction() == IntentAction.ENTER_LONG ? "LONG " : "SHORT";
        String pnl = (t.pnl().signum() >= 0 ? "+" : "") + fmt(t.pnl());
        log.info("│  [{}] CLOSE {} @ {}  ({})  pnl={}",
                shortId(), side, fmt(t.exitPrice()), t.exitReason(), pnl);
    }

    private String summaryLine() {
        BigDecimal net = book.getNetPnl();
        String sign = net.signum() >= 0 ? "+" : "";
        return String.format("candles=%d trades=%d wins=%d losses=%d net=%s%s",
                candles.size(), book.getTradeCount(),
                book.getWinners(), book.getLosers(), sign, fmt(net));
    }

    private String shortId() {
        return id.substring(0, 8);
    }

    private static String fmt(BigDecimal v) {
        return v == null ? "—" : v.toPlainString();
    }

    private static BigDecimal checkAutoExitPrice(PaperPosition pos, Candle c) {
        if (pos.direction() == IntentAction.ENTER_LONG) {
            if (pos.stop()   != null && c.low().compareTo(pos.stop())   <= 0) return pos.stop();
            if (pos.target() != null && c.high().compareTo(pos.target()) >= 0) return pos.target();
        } else {
            if (pos.stop()   != null && c.high().compareTo(pos.stop())   >= 0) return pos.stop();
            if (pos.target() != null && c.low().compareTo(pos.target())  <= 0) return pos.target();
        }
        return null;
    }

    private static boolean exitPriceIsStop(PaperPosition pos, BigDecimal exitPrice) {
        return pos.stop() != null && pos.stop().compareTo(exitPrice) == 0;
    }

    public synchronized SessionSnapshot snapshot() {
        return new SessionSnapshot(
                id, strategy.name(), source.name(),
                status, startedAt, lastCandleAt,
                candles.size(),
                book.getTradeCount(), book.getWinners(), book.getLosers(),
                book.getNetPnl(),
                lastIntent, book.getOpen(),
                book.snapshotTrades()
        );
    }

    public String getId()          { return id; }
    public SessionStatus getStatus() { return status; }
}
