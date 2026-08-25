package com.angle.trading.controller;

import com.angle.trading.backtest.BacktestResult;
import com.angle.trading.backtest.Backtester;
import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.marketdata.MarketDataService;
import com.angle.trading.marketdata.NiftyFileLoader;
import com.angle.trading.strategy.Strategy;
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
 *   GET /api/analysis/backtest
 *       Run default strategy on nifty-data.txt
 *
 *   GET /api/analysis/candles?broker=ANGEL&exchange=NSE&symbolToken=99926000&from=...&to=...
 *       Equity or index (default exchange=NSE)
 *
 *   GET /api/analysis/candles?broker=ANGEL&exchange=NFO&symbolToken=<option-token>&from=...&to=...
 *       Options / futures (exchange=NFO for NSE F&O, BFO for BSE F&O, MCX for commodities)
 */
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final NiftyFileLoader niftyFileLoader;
    private final Backtester backtester;
    private final Strategy defaultStrategy;
    private final MarketDataService marketDataService;

    @GetMapping("/backtest")
    public BacktestResult backtest() {
        List<Candle> candles = niftyFileLoader.load();
        return backtester.run(defaultStrategy, candles);
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
