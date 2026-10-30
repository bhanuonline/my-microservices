package com.angle.trading.controller;

import com.angle.trading.broker.angel.AngelLtpClient;
import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.analysis.SmcAnalysisService;
import com.angle.trading.cache.IndicatorCacheStore;
import com.angle.trading.config.IndicatorCacheProperties;
import com.angle.trading.dto.BollingerBandsDto;
import com.angle.trading.dto.CandleDataDto;
import com.angle.trading.dto.MarketStructureDto;
import com.angle.trading.dto.SmartMoneyDto;
import com.angle.trading.dto.MacdDto;
import com.angle.trading.dto.MomentumDto;
import com.angle.trading.dto.MovingAveragesDto;
import com.angle.trading.dto.SupertrendDto;
import com.angle.trading.dto.TrendDto;
import com.angle.trading.dto.VolatilityDto;
import com.angle.trading.indicator.CandleAggregatorUtil;
import com.angle.trading.indicator.IndicatorSignals;
import com.angle.trading.dto.IndicatorSnapshotDto;
import com.angle.trading.dto.IndicatorsDto;
import com.angle.trading.dto.PivotPointsDto;
import com.angle.trading.dto.MtfConclusionDto;
import com.angle.trading.dto.MtfResponseDto;
import com.angle.trading.dto.OrderBlockZoneDto;
import com.angle.trading.dto.StockQuoteDto;
import com.angle.trading.dto.TradeLevelDto;
import com.angle.trading.dto.TradeSuggestionDto;
import com.angle.trading.dto.TradeZoneDto;
import com.angle.trading.indicator.AverageDirectionalIndex;
import com.angle.trading.indicator.AverageTrueRange;
import com.angle.trading.indicator.BollingerBands;
import com.angle.trading.indicator.ExponentialMovingAverage;
import com.angle.trading.indicator.MACD;
import com.angle.trading.indicator.RelativeStrengthIndex;
import com.angle.trading.indicator.SimpleMovingAverage;
import com.angle.trading.indicator.SuperTrend;
import com.angle.trading.indicator.VwapIndicator;
import com.angle.trading.marketdata.InstrumentMasterService;
import com.angle.trading.marketdata.MarketDataService;
import com.angle.trading.marketdata.model.Instrument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockQuoteController {

    private final InstrumentMasterService instruments;
    private final AngelLtpClient ltpClient;
    private final MarketDataService marketDataService;
    private final IndicatorCacheStore indicatorCache;
    private final IndicatorCacheProperties cacheProps;
    private final SmcAnalysisService smcAnalysis;

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter IST_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(IST);

    // -------------------------------------------------------------------------
    // GET /api/stocks/indicators/day
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // MTF hierarchy — ordered lowest → highest
    // -------------------------------------------------------------------------
    private static final Map<Interval, List<Interval>> MTF_HIERARCHY = Map.of(
            Interval.FIFTEEN_MINUTE, List.of(Interval.FIFTEEN_MINUTE),
            Interval.THIRTY_MINUTE,  List.of(Interval.FIFTEEN_MINUTE, Interval.THIRTY_MINUTE),
            Interval.ONE_HOUR,       List.of(Interval.FIFTEEN_MINUTE, Interval.THIRTY_MINUTE, Interval.ONE_HOUR),
            Interval.TWO_HOUR,       List.of(Interval.FIFTEEN_MINUTE, Interval.THIRTY_MINUTE, Interval.ONE_HOUR, Interval.TWO_HOUR),
            Interval.THREE_HOUR,     List.of(Interval.FIFTEEN_MINUTE, Interval.THIRTY_MINUTE, Interval.ONE_HOUR, Interval.TWO_HOUR, Interval.THREE_HOUR),
            Interval.FOUR_HOUR,      List.of(Interval.FIFTEEN_MINUTE, Interval.THIRTY_MINUTE, Interval.ONE_HOUR, Interval.TWO_HOUR, Interval.THREE_HOUR, Interval.FOUR_HOUR),
            Interval.ONE_DAY,        List.of(Interval.FIFTEEN_MINUTE, Interval.THIRTY_MINUTE, Interval.ONE_HOUR, Interval.TWO_HOUR, Interval.THREE_HOUR, Interval.FOUR_HOUR, Interval.ONE_DAY)
    );

    /**
     * GET /api/stocks/indicators/mtf?symbol=HDFCBANK-EQ&interval=ONE_DAY&days=60
     *
     * Returns indicator values for every timeframe from 15m up to the requested interval.
     * Includes a cross-TF conclusion: primaryBias, confidence, tradeSetup.
     */
    @GetMapping("/indicators/mtf")
    public ResponseEntity<MtfResponseDto> multiTimeframe(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "ONE_HOUR") Interval interval,
            @RequestParam(defaultValue = "60") int days,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf
    ) {
        Instrument inst = instruments.findBySymbol(symbol).orElse(null);
        if (inst == null) return ResponseEntity.notFound().build();

        List<Interval> tfs = MTF_HIERARCHY.getOrDefault(interval, List.of(interval));
        LocalDate to = asOf != null ? asOf : LocalDate.now();

        // Pre-fetch 1h candles once — reused to synthesize 2h/3h/4h (Angel doesn't support them natively)
        List<Candle> oneHourCandles = null;
        boolean needsSynthetic = tfs.stream().anyMatch(tf ->
                tf == Interval.TWO_HOUR || tf == Interval.THREE_HOUR || tf == Interval.FOUR_HOUR);
        if (needsSynthetic) {
            try {
                oneHourCandles = marketDataService.getCandles(
                        "ANGEL", inst.exchange(), inst.token(),
                        Interval.ONE_HOUR, to.minusDays(days), to);
            } catch (Exception e) {
                log.warn("MTF: could not fetch 1h candles for synthetic aggregation: {}", e.getMessage());
            }
        }

        // Compute indicators for each timeframe
        LinkedHashMap<String, IndicatorsDto> timeframes = new LinkedHashMap<>();
        for (Interval tf : tfs) {
            String key = cacheKey("indicators", symbol, tf, days, to);
            IndicatorsDto cached = indicatorCache.<IndicatorsDto>get(key).orElse(null);
            if (cached != null) {
                timeframes.put(tf.name(), cached);
                continue;
            }

            IndicatorsDto ind;
            if ((tf == Interval.TWO_HOUR || tf == Interval.THREE_HOUR || tf == Interval.FOUR_HOUR)
                    && oneHourCandles != null && !oneHourCandles.isEmpty()) {
                // Synthesize from 1h candles
                int multiplier = tf == Interval.TWO_HOUR ? 2 : tf == Interval.THREE_HOUR ? 3 : 4;
                List<Candle> synthetic = CandleAggregatorUtil.aggregate(oneHourCandles, multiplier);
                ind = computeIndicatorsFromCandles(inst, synthetic, tf, to);
            } else {
                ind = computeIndicators(inst, tf, days, asOf);
            }

            if (ind != null) {
                indicatorCache.put(key, ind, ttlFor(to));
                timeframes.put(tf.name(), ind);
            }
        }

        if (timeframes.isEmpty()) return ResponseEntity.internalServerError().build();

        MtfConclusionDto conclusion = buildMtfConclusion(timeframes, tfs);

        // Highest TF indicators — used for stop/target/entry zone (has marketStructure + smartMoney)
        String highestKey = tfs.get(tfs.size() - 1).name();
        IndicatorsDto highestTfInd = timeframes.get(highestKey);

        return ResponseEntity.ok(new MtfResponseDto(
                symbol,
                interval.name(),
                timeframes,
                conclusion,
                buildTradeSuggestion(conclusion, highestTfInd)
        ));
    }

    private MtfConclusionDto buildMtfConclusion(LinkedHashMap<String, IndicatorsDto> timeframes,
                                                  List<Interval> tfs) {
        List<String> tfNames = tfs.stream().map(Interval::name).toList();
        int n = tfNames.size();

        // Higher TFs = top half of included list (these define the structural bias)
        // Lower TFs  = bottom half (intraday / entry timing)
        int split = Math.max(1, n / 2);
        List<String> higherTfs = tfNames.subList(split, n);
        List<String> lowerTfs  = tfNames.subList(0, split);

        // Primary bias from the highest timeframe
        String primaryBias = trend(timeframes.get(tfNames.get(n - 1)));

        // Which TFs align / conflict with primary bias
        List<String> aligned    = new ArrayList<>();
        List<String> conflicting = new ArrayList<>();
        for (String tf : tfNames) {
            String t = trend(timeframes.get(tf));
            if (primaryBias != null && primaryBias.equals(t)) aligned.add(tf);
            else if (t != null)                                conflicting.add(tf);
        }

        // Confidence — based on how many HIGHER TFs agree
        long higherAligned = higherTfs.stream()
                .filter(tf -> primaryBias != null && primaryBias.equals(trend(timeframes.get(tf))))
                .count();
        String confidence;
        if      (higherAligned == higherTfs.size())              confidence = "STRONG";
        else if (higherAligned >= Math.ceil(higherTfs.size() / 2.0)) confidence = "MODERATE";
        else                                                      confidence = "WEAK";

        // Trade setup
        String tradeSetup;
        if (primaryBias == null) {
            tradeSetup = "NO_CLEAR_TREND";
        } else {
            long lowerAligned   = lowerTfs.stream().filter(tf -> primaryBias.equals(trend(timeframes.get(tf)))).count();
            long lowerConflict  = lowerTfs.stream().filter(tf -> { String t = trend(timeframes.get(tf)); return t != null && !primaryBias.equals(t); }).count();
            if (lowerAligned == lowerTfs.size())       tradeSetup = "TREND_CONTINUATION";
            else if (lowerConflict == lowerTfs.size()) tradeSetup = "WAIT_FOR_PULLBACK_END";
            else                                       tradeSetup = "MIXED_SIGNALS";
        }

        return new MtfConclusionDto(primaryBias, aligned, conflicting, confidence, tradeSetup);
    }

    private static String trend(IndicatorsDto dto) {
        if (dto == null || dto.signals() == null) return null;
        return dto.signals().trend();
    }

    private TradeSuggestionDto buildTradeSuggestion(MtfConclusionDto conclusion,
                                                     IndicatorsDto highestTfInd) {
        String bias  = conclusion.primaryBias();
        String setup = conclusion.tradeSetup();

        if (bias == null || "NO_CLEAR_TREND".equals(setup) || "MIXED_SIGNALS".equals(setup)) {
            return new TradeSuggestionDto("NO_TRADE", "NO_TRADE", null, null, null, null, null,
                    "No clear trend across timeframes — stay out.");
        }

        boolean isShort = "BEARISH".equals(bias);
        String direction = isShort ? "SHORT" : "LONG";
        String timing    = "TREND_CONTINUATION".equals(setup) ? "NOW" : "WAIT";

        String waitFor = null;
        if ("WAIT".equals(timing) && !conclusion.conflictingTimeframes().isEmpty()) {
            waitFor = "Wait for " + String.join(" + ", conclusion.conflictingTimeframes())
                    + " SuperTrend to turn " + bias + " (pullback ending)";
        }

        var sm  = highestTfInd != null ? highestTfInd.smartMoney()       : null;
        var ms  = highestTfInd != null ? highestTfInd.marketStructure()   : null;
        var vol = highestTfInd != null ? highestTfInd.volatility()        : null;
        BigDecimal atr = vol != null ? vol.atr14() : null;

        // Entry zone — from Order Block
        TradeZoneDto  entryZone = null;
        BigDecimal    entryMid  = null;
        OrderBlockZoneDto ob = isShort
                ? (sm != null ? sm.nearestBearishOB() : null)
                : (sm != null ? sm.nearestBullishOB() : null);
        if (ob != null) {
            entryZone = new TradeZoneDto(ob.bottom(), ob.top(),
                    isShort ? "BEARISH_ORDER_BLOCK" : "BULLISH_ORDER_BLOCK");
            entryMid  = ob.bottom().add(ob.top())
                    .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        }

        // Stop loss — beyond nearest swing, plus ATR buffer
        TradeLevelDto stopLoss = null;
        BigDecimal buffer = atr != null ? atr.multiply(BigDecimal.valueOf(0.3))
                                              .setScale(2, RoundingMode.HALF_UP)
                                        : BigDecimal.ZERO;
        if (isShort && ms != null && ms.nearestSwingHigh() != null) {
            stopLoss = new TradeLevelDto(
                    ms.nearestSwingHigh().add(buffer).setScale(2, RoundingMode.HALF_UP),
                    "ABOVE_NEAREST_SWING_HIGH", null);
        } else if (!isShort && ms != null && ms.nearestSwingLow() != null) {
            stopLoss = new TradeLevelDto(
                    ms.nearestSwingLow().subtract(buffer).setScale(2, RoundingMode.HALF_UP),
                    "BELOW_NEAREST_SWING_LOW", null);
        }

        // Target 1 — nearest swing in trade direction
        TradeLevelDto target1 = null;
        if (isShort && ms != null && ms.nearestSwingLow() != null) {
            target1 = new TradeLevelDto(ms.nearestSwingLow(), "NEAREST_SWING_LOW",
                    rr(entryMid, stopLoss, ms.nearestSwingLow(), true));
        } else if (!isShort && ms != null && ms.nearestSwingHigh() != null) {
            target1 = new TradeLevelDto(ms.nearestSwingHigh(), "NEAREST_SWING_HIGH",
                    rr(entryMid, stopLoss, ms.nearestSwingHigh(), false));
        }

        // Target 2 — liquidity level beyond target 1
        TradeLevelDto target2 = null;
        if (isShort && sm != null && sm.sellSideLiquidityBelow() != null) {
            target2 = new TradeLevelDto(sm.sellSideLiquidityBelow(), "SELL_SIDE_LIQUIDITY",
                    rr(entryMid, stopLoss, sm.sellSideLiquidityBelow(), true));
        } else if (!isShort && sm != null && sm.buySideLiquidityAbove() != null) {
            target2 = new TradeLevelDto(sm.buySideLiquidityAbove(), "BUY_SIDE_LIQUIDITY",
                    rr(entryMid, stopLoss, sm.buySideLiquidityAbove(), false));
        }

        String note = direction + " setup ("  + conclusion.confidence() + " confidence). "
                + ("NOW".equals(timing)
                        ? "All timeframes aligned — enter now."
                        : "Higher TFs " + bias + " but lower TFs in pullback. " + waitFor + ".")
                + (stopLoss != null ? " Stop: ₹" + stopLoss.price() + "." : "")
                + (target1  != null ? " Target 1: ₹" + target1.price() + " (" + target1.riskReward() + ")." : "");

        return new TradeSuggestionDto(direction, timing, waitFor,
                entryZone, stopLoss, target1, target2, note);
    }

    private static String rr(BigDecimal entry, TradeLevelDto stop,
                               BigDecimal target, boolean isShort) {
        if (entry == null || stop == null || target == null) return null;
        BigDecimal risk   = stop.price().subtract(entry).abs();
        if (risk.compareTo(BigDecimal.ZERO) == 0) return null;
        BigDecimal reward = entry.subtract(target).abs();
        return "1:" + reward.divide(risk, 1, RoundingMode.HALF_UP);
    }

    @GetMapping("/indicators/day")
    public ResponseEntity<List<IndicatorSnapshotDto>> indicatorsForDay(
            @RequestParam String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "ONE_HOUR") Interval interval,
            @RequestParam(defaultValue = "300") int days
    ) {
        Instrument inst = instruments.findBySymbol(symbol).orElse(null);
        if (inst == null) return ResponseEntity.notFound().build();

        String key = cacheKey("day", symbol, interval, days, date);

        List<IndicatorSnapshotDto> cached = indicatorCache.<List<IndicatorSnapshotDto>>get(key).orElse(null);
        if (cached != null) {
            log.debug("Indicator day cache HIT: {}", key);
            return ResponseEntity.ok(cached);
        }

        List<IndicatorSnapshotDto> result = computeDayBreakdown(inst, date, interval, days);
        if (result == null) return ResponseEntity.internalServerError().build();
        indicatorCache.put(key, result, ttlFor(date));
        return ResponseEntity.ok(result);
    }

    // -------------------------------------------------------------------------
    // GET /api/stocks/indicators
    // -------------------------------------------------------------------------

    @GetMapping("/indicators")
    public ResponseEntity<IndicatorsDto> indicators(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "ONE_DAY") Interval interval,
            @RequestParam(defaultValue = "300") int days,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf
    ) {
        Instrument inst = instruments.findBySymbol(symbol).orElse(null);
        if (inst == null) return ResponseEntity.notFound().build();

        LocalDate to = asOf != null ? asOf : LocalDate.now();
        String key = cacheKey("indicators", symbol, interval, days, to);

        IndicatorsDto cached = indicatorCache.<IndicatorsDto>get(key).orElse(null);
        if (cached != null) {
            log.debug("Indicator cache HIT: {}", key);
            return ResponseEntity.ok(cached);
        }

        IndicatorsDto result = computeIndicators(inst, interval, days, asOf);
        if (result == null) return ResponseEntity.internalServerError().build();
        indicatorCache.put(key, result, ttlFor(to));
        return ResponseEntity.ok(result);
    }

    // -------------------------------------------------------------------------
    // GET /api/stocks/quote
    // -------------------------------------------------------------------------

    @GetMapping("/quote")
    public ResponseEntity<StockQuoteDto> quote(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "ONE_DAY") Interval interval,
            @RequestParam(defaultValue = "300") int days,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf
    ) {
        Instrument inst = instruments.findBySymbol(symbol).orElse(null);
        if (inst == null) return ResponseEntity.notFound().build();

        AngelLtpClient.QuoteData q = ltpClient.fetchQuote(inst.exchSeg(), symbol, inst.token()).orElse(null);

        BigDecimal ltp       = q != null ? q.ltp()          : null;
        BigDecimal prevClose = q != null ? q.previousClose() : null;
        BigDecimal change    = (ltp != null && prevClose != null) ? ltp.subtract(prevClose) : null;
        BigDecimal changePct = (change != null && prevClose != null && prevClose.compareTo(BigDecimal.ZERO) != 0)
                ? change.divide(prevClose, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : null;

        LocalDate to = asOf != null ? asOf : LocalDate.now();
        String key   = cacheKey("indicators", symbol, interval, days, to);

        IndicatorsDto indCache = indicatorCache.<IndicatorsDto>get(key).orElse(null);
        IndicatorsDto ind = indCache != null ? indCache : computeIndicators(inst, interval, days, asOf);
        if (ind != null && indCache == null) indicatorCache.put(key, ind, ttlFor(to));

        return ResponseEntity.ok(new StockQuoteDto(
                inst.token(), inst.symbol(), inst.name(), inst.exchSeg(), inst.instrumentType(),
                ltp,
                q != null ? q.open() : null,
                q != null ? q.high() : null,
                q != null ? q.low()  : null,
                prevClose, change, changePct,
                ind
        ));
    }

    // -------------------------------------------------------------------------
    // Internal helpers — also called directly by IndicatorCacheAdminController
    // -------------------------------------------------------------------------

    /** Compute indicators from a pre-fetched candle list (used for synthetic 2h/3h/4h). */
    private IndicatorsDto computeIndicatorsFromCandles(Instrument inst, List<Candle> candles,
                                                        Interval interval, LocalDate to) {
        if (candles == null || candles.isEmpty()) return null;
        try {
            return buildIndicatorsDto(candles, inst, interval, to);
        } catch (Exception e) {
            log.warn("Indicator computation failed for {} (synthetic {}): {}", inst.symbol(), interval, e.getMessage());
            return null;
        }
    }

    IndicatorsDto computeIndicators(Instrument inst, Interval interval, int days, LocalDate asOf) {
        try {
            LocalDate to   = asOf != null ? asOf : LocalDate.now();
            LocalDate from = to.minusDays(days);
            List<Candle> candles = marketDataService.getCandles(
                    "ANGEL", inst.exchange(), inst.token(), interval, from, to);
            if (candles.isEmpty()) return null;
            return buildIndicatorsDto(candles, inst, interval, to);
        } catch (Exception e) {
            log.warn("Indicator computation failed for {}: {}", inst.symbol(), e.getMessage());
            return null;
        }
    }

    private IndicatorsDto buildIndicatorsDto(List<Candle> candles, Instrument inst,
                                              Interval interval, LocalDate to) {
        int last = candles.size() - 1;
        List<BigDecimal>          rsi  = new RelativeStrengthIndex(14).compute(candles);
        List<BigDecimal>          adx  = new AverageDirectionalIndex(14).compute(candles);
        List<BigDecimal>          atr  = new AverageTrueRange(14).compute(candles);
        List<BigDecimal>         ema9  = new ExponentialMovingAverage(9).compute(candles);
        List<BigDecimal>         ema20 = new ExponentialMovingAverage(20).compute(candles);
        List<BigDecimal>         ema50 = new ExponentialMovingAverage(50).compute(candles);
        List<BigDecimal>        ema200 = new ExponentialMovingAverage(200).compute(candles);
        List<BigDecimal>         sma20 = new SimpleMovingAverage(20).compute(candles);
        List<MACD.MacdValue>      macd = new MACD(12, 26, 9).computeSeries(candles);
        List<BigDecimal>          vwap = new VwapIndicator().compute(candles);
        List<SuperTrend.Value>      st = new SuperTrend(10, 3.0).compute(candles);
        List<BollingerBands.Value>  bb = new BollingerBands(20, 2.0).compute(candles);

        MACD.MacdValue mv = macd.get(last);
        SuperTrend.Value sv = st.get(last);
        BollingerBands.Value bv = bb.get(last);

        BigDecimal close = candles.get(last).close();
        SupertrendDto stDto = new SupertrendDto(sv != null ? sv.line() : null,
                                                sv != null ? sv.bullish() : null);
        BollingerBandsDto bbDto = new BollingerBandsDto(bv != null ? bv.upper() : null,
                                                         bv != null ? bv.middle() : null,
                                                         bv != null ? bv.lower() : null);
        MovingAveragesDto maDto = new MovingAveragesDto(ema9.get(last), ema20.get(last),
                ema50.get(last), ema200.get(last), sma20.get(last), vwap.get(last));

        SmcAnalysisService.Result smc = smcAnalysis.analyze(candles, close);
        PivotPointsDto pivots = computePivotPoints(candles, to);

        return new IndicatorsDto(
                new MomentumDto(rsi.get(last),
                        new MacdDto(mv != null ? mv.macd() : null,
                                    mv != null ? mv.signal() : null,
                                    mv != null ? mv.histogram() : null)),
                new TrendDto(adx.get(last), null, null, stDto),
                maDto,
                new VolatilityDto(atr.get(last), bbDto),
                IndicatorSignals.buildSignals(close, rsi.get(last), adx.get(last),
                        mv != null ? mv.histogram() : null, stDto, bbDto, vwap.get(last)),
                IndicatorSignals.buildCrossovers(close, maDto),
                smc.marketStructure(),
                smc.smartMoney(),
                pivots,
                interval.name(), candles.size(), null
        );
    }

    List<IndicatorSnapshotDto> computeDayBreakdown(Instrument inst, LocalDate date, Interval interval, int days) {
        try {
            List<Candle> candles = marketDataService.getCandles(
                    "ANGEL", inst.exchange(), inst.token(), interval, date.minusDays(days), date);
            if (candles.isEmpty()) return List.of();

            // Compute indicators on full history (warmup needed for RSI, ADX, EMA, etc.)
            List<BigDecimal>         rsi  = new RelativeStrengthIndex(14).compute(candles);
            List<BigDecimal>         adx  = new AverageDirectionalIndex(14).compute(candles);
            List<BigDecimal>         atr  = new AverageTrueRange(14).compute(candles);
            List<BigDecimal>        ema9  = new ExponentialMovingAverage(9).compute(candles);
            List<BigDecimal>        ema20 = new ExponentialMovingAverage(20).compute(candles);
            List<BigDecimal>        ema50 = new ExponentialMovingAverage(50).compute(candles);
            List<BigDecimal>       ema200 = new ExponentialMovingAverage(200).compute(candles);
            List<BigDecimal>        sma20 = new SimpleMovingAverage(20).compute(candles);
            List<MACD.MacdValue>     macd = new MACD(12, 26, 9).computeSeries(candles);
            List<SuperTrend.Value>     st = new SuperTrend(10, 3.0).compute(candles);
            List<BollingerBands.Value> bb = new BollingerBands(20, 2.0).compute(candles);

            // VWAP is session-based — compute only on the target day's candles so it
            // resets at market open (9:15 AM) rather than accumulating across 60 days.
            List<Candle> dayCandles = candles.stream()
                    .filter(c -> c.timestamp().atZone(IST).toLocalDate().equals(date))
                    .toList();
            List<BigDecimal> vwapDay = new VwapIndicator().compute(dayCandles);

            // SMC analysis + Pivot Points — computed once, same for all candles on the day
            SmcAnalysisService.Result smc = smcAnalysis.analyze(candles);
            PivotPointsDto pivots = computePivotPoints(candles, date);

            // Build result — one row per candle on `date`
            List<IndicatorSnapshotDto> result = new ArrayList<>();
            int vwapIdx = 0;
            for (int i = 0; i < candles.size(); i++) {
                Candle c = candles.get(i);
                if (!c.timestamp().atZone(IST).toLocalDate().equals(date)) continue;
                MACD.MacdValue mv         = macd.get(i);
                SuperTrend.Value sv       = st.get(i);
                BollingerBands.Value bv   = bb.get(i);
                BigDecimal vwap           = vwapIdx < vwapDay.size() ? vwapDay.get(vwapIdx++) : null;
                SupertrendDto stDto = new SupertrendDto(sv != null ? sv.line() : null,
                                                        sv != null ? sv.bullish() : null);
                BollingerBandsDto bbDto = new BollingerBandsDto(bv != null ? bv.upper() : null,
                                                                 bv != null ? bv.middle() : null,
                                                                 bv != null ? bv.lower() : null);
                MovingAveragesDto maDto = new MovingAveragesDto(ema9.get(i), ema20.get(i),
                        ema50.get(i), ema200.get(i), sma20.get(i), vwap);

                result.add(new IndicatorSnapshotDto(
                        c.timestamp(), IST_FMT.format(c.timestamp()),
                        new CandleDataDto(c.open(), c.high(), c.low(), c.close(), c.volume()),
                        new MomentumDto(rsi.get(i),
                                new MacdDto(mv != null ? mv.macd() : null,
                                            mv != null ? mv.signal() : null,
                                            mv != null ? mv.histogram() : null)),
                        new TrendDto(adx.get(i), null, null, stDto),
                        maDto,
                        new VolatilityDto(atr.get(i), bbDto),
                        IndicatorSignals.buildSignals(c.close(), rsi.get(i), adx.get(i),
                                mv != null ? mv.histogram() : null, stDto, bbDto, vwap),
                        IndicatorSignals.buildCrossovers(c.close(), maDto),
                        smc.marketStructure(),
                        smc.smartMoney(),
                        pivots,
                        null   // ORB not computed in legacy endpoint
                ));
            }
            return result;
        } catch (Exception e) {
            log.warn("Day indicator breakdown failed for {} on {}: {}", inst.symbol(), date, e.getMessage());
            return null;
        }
    }

    static String cacheKey(String type, String symbol, Interval interval, int days, LocalDate date) {
        return type + "|" + symbol + "|" + interval + "|" + days + "|" + date;
    }


    /**
     * Past dates: use the configured TTL (default 15 min, but caller can override to 24h).
     * Today's date: use a short TTL (2 min) so incomplete intraday data refreshes frequently.
     * Tomorrow+: same as configured TTL (future dates return empty anyway).
     */
    /**
     * Computes classic pivot points from the previous trading day's candles.
     * Groups existing candles by IST date — no extra API call needed.
     * Returns null if no previous day candles found in the series.
     */
    private PivotPointsDto computePivotPoints(List<Candle> candles, LocalDate targetDate) {
        try {
            // Group candles by IST date
            Map<LocalDate, List<Candle>> byDate = candles.stream()
                    .collect(Collectors.groupingBy(
                            c -> c.timestamp().atZone(IST).toLocalDate()));

            // Find most recent date BEFORE targetDate
            LocalDate prevDay = byDate.keySet().stream()
                    .filter(d -> d.isBefore(targetDate))
                    .max(Comparator.naturalOrder())
                    .orElse(null);
            if (prevDay == null) return null;

            List<Candle> prev = byDate.get(prevDay);
            BigDecimal pH = prev.stream().map(Candle::high).max(BigDecimal::compareTo).orElse(null);
            BigDecimal pL = prev.stream().map(Candle::low).min(BigDecimal::compareTo).orElse(null);
            BigDecimal pC = prev.get(prev.size() - 1).close();
            if (pH == null || pL == null) return null;

            BigDecimal p  = pH.add(pL).add(pC).divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
            BigDecimal hl = pH.subtract(pL);
            BigDecimal r1 = p.multiply(BigDecimal.valueOf(2)).subtract(pL).setScale(2, RoundingMode.HALF_UP);
            BigDecimal r2 = p.add(hl).setScale(2, RoundingMode.HALF_UP);
            BigDecimal r3 = pH.add(p.subtract(pL).multiply(BigDecimal.valueOf(2))).setScale(2, RoundingMode.HALF_UP);
            BigDecimal s1 = p.multiply(BigDecimal.valueOf(2)).subtract(pH).setScale(2, RoundingMode.HALF_UP);
            BigDecimal s2 = p.subtract(hl).setScale(2, RoundingMode.HALF_UP);
            BigDecimal s3 = pL.subtract(pH.subtract(p).multiply(BigDecimal.valueOf(2))).setScale(2, RoundingMode.HALF_UP);

            return new PivotPointsDto(p, r1, r2, r3, s1, s2, s3, prevDay.toString());
        } catch (Exception e) {
            log.warn("Pivot point computation failed: {}", e.getMessage());
            return null;
        }
    }

    private Duration ttlFor(LocalDate date) {
        return date.isBefore(LocalDate.now())
                ? cacheProps.getTtlPast()    // past — data is complete, cache a full day
                : cacheProps.getTtlToday();  // today — data grows every hour, refresh often
    }
}
