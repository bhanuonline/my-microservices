package com.angle.trading.controller;

import com.angle.trading.analyst.AnalystService;
import com.angle.trading.analyst.model.AnalystReport;
import com.angle.trading.backtest.BacktestResult;
import com.angle.trading.backtest.Backtester;
import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.marketdata.MarketDataService;
import com.angle.trading.marketdata.NiftyFileLoader;
import com.angle.trading.marketstructure.FvgDetector;
import com.angle.trading.marketstructure.LiquidityDetector;
import com.angle.trading.marketstructure.MarketContextBuilder;
import com.angle.trading.marketstructure.OrderBlockDetector;
import com.angle.trading.marketstructure.StructureAnalyzer;
import com.angle.trading.marketstructure.model.MarketContext;
import com.angle.trading.strategy.Strategy;
import com.angle.trading.strategy.StrategyRegistry;
import com.angle.trading.strategy.impl.EnsembleStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints to trigger analysis.
 *
 *   GET /api/analysis/backtest[?strategy=name]
 *       Runs the given strategy (or the default) on nifty-data.txt.
 *       ?strategy=moving-average-crossover   (default from config)
 *       ?strategy=rsi-mean-reversion
 *
 *   GET /api/analysis/strategies
 *       Lists all available strategy names.
 *
 *   GET /api/analysis/candles?broker=ANGEL&exchange=NSE&symbolToken=99926000&from=...&to=...
 *       Equity or index (default exchange=NSE)
 *
 *   GET /api/analysis/candles?broker=ANGEL&exchange=NFO&symbolToken=<option-token>&from=...&to=...
 *       Options / futures (NFO = NSE F&O, BFO = BSE F&O, MCX = commodities)
 */
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final NiftyFileLoader niftyFileLoader;
    private final Backtester backtester;
    private final StrategyRegistry strategyRegistry;
    private final MarketDataService marketDataService;
    private final StructureAnalyzer structureAnalyzer;
    private final OrderBlockDetector orderBlockDetector;
    private final FvgDetector fvgDetector;
    private final LiquidityDetector liquidityDetector;
    private final MarketContextBuilder marketContextBuilder;
    private final AnalystService analystService;

    @GetMapping("/backtest")
    public BacktestResult backtest(
            @RequestParam(required = false) String strategy,
            @RequestParam(required = false) Integer minAgreement
    ) {
        Strategy chosen = (strategy == null || strategy.isBlank())
                ? strategyRegistry.getDefault()
                : strategyRegistry.get(strategy);

        // Ensemble-specific override — ?minAgreement=N tweaks the vote threshold
        // for this call only, no config edit / restart needed.
        if (minAgreement != null && chosen instanceof EnsembleStrategy ensemble) {
            chosen = ensemble.withMinAgreement(minAgreement);
        }

        List<Candle> candles = niftyFileLoader.load();
        return backtester.run(chosen, candles);
    }

    @GetMapping("/strategies")
    public Map<String, Object> strategies() {
        return Map.of(
                "default", strategyRegistry.getDefault().name(),
                "available", strategyRegistry.availableNames()
        );
    }

    /**
     * SMC Phase 1: detect swing points and BOS/CHoCH events on the local Nifty CSV.
     * Returns the swings and structural events; visual verification is left
     * to the caller (compare against a TradingView chart with the same lookback).
     */
    @GetMapping("/structure")
    public Map<String, Object> structure() {
        List<Candle> candles = niftyFileLoader.load();
        StructureAnalyzer.Result result = structureAnalyzer.analyze(candles);
        return Map.of(
                "candleCount", candles.size(),
                "swingCount", result.swings().size(),
                "eventCount", result.events().size(),
                "swings", result.swings(),
                "events", result.events()
        );
    }

    /**
     * ONE call that combines every strategy's view + SMC context into a
     * single recommendation.
     *
     * Two modes:
     *
     *   No broker/token params → uses the bundled Nifty daily CSV.
     *      GET /api/analysis/analyst
     *
     *   With broker + symbolToken → fetches live from that broker.
     *      GET /api/analysis/analyst
     *          ?broker=ANGEL
     *          &exchange=NSE
     *          &symbolToken=99926000
     *          &interval=ONE_DAY
     *          &from=2026-05-25   (optional, default to-90d)
     *          &to=2026-08-25     (optional, default today)
     */
    @GetMapping("/analyst")
    public AnalystReport analyst(
            @RequestParam(required = false) String broker,
            @RequestParam(required = false) Exchange exchange,
            @RequestParam(required = false) String symbolToken,
            @RequestParam(required = false) Interval interval,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        if (broker != null && symbolToken != null) {
            Exchange ex = exchange == null ? Exchange.NSE : exchange;
            Interval iv = interval == null ? Interval.ONE_DAY : interval;
            LocalDate toDate   = to   == null ? LocalDate.now()          : to;
            LocalDate fromDate = from == null ? toDate.minusDays(90)     : from;
            return analystService.analyseLive(broker, ex, symbolToken, iv, fromDate, toDate);
        }
        return analystService.analyseFromCsv();
    }

    /**
     * SMC Phase 3: combined market context at a specific candle.
     * Returns the trend bias, unmitigated zones, unswept liquidity, and recent
     * sweeps — everything a strategy needs to make a decision.
     *
     * asOfIndex omitted → snapshot at end of CSV (i.e. "right now")
     * asOfIndex=N       → snapshot as-of candle N (no future data visible)
     */
    @GetMapping("/context")
    public MarketContext context(
            @RequestParam(required = false) Integer asOfIndex
    ) {
        List<Candle> candles = niftyFileLoader.load();
        return asOfIndex == null
                ? marketContextBuilder.build(candles)
                : marketContextBuilder.buildAsOf(candles, asOfIndex);
    }

    /**
     * SMC Phase 2: order blocks, fair value gaps, and liquidity (levels + sweeps).
     * Each zone is marked mitigated/swept if price has already interacted with it.
     */
    @GetMapping("/zones")
    public Map<String, Object> zones() {
        List<Candle> candles = niftyFileLoader.load();
        StructureAnalyzer.Result structure = structureAnalyzer.analyze(candles);
        var orderBlocks = orderBlockDetector.detect(candles, structure.events());
        var fvgs = fvgDetector.detect(candles);
        var liquidity = liquidityDetector.detect(candles, structure.swings());

        return Map.of(
                "candleCount", candles.size(),
                "orderBlockCount", orderBlocks.size(),
                "fvgCount", fvgs.size(),
                "liquidityLevelCount", liquidity.levels().size(),
                "liquiditySweepCount", liquidity.sweeps().size(),
                "orderBlocks", orderBlocks,
                "fvgs", fvgs,
                "liquidityLevels", liquidity.levels(),
                "liquiditySweeps", liquidity.sweeps()
        );
    }

    @GetMapping("/candles")
    public Map<String, Object> candles(
            @RequestParam String broker,
            @RequestParam(defaultValue = "NSE") Exchange exchange,
            @RequestParam String symbolToken,
            @RequestParam(defaultValue = "ONE_DAY") Interval interval,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        List<Candle> candles = marketDataService.getCandles(broker, exchange, symbolToken, interval, from, to);
        return Map.of(
                "broker", broker,
                "exchange", exchange,
                "symbolToken", symbolToken,
                "interval", interval,
                "count", candles.size(),
                "candles", candles
        );
    }
}
