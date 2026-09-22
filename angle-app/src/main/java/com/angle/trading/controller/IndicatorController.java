package com.angle.trading.controller;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.indicator.AverageDirectionalIndex;
import com.angle.trading.indicator.AverageTrueRange;
import com.angle.trading.indicator.ExponentialMovingAverage;
import com.angle.trading.indicator.MACD;
import com.angle.trading.indicator.RelativeStrengthIndex;
import com.angle.trading.indicator.SimpleMovingAverage;
import com.angle.trading.indicator.VwapIndicator;
import com.angle.trading.marketdata.MarketDataService;
import com.angle.trading.marketdata.NiftyFileLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compute technical indicators for any candle series.
 *
 *   GET /api/indicators
 *       broker=ANGEL, exchange=NSE, symbolToken=99926000,
 *       interval=FIVE_MINUTE, from=YYYY-MM-DD, to=YYYY-MM-DD
 *   → returns latest values for ALL indicators.
 *
 *   GET /api/indicators/csv (no broker required — uses bundled Nifty CSV)
 *
 * Response shape (latest value per indicator, plus last few for context):
 *   {
 *     "candleCount": 100,
 *     "latest": {
 *       "close":  24680.75,
 *       "ema20":  24610.25,
 *       "ema50":  24540.10,
 *       "ema200": 24310.50,
 *       "sma20":  24600.30,
 *       "vwap":   24645.80,
 *       "rsi14":  62.5,
 *       "adx14":  28.3,
 *       "atr14":  105.2,
 *       "macdLine": 25.4,
 *       "macdSignal": 20.1,
 *       "macdHistogram": 5.3
 *     }
 *   }
 */
@RestController
@RequestMapping("/api/indicators")
@RequiredArgsConstructor
public class IndicatorController {

    private final MarketDataService marketDataService;
    private final NiftyFileLoader niftyFileLoader;

    @GetMapping
    public Map<String, Object> forInstrument(
            @RequestParam String broker,
            @RequestParam(defaultValue = "NSE") Exchange exchange,
            @RequestParam String symbolToken,
            @RequestParam(defaultValue = "FIVE_MINUTE") Interval interval,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        List<Candle> candles = marketDataService.getCandles(broker, exchange, symbolToken, interval, from, to);
        return buildResponse(candles);
    }

    @GetMapping("/csv")
    public Map<String, Object> forBundledCsv() {
        return buildResponse(niftyFileLoader.load());
    }

    private static Map<String, Object> buildResponse(List<Candle> candles) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("candleCount", candles.size());
        if (candles.isEmpty()) {
            out.put("latest", Map.of());
            return out;
        }

        int last = candles.size() - 1;
        List<BigDecimal> ema20  = new ExponentialMovingAverage(20).compute(candles);
        List<BigDecimal> ema50  = new ExponentialMovingAverage(50).compute(candles);
        List<BigDecimal> ema200 = new ExponentialMovingAverage(200).compute(candles);
        List<BigDecimal> sma20  = new SimpleMovingAverage(20).compute(candles);
        List<BigDecimal> vwap   = new VwapIndicator().compute(candles);
        List<BigDecimal> rsi    = new RelativeStrengthIndex(14).compute(candles);
        List<BigDecimal> adx    = new AverageDirectionalIndex(14).compute(candles);
        List<BigDecimal> atr    = new AverageTrueRange(14).compute(candles);
        List<MACD.MacdValue> macd = new MACD(12, 26, 9).computeSeries(candles);

        Map<String, Object> latest = new LinkedHashMap<>();
        latest.put("timestamp",     candles.get(last).timestamp());
        latest.put("close",         candles.get(last).close());
        latest.put("ema20",         ema20.get(last));
        latest.put("ema50",         ema50.get(last));
        latest.put("ema200",        ema200.get(last));
        latest.put("sma20",         sma20.get(last));
        latest.put("vwap",          vwap.get(last));
        latest.put("rsi14",         rsi.get(last));
        latest.put("adx14",         adx.get(last));
        latest.put("atr14",         atr.get(last));
        latest.put("macdLine",      macd.get(last).macd());
        latest.put("macdSignal",    macd.get(last).signal());
        latest.put("macdHistogram", macd.get(last).histogram());

        out.put("latest", latest);
        return out;
    }
}
