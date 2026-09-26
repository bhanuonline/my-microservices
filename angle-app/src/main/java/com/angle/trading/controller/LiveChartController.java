package com.angle.trading.controller;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.marketdata.MarketDataService;
import com.angle.trading.persistence.BiasInstrumentEntity;
import com.angle.trading.service.InstrumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Serves the live chart page and its historical-candle backing API.
 *
 *   GET /live/chart[?token=X&interval=FIVE_MINUTE]  → HTML chart page
 *   GET /api/live/candles?token=X&interval=Y&limit=300  → JSON candles for chart bootstrap
 *
 * The chart page is a client-side app: on load it fetches history via the
 * JSON endpoint, then subscribes to /api/live/stream (SSE, Phase 3) for
 * real-time updates. All caching (Caffeine + Redis + MySQL) is transparent —
 * the history call typically returns from L1/L3 in <10 ms.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class LiveChartController {

    private static final int DEFAULT_LIMIT = 300;
    private static final int MAX_LIMIT     = 2000;

    private final MarketDataService marketDataService;
    private final InstrumentService instrumentService;

    // ---------- HTML ----------

    @GetMapping("/live/chart")
    public String chart(
            @RequestParam(defaultValue = "99926000") String token,
            @RequestParam(defaultValue = "FIVE_MINUTE") String interval,
            Model model
    ) {
        List<BiasInstrumentEntity> instruments = instrumentService.listEnabled();
        BiasInstrumentEntity selected = instruments.stream()
                .filter(i -> token.equals(i.getSymbolToken()))
                .findFirst()
                .orElse(instruments.isEmpty() ? null : instruments.get(0));
        model.addAttribute("instruments", instruments);
        model.addAttribute("selected",    selected);
        model.addAttribute("selectedToken",    selected == null ? token    : selected.getSymbolToken());
        model.addAttribute("selectedSymbol",   selected == null ? "Unknown": selected.getSymbol());
        model.addAttribute("selectedExchange", selected == null ? "NSE"    : selected.getExchange());
        model.addAttribute("selectedInterval", interval);
        return "live/chart";
    }

    // ---------- JSON candles for the chart to bootstrap ----------

    /**
     * Return the last {@code limit} candles for the chart to render on load.
     *
     * Response shape (TradingView Lightweight Charts format):
     *   [
     *     { "time": 1737891360, "open": 24680.5, "high": 24682.25,
     *       "low": 24679.0, "close": 24681.5, "volume": 12345 },
     *     ...
     *   ]
     * time is seconds since epoch (UTC).
     */
    @GetMapping("/api/live/candles")
    @ResponseBody
    public List<Map<String, Object>> candles(
            @RequestParam String token,
            @RequestParam(defaultValue = "FIVE_MINUTE") Interval interval,
            @RequestParam(defaultValue = "300") int limit,
            @RequestParam(defaultValue = "ANGEL") String broker
    ) {
        int capped = Math.min(Math.max(limit, 10), MAX_LIMIT);

        // Look up the instrument to pick the right exchange (some are NSE, MCX, etc.)
        Exchange exch = instrumentService.listAll().stream()
                .filter(i -> token.equals(i.getSymbolToken()))
                .findFirst()
                .map(i -> {
                    try { return Exchange.valueOf(i.getExchange()); }
                    catch (Exception e) { return Exchange.NSE; }
                })
                .orElse(Exchange.NSE);

        // Fetch a window large enough to yield 'capped' candles. Use interval math for lookback.
        LocalDate to   = LocalDate.now();
        LocalDate from = to.minusDays(Math.max(2, calcLookbackDays(interval, capped)));

        List<Candle> all;
        try {
            all = marketDataService.getCandles(broker, exch, token, interval, from, to);
        } catch (Exception e) {
            log.warn("Chart candle fetch failed for {}:{}: {}", token, interval, e.getMessage());
            return List.of();
        }
        if (all == null || all.isEmpty()) return List.of();

        // Keep only the tail (latest N)
        int start = Math.max(0, all.size() - capped);
        List<Candle> tail = all.subList(start, all.size());

        List<Map<String, Object>> out = new ArrayList<>(tail.size());
        for (Candle c : tail) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("time",   c.timestamp().getEpochSecond());
            m.put("open",   c.open());
            m.put("high",   c.high());
            m.put("low",    c.low());
            m.put("close",  c.close());
            m.put("volume", c.volume());
            out.add(m);
        }
        return out;
    }

    /** Rough calendar-days lookback to guarantee ~N candles for the given interval. */
    private static int calcLookbackDays(Interval iv, int candlesWanted) {
        Duration barDur = switch (iv) {
            case ONE_MINUTE     -> Duration.ofMinutes(1);
            case FIVE_MINUTE    -> Duration.ofMinutes(5);
            case FIFTEEN_MINUTE -> Duration.ofMinutes(15);
            case THIRTY_MINUTE  -> Duration.ofMinutes(30);
            case ONE_HOUR       -> Duration.ofHours(1);
            case ONE_DAY        -> Duration.ofDays(1);
        };
        // 6.5 trading hours/day = 390 min. Round up + double for holidays.
        long tradingMinutesPerDay = 390;
        long minsWanted = barDur.toMinutes() * candlesWanted;
        int days = (int) Math.ceil((double) minsWanted / tradingMinutesPerDay) * 2;
        return Math.max(days, 5);
    }
}
