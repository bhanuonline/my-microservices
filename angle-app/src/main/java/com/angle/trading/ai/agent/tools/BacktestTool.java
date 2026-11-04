package com.angle.trading.ai.agent.tools;

import com.angle.trading.ai.agent.ResearchTool;
import com.angle.trading.backtest.PipelineBacktestRequest;
import com.angle.trading.backtest.PipelineBacktestResult;
import com.angle.trading.backtest.PipelineBacktestService;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.marketdata.InstrumentMasterService;
import com.angle.trading.marketdata.model.Instrument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tool: run_backtest
 * Runs the pipeline backtest (all strategies + consensus) on a symbol.
 * Returns win rate, profit factor, trade count — key stats for Claude to reason over.
 */
@Component
@RequiredArgsConstructor
public class BacktestTool implements ResearchTool {

    private final InstrumentMasterService  instruments;
    private final PipelineBacktestService  backtestService;

    @Override public String name() { return "run_backtest"; }

    @Override
    public String description() {
        return "Run a historical backtest on an Indian stock symbol using the consensus pipeline strategy. " +
               "Returns win rate, profit factor, and trade statistics to validate if the setup has edge.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("symbol",   Map.of("type", "string", "description", "NSE symbol e.g. HDFCBANK-EQ"));
        props.put("interval", Map.of("type", "string", "description", "ONE_HOUR|ONE_DAY|FIFTEEN_MINUTE", "default", "ONE_HOUR"));
        props.put("days",     Map.of("type", "integer", "description", "Backtest lookback in days", "default", 90));

        return Map.of(
                "type",       "object",
                "properties", props,
                "required",   new String[]{"symbol"}
        );
    }

    @Override
    public String execute(Map<String, Object> args) {
        String symbol      = (String) args.get("symbol");
        String intervalStr = args.getOrDefault("interval", "ONE_HOUR").toString();
        int    days        = args.containsKey("days") ? ((Number) args.get("days")).intValue() : 90;

        Instrument inst = instruments.findBySymbol(symbol).orElse(null);
        if (inst == null) return "Symbol not found: " + symbol;

        Interval interval;
        try { interval = Interval.valueOf(intervalStr); }
        catch (Exception e) { interval = Interval.ONE_HOUR; }

        LocalDate to   = LocalDate.now();
        LocalDate from = to.minusDays(days);

        PipelineBacktestRequest req = new PipelineBacktestRequest(
                inst.token(), symbol,
                Exchange.valueOf(inst.exchange().name()),
                interval, from, to,
                3,          // minAgreement
                "NONE",     // trailingMode
                0.4, 0.3,   // trailingPercent, trailingActivation
                48,         // expiryBars
                500000,     // capital
                1.0,        // riskPercent
                1,          // lotSize
                "RISK_BASED",
                1           // fixedLots
        );

        PipelineBacktestResult r = backtestService.run(req);

        if (r.signalsFired() == 0)
            return "Backtest for " + symbol + " over " + days + " days: no signals fired (strategy found no setups).";

        return String.format(
                "Backtest for %s (%s, %d days):\n" +
                "  Signals fired:   %d\n" +
                "  Wins:            %d\n" +
                "  Losses:          %d\n" +
                "  Win rate:        %.1f%%\n" +
                "  Profit factor:   %.2f\n" +
                "  Avg return/trade:%.2f%%\n" +
                "  Total return:    %.2f%%\n" +
                "  Max drawdown:    %.2f%%\n" +
                "  Expectancy ₹:    %.0f\n" +
                "  Warnings:        %s",
                symbol, intervalStr, days,
                r.signalsFired(), r.wins(), r.losses(),
                r.winRate(), r.profitFactor(),
                r.avgReturn(), r.totalReturnPct(), r.maxDrawdownPct(),
                r.expectancy(),
                r.warnings() != null ? r.warnings() : "none"
        );
    }
}
