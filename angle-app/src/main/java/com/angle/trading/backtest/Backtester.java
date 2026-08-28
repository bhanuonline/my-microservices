package com.angle.trading.backtest;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.strategy.Strategy;
import com.angle.trading.strategy.model.ExitReason;
import com.angle.trading.strategy.model.IntentAction;
import com.angle.trading.strategy.model.Trade;
import com.angle.trading.strategy.model.TradeIntent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a Strategy over a list of candles and reports the trades it would
 * have made.
 *
 *   - Supports long AND short.
 *   - Honours stop-loss and target when the strategy sets them.
 *   - Falls back to SIGNAL_EXIT when a strategy emits EXIT (or opposite-
 *     direction ENTER) with no auto-exit levels.
 *   - Closes any open position at end-of-series so P&L is realised.
 *
 * Simplifications (fine for study/comparison, not for live capital):
 *   - One position at a time, one unit each.
 *   - No fees, no slippage, no partial fills.
 *   - Stop/target checked against the candle's OHLC range only. If both
 *     hit in the same candle, stop wins (conservative).
 */
@Slf4j
@Service
public class Backtester {

    public BacktestResult run(Strategy strategy, List<Candle> candles) {
        List<TradeIntent> intents = strategy.evaluate(candles);
        List<Trade> trades = new ArrayList<>();

        BigDecimal netProfit = BigDecimal.ZERO;
        int winners = 0;
        int losers  = 0;
        Position position = null;

        for (int i = 0; i < candles.size(); i++) {
            Candle       c      = candles.get(i);
            TradeIntent  intent = intents.get(i);

            // 1. Check auto-exit on any open position (stop / target)
            if (position != null) {
                ExitOutcome auto = checkAutoExit(position, c);
                if (auto != null) {
                    Trade t = closeTrade(position, c.timestamp(), auto.price(), auto.reason());
                    trades.add(t);
                    netProfit = netProfit.add(t.pnl());
                    if (t.pnl().signum() > 0) winners++;
                    else if (t.pnl().signum() < 0) losers++;
                    position = null;
                }
            }

            // 2. Check signal-based actions
            if (position != null) {
                boolean closeSignal = intent.action() == IntentAction.EXIT
                        || (position.direction == IntentAction.ENTER_LONG
                                && intent.action() == IntentAction.ENTER_SHORT)
                        || (position.direction == IntentAction.ENTER_SHORT
                                && intent.action() == IntentAction.ENTER_LONG);
                if (closeSignal) {
                    Trade t = closeTrade(position, c.timestamp(), c.close(), ExitReason.SIGNAL_EXIT);
                    trades.add(t);
                    netProfit = netProfit.add(t.pnl());
                    if (t.pnl().signum() > 0) winners++;
                    else if (t.pnl().signum() < 0) losers++;
                    position = null;
                }
            }

            // 3. Open a new position if flat + entry intent
            if (position == null) {
                if (intent.action() == IntentAction.ENTER_LONG
                        || intent.action() == IntentAction.ENTER_SHORT) {
                    BigDecimal entry = intent.entry() != null ? intent.entry() : c.close();
                    position = new Position(intent.action(), c.timestamp(), entry,
                            intent.stop(), intent.target(), intent.rationale());
                }
            }
        }

        // Force-close any position hanging at end of series so P&L is realised.
        if (position != null && !candles.isEmpty()) {
            Candle last = candles.get(candles.size() - 1);
            Trade t = closeTrade(position, last.timestamp(), last.close(), ExitReason.END_OF_SERIES);
            trades.add(t);
            netProfit = netProfit.add(t.pnl());
            if (t.pnl().signum() > 0) winners++;
            else if (t.pnl().signum() < 0) losers++;
        }

        BacktestResult result = new BacktestResult(
                strategy.name(), candles.size(), trades.size(),
                winners, losers, netProfit, trades);
        log.info("Backtest {}: candles={}, trades={}, wins={}, losses={}, net={}",
                strategy.name(), candles.size(), trades.size(), winners, losers, netProfit);
        return result;
    }

    private static ExitOutcome checkAutoExit(Position pos, Candle c) {
        if (pos.direction == IntentAction.ENTER_LONG) {
            boolean stopHit   = pos.stop   != null && c.low().compareTo(pos.stop)   <= 0;
            boolean targetHit = pos.target != null && c.high().compareTo(pos.target) >= 0;
            if (stopHit)   return new ExitOutcome(pos.stop,   ExitReason.STOP);
            if (targetHit) return new ExitOutcome(pos.target, ExitReason.TARGET);
        } else { // ENTER_SHORT
            boolean stopHit   = pos.stop   != null && c.high().compareTo(pos.stop)   >= 0;
            boolean targetHit = pos.target != null && c.low().compareTo(pos.target)  <= 0;
            if (stopHit)   return new ExitOutcome(pos.stop,   ExitReason.STOP);
            if (targetHit) return new ExitOutcome(pos.target, ExitReason.TARGET);
        }
        return null;
    }

    private static Trade closeTrade(Position pos, Instant exitTime,
                                    BigDecimal exitPrice, ExitReason reason) {
        BigDecimal pnl = (pos.direction == IntentAction.ENTER_LONG)
                ? exitPrice.subtract(pos.entry)
                : pos.entry.subtract(exitPrice);
        return new Trade(
                pos.direction,
                pos.entryTime, pos.entry,
                exitTime, exitPrice,
                pos.stop, pos.target,
                reason, pnl, pos.rationale
        );
    }

    /** Internal record of an open position. */
    private record Position(
            IntentAction direction,
            Instant entryTime,
            BigDecimal entry,
            BigDecimal stop,
            BigDecimal target,
            String rationale
    ) {}

    private record ExitOutcome(BigDecimal price, ExitReason reason) {}
}
