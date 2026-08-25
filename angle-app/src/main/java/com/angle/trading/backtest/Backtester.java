package com.angle.trading.backtest;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.strategy.Signal;
import com.angle.trading.strategy.Strategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a Strategy over a list of candles and reports what would have happened.
 *
 * Simple model: one unit long-only. BUY opens a position at the candle's close;
 * SELL closes it and books P&L. No fees, no slippage, no position sizing.
 * Good enough to compare strategies; not suitable for live capital decisions.
 */
@Slf4j
@Service
public class Backtester {

    public BacktestResult run(Strategy strategy, List<Candle> candles) {
        List<Signal> signals = strategy.evaluate(candles);
        List<BacktestResult.Trade> trades = new ArrayList<>();

        BigDecimal netProfit = BigDecimal.ZERO;
        BigDecimal openPrice = null;
        int buys = 0;
        int sells = 0;

        for (int i = 0; i < candles.size(); i++) {
            Signal sig = signals.get(i);
            Candle c = candles.get(i);
            if (sig == Signal.BUY && openPrice == null) {
                openPrice = c.close();
                buys++;
                trades.add(new BacktestResult.Trade(c.timestamp(), Signal.BUY, c.close()));
            } else if (sig == Signal.SELL && openPrice != null) {
                netProfit = netProfit.add(c.close().subtract(openPrice));
                openPrice = null;
                sells++;
                trades.add(new BacktestResult.Trade(c.timestamp(), Signal.SELL, c.close()));
            }
        }

        BacktestResult result = new BacktestResult(
                strategy.name(), candles.size(), buys, sells, netProfit, trades);
        log.info("Backtest {}: candles={}, buys={}, sells={}, net={}",
                strategy.name(), candles.size(), buys, sells, netProfit);
        return result;
    }
}
