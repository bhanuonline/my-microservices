package com.angle.trading.backtest;

import com.angle.trading.strategy.model.Trade;

import java.math.BigDecimal;
import java.util.List;

/**
 * Summary of a backtest run: overall stats plus each completed trade.
 *
 *   totalTrades     — round-trips completed (entry + exit)
 *   winners/losers  — trades with positive / negative P&L
 *   netProfit       — sum of all trade P&Ls
 *   trades          — chronological list; useful for auditing / plotting
 */
public record BacktestResult(
        String strategyName,
        int totalCandles,
        int totalTrades,
        int winners,
        int losers,
        BigDecimal netProfit,
        List<Trade> trades
) {}
