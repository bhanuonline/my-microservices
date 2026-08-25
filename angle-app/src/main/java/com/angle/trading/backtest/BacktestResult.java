package com.angle.trading.backtest;

import com.angle.trading.strategy.Signal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Summary of a backtest run: overall stats plus each signal event.
 */
public record BacktestResult(
        String strategyName,
        int totalCandles,
        int buys,
        int sells,
        BigDecimal netProfit,
        List<Trade> trades
) {
    public record Trade(
            Instant timestamp,
            Signal signal,
            BigDecimal price
    ) {}
}
