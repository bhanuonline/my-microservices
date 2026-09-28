package com.angle.trading.backtest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Aggregate stats + trade log + equity curve for a pipeline backtest run.
 *  Renamed with Pipeline prefix to avoid clashing with the older per-strategy
 *  {@code BacktestResult} record. */
public record PipelineBacktestResult(
        String      symbol,
        String      interval,
        LocalDate   fromDate,
        LocalDate   toDate,
        int         totalCandles,
        int         signalsFired,
        int         wins,
        int         losses,
        int         expired,
        int         trailed,
        double      winRate,
        double      avgReturn,
        double      totalReturnPct,
        double      maxDrawdownPct,
        double      finalCapital,
        double      profitFactor,
        double      expectancy,
        List<PipelineBacktestTrade> trades,
        List<EquityPoint>           equityCurve,
        String      warnings
) {
    public record EquityPoint(Instant at, double capital, double pctFromStart) {}
}
