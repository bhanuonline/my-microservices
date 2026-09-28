package com.angle.trading.backtest;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One simulated trade with full transparency:
 *   agreedStrategyNames  — which strategies actually voted (comma-sep list)
 *   lots + units         — how many contracts + total units traded
 *   notional             — position value in ₹ = units × entryPrice
 */
public record PipelineBacktestTrade(
        int         index,
        Instant     entryAt,
        String      action,
        BigDecimal  entryPrice,
        BigDecimal  targetPrice,
        BigDecimal  stopPrice,
        int         agreedStrategies,
        String      agreedStrategyNames,   // e.g. "moving-average-crossover,rsi-mean-reversion,macd-crossover"
        int         lots,                   // contracts/lots taken
        int         units,                  // = lots × lotSize
        BigDecimal  notional,               // = units × entryPrice (₹)
        Instant     exitAt,
        BigDecimal  exitPrice,
        String      exitReason,
        BigDecimal  returnPercent,
        BigDecimal  pnlPoints,
        BigDecimal  pnlRupees
) {}
