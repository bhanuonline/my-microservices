package com.angle.trading.ai.agent.tools;

import com.angle.trading.ai.agent.ResearchTool;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.controller.StockQuoteController;
import com.angle.trading.dto.IndicatorsDto;
import com.angle.trading.marketdata.InstrumentMasterService;
import com.angle.trading.marketdata.model.Instrument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tool: get_indicators
 * Fetches RSI, MACD, EMA, ADX, ATR for a symbol.
 * Delegates to StockQuoteController.computeIndicators() — reuses L1→L2→L3 cache.
 */
@Component
@RequiredArgsConstructor
public class IndicatorsTool implements ResearchTool {

    private final InstrumentMasterService instruments;
    private final StockQuoteController    quoteController;

    @Override public String name() { return "get_indicators"; }

    @Override
    public String description() {
        return "Get technical indicators (RSI, MACD, EMA9/20/50/200, ADX, ATR, SMA20) for an Indian stock symbol. " +
               "Use this first to understand momentum and trend.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("symbol",   Map.of("type", "string", "description", "NSE symbol e.g. HDFCBANK-EQ"));
        props.put("interval", Map.of("type", "string", "description", "ONE_MINUTE|FIVE_MINUTE|FIFTEEN_MINUTE|ONE_HOUR|ONE_DAY", "default", "ONE_DAY"));
        props.put("days",     Map.of("type", "integer", "description", "Lookback days for candle history", "default", 300));

        return Map.of(
                "type",       "object",
                "properties", props,
                "required",   new String[]{"symbol"}
        );
    }

    @Override
    public String execute(Map<String, Object> args) {
        String symbol   = (String) args.get("symbol");
        String intervalStr = args.getOrDefault("interval", "ONE_DAY").toString();
        int    days     = args.containsKey("days") ? ((Number) args.get("days")).intValue() : 300;

        Instrument inst = instruments.findBySymbol(symbol).orElse(null);
        if (inst == null) return "Symbol not found: " + symbol;

        Interval interval;
        try { interval = Interval.valueOf(intervalStr); }
        catch (Exception e) { interval = Interval.ONE_DAY; }

        IndicatorsDto dto = quoteController.computeIndicators(inst, interval, days, null);
        if (dto == null) return "Could not compute indicators for " + symbol + " — Angel API may be rate-limited.";

        return String.format(
                "Indicators for %s (%s, %d candles):\n" +
                "  RSI(14):       %s\n" +
                "  ADX(14):       %s\n" +
                "  ATR(14):       %s\n" +
                "  EMA9:          %s\n" +
                "  EMA20:         %s\n" +
                "  EMA50:         %s\n" +
                "  EMA200:        %s\n" +
                "  SMA20:         %s\n" +
                "  MACD Line:     %s\n" +
                "  MACD Signal:   %s\n" +
                "  MACD Histogram:%s",
                symbol, intervalStr, dto.candleCount(),
                dto.rsi14(), dto.adx14(), dto.atr14(),
                dto.ema9(), dto.ema20(), dto.ema50(), dto.ema200(), dto.sma20(),
                dto.macdLine(), dto.macdSignal(), dto.macdHistogram()
        );
    }
}
