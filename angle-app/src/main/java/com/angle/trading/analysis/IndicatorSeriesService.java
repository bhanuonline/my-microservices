package com.angle.trading.analysis;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.indicator.ExponentialMovingAverage;
import com.angle.trading.indicator.MACD;
import com.angle.trading.indicator.RelativeStrengthIndex;
import com.angle.trading.indicator.SuperTrend;
import com.angle.trading.indicator.VwapIndicator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes indicator time-series for the live chart page.
 *
 * Returns arrays aligned 1-to-1 with the candle list so the frontend can plot
 * each indicator as its own line series without re-computing anything.
 *
 * Output shape (per indicator = List of {time, value} maps):
 *   [
 *     { "time": 1737891360, "value": 24660.4 },
 *     { "time": 1737891420, "value": 24661.2 },
 *     ...
 *   ]
 *
 * Null indicator values (warmup period) are simply omitted — TradingView
 * handles gaps gracefully.
 */
@Slf4j
@Service
public class IndicatorSeriesService {

    /**
     * Compute every indicator the chart page uses. Grouped into "overlays"
     * (drawn on price scale — EMAs, VWAP, SuperTrend) and "oscillators"
     * (drawn in their own pane — RSI, MACD).
     *
     * Returned map keys: ema9, ema20, ema50, vwap, superTrend, rsi14,
     *                    macd, macdSignal, macdHistogram.
     */
    public Map<String, List<Map<String, Object>>> compute(List<Candle> candles) {
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        if (candles == null || candles.isEmpty()) return out;

        // Overlay lines (same scale as price)
        out.put("ema9",  toSeries(candles, new ExponentialMovingAverage(9).compute(candles)));
        out.put("ema20", toSeries(candles, new ExponentialMovingAverage(20).compute(candles)));
        out.put("ema50", toSeries(candles, new ExponentialMovingAverage(50).compute(candles)));
        out.put("vwap",  toSeries(candles, new VwapIndicator().compute(candles)));

        // SuperTrend returns Value(line, bullish) — flatten into just the line
        List<SuperTrend.Value> st = new SuperTrend(10, 3.0).compute(candles);
        List<Map<String, Object>> stLine = new ArrayList<>(st.size());
        for (int i = 0; i < candles.size(); i++) {
            SuperTrend.Value v = st.get(i);
            if (v == null || v.line() == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("time", candles.get(i).timestamp().getEpochSecond());
            m.put("value", v.line());
            m.put("bullish", v.bullish());   // frontend colors line green/red
            stLine.add(m);
        }
        out.put("superTrend", stLine);

        // Oscillators (own pane)
        out.put("rsi14", toSeries(candles, new RelativeStrengthIndex(14).compute(candles)));

        // MACD is 3 series
        List<MACD.MacdValue> macd = new MACD(12, 26, 9).computeSeries(candles);
        List<Map<String, Object>> macdLine = new ArrayList<>();
        List<Map<String, Object>> macdSignal = new ArrayList<>();
        List<Map<String, Object>> macdHist = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            MACD.MacdValue v = macd.get(i);
            if (v == null) continue;
            long t = candles.get(i).timestamp().getEpochSecond();
            if (v.macd() != null)      macdLine.add(Map.of("time", t, "value", v.macd()));
            if (v.signal() != null)    macdSignal.add(Map.of("time", t, "value", v.signal()));
            if (v.histogram() != null) macdHist.add(Map.of("time", t, "value", v.histogram(),
                    "color", v.histogram().signum() >= 0 ? "rgba(49,211,155,.6)" : "rgba(255,107,112,.6)"));
        }
        out.put("macd",          macdLine);
        out.put("macdSignal",    macdSignal);
        out.put("macdHistogram", macdHist);

        return out;
    }

    private static List<Map<String, Object>> toSeries(List<Candle> candles, List<BigDecimal> series) {
        List<Map<String, Object>> out = new ArrayList<>(candles.size());
        for (int i = 0; i < candles.size(); i++) {
            BigDecimal v = series.get(i);
            if (v == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("time",  candles.get(i).timestamp().getEpochSecond());
            m.put("value", v);
            out.add(m);
        }
        return out;
    }
}
