package com.angle.trading.analysis;

import com.angle.trading.analysis.model.MarketStatus;
import com.angle.trading.broker.angel.AngelLtpClient;
import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.cache.IndicatorCacheStore;
import com.angle.trading.config.AnalysisLiveProperties;
import com.angle.trading.config.IndicatorCacheProperties;
import com.angle.trading.dto.AnalysisStatus;
import com.angle.trading.dto.BollingerBandsDto;
import com.angle.trading.dto.CandleDataDto;
import com.angle.trading.dto.CrossoversDto;
import com.angle.trading.dto.DayBreakdownDto;
import com.angle.trading.dto.IndicatorSnapshotDto;
import com.angle.trading.dto.IndicatorsDto;
import com.angle.trading.dto.LastSessionDto;
import com.angle.trading.dto.MacdDto;
import com.angle.trading.dto.MarketStatusDto;
import com.angle.trading.dto.MomentumDto;
import com.angle.trading.dto.OrbDto;
import com.angle.trading.dto.OrbTimeframeDto;
import com.angle.trading.dto.MovingAveragesDto;
import com.angle.trading.dto.MtfConclusionDto;
import com.angle.trading.dto.PivotPointsDto;
import com.angle.trading.dto.PriceDto;
import com.angle.trading.dto.SignalsDto;
import com.angle.trading.dto.StockAnalysisDto;
import com.angle.trading.dto.SupertrendDto;
import com.angle.trading.dto.TradeLevelDto;
import com.angle.trading.dto.TradeSuggestionDto;
import com.angle.trading.dto.TradeZoneDto;
import com.angle.trading.dto.TimeframeStatusDto;
import com.angle.trading.dto.TrendDto;
import com.angle.trading.dto.VolatilityDto;
import com.angle.trading.indicator.AverageDirectionalIndex;
import com.angle.trading.indicator.AverageTrueRange;
import com.angle.trading.indicator.BollingerBands;
import com.angle.trading.indicator.CandleAggregatorUtil;
import com.angle.trading.indicator.ExponentialMovingAverage;
import com.angle.trading.indicator.IndicatorSignals;
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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Facade service that orchestrates full stock analysis across all modes.
 *
 * <p>Coordinates: market calendar, live price, candle fetch, indicator compute,
 * SMC analysis, cache, MTF conclusion, and trade suggestion — all in one call.
 *
 * <p>Reusable by both the single-symbol GET endpoint and the bulk POST endpoint.
 * Also usable by scheduled briefing jobs, screeners, or any other component.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockAnalysisService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter TIME_FMT    = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter ISO_IST_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx")
            .withZone(IST);

    // ── Injected services ────────────────────────────────────────────
    private final AnalysisLiveProperties  props;
    private final MarketCalendarService   calendar;
    private final InstrumentMasterService instruments;
    private final MarketDataService       marketDataService;
    private final AngelLtpClient          ltpClient;
    private final IndicatorCacheStore     indicatorCache;
    private final IndicatorCacheProperties cacheProps;
    private final SmcAnalysisService      smcAnalysis;

    // ── MTF hierarchy ────────────────────────────────────────────────
    // key = highest TF, value = ordered list from lowest → highest TF
    static final Map<Interval, List<Interval>> MTF_HIERARCHY = Map.of(
            Interval.FIFTEEN_MINUTE, List.of(Interval.FIFTEEN_MINUTE),
            Interval.THIRTY_MINUTE,  List.of(Interval.FIFTEEN_MINUTE, Interval.THIRTY_MINUTE),
            Interval.ONE_HOUR,       List.of(Interval.FIFTEEN_MINUTE, Interval.THIRTY_MINUTE, Interval.ONE_HOUR),
            Interval.TWO_HOUR,       List.of(Interval.FIFTEEN_MINUTE, Interval.THIRTY_MINUTE, Interval.ONE_HOUR, Interval.TWO_HOUR),
            Interval.THREE_HOUR,     List.of(Interval.FIFTEEN_MINUTE, Interval.THIRTY_MINUTE, Interval.ONE_HOUR, Interval.TWO_HOUR, Interval.THREE_HOUR),
            Interval.FOUR_HOUR,      List.of(Interval.FIFTEEN_MINUTE, Interval.THIRTY_MINUTE, Interval.ONE_HOUR, Interval.TWO_HOUR, Interval.THREE_HOUR, Interval.FOUR_HOUR),
            Interval.ONE_DAY,        List.of(Interval.FIFTEEN_MINUTE, Interval.THIRTY_MINUTE, Interval.ONE_HOUR, Interval.TWO_HOUR, Interval.THREE_HOUR, Interval.FOUR_HOUR, Interval.ONE_DAY)
    );

    // ── Analysis modes ───────────────────────────────────────────────

    /**
     * The three analysis modes — auto-detected from request parameters.
     */
    public enum AnalysisMode { LIVE, CONSOLIDATED, HISTORICAL }

    // ── Core public API ──────────────────────────────────────────────

    /**
     * Runs full stock analysis for a single symbol.
     *
     * <p>Returns a complete {@link StockAnalysisDto} with price, MTF indicators,
     * SMC analysis, trade suggestion, and market status.
     * Never throws — errors are returned as {@link AnalysisStatus#ERROR} results.
     *
     * @param params all analysis parameters
     * @return complete analysis response
     */
    public StockAnalysisDto analyze(AnalysisParams params) {
        log.info("[Analysis] {} mode={} interval={} days={} asOf={}",
                params.symbol(),
                detectMode(params.live(), params.asOf()),
                params.interval(),
                params.days(),
                params.asOf());

        try {
            return doAnalyze(params);
        } catch (IllegalArgumentException e) {
            log.warn("[Analysis] Bad request for {}: {}", params.symbol(), e.getMessage());
            return StockAnalysisDto.error(params.symbol(), e.getMessage());
        } catch (Exception e) {
            log.error("[Analysis] Unexpected error for {}: {}", params.symbol(), e.getMessage());
            return StockAnalysisDto.error(params.symbol(),
                    "Unexpected error — please retry. " + e.getMessage());
        }
    }

    // ── Mode detection helpers ───────────────────────────────────────

    /**
     * Detects the analysis mode from request parameters.
     * live=true → LIVE. asOf given → HISTORICAL. Otherwise → CONSOLIDATED.
     */
    public AnalysisMode detectMode(boolean live, LocalDate asOf) {
        if (live)           return AnalysisMode.LIVE;
        if (asOf != null)   return AnalysisMode.HISTORICAL;
        return AnalysisMode.CONSOLIDATED;
    }

    /** Looks up an instrument by symbol, logs a warning if not found. */
    public Optional<Instrument> findInstrument(String symbol) {
        Optional<Instrument> inst = instruments.findBySymbol(symbol);
        if (inst.isEmpty()) log.warn("[Analysis] Symbol not found in scrip master: {}", symbol);
        return inst;
    }

    /** Returns the exchange for the instrument (auto-detected from scrip master). */
    public Exchange exchangeOf(Instrument inst) { return inst.exchange(); }

    /** Returns today for LIVE/CONSOLIDATED, or asOf for HISTORICAL. */
    public LocalDate effectiveDate(AnalysisMode mode, LocalDate asOf) {
        return (mode == AnalysisMode.HISTORICAL && asOf != null) ? asOf : LocalDate.now();
    }

    /** Returns requested baseInterval or the configured default. */
    public Interval effectiveBaseInterval(Interval requested) {
        return requested != null ? requested : props.getBaseInterval();
    }

    /** Validates live mode is enabled in config. */
    public void validateLiveEnabled(boolean live) {
        if (live && !props.isEnabled())
            throw new IllegalStateException(
                    "Live analysis is disabled. Set analysis.live.enabled=true to enable.");
    }

    /** Validates bulk symbol count against configured maximum. */
    public void validateBulkSize(int symbolCount) {
        int max = props.getMaxSymbols();
        if (symbolCount == 0)
            throw new IllegalArgumentException("symbols list cannot be empty.");
        if (symbolCount > max)
            throw new IllegalArgumentException(String.format(
                    "Too many symbols: %d. Maximum: %d. Split into multiple requests.",
                    symbolCount, max));
    }

    /** Returns current IST time. */
    public ZonedDateTime nowIst() { return ZonedDateTime.now(IST); }

    // ── Private implementation ───────────────────────────────────────

    private StockAnalysisDto doAnalyze(AnalysisParams params) {
        // 1. Validate + find instrument
        validateLiveEnabled(params.live());
        Instrument inst = findInstrument(params.symbol()).orElse(null);
        if (inst == null) return StockAnalysisDto.error(params.symbol(),
                "Symbol '" + params.symbol() + "' not found in scrip master. " +
                "Use GET /api/instruments/lookup?symbol=... to find the correct symbol.");

        // 2. Resolve params
        AnalysisMode  mode     = detectMode(params.live(), params.asOf());
        Interval      baseIv   = effectiveBaseInterval(params.baseInterval());
        Interval      topIv    = params.interval() != null ? params.interval() : Interval.ONE_DAY;
        int           days     = params.days() > 0 ? params.days() : 60;
        LocalDate     toDate   = effectiveDate(mode, params.asOf());
        Exchange      exchange = exchangeOf(inst);
        ZonedDateTime now      = nowIst();

        // 3. Market status
        MarketStatus mkt = calendar.getMarketStatus(exchange, now, baseIv);

        // 4. LIVE + CLOSED → intelligent response with last session data
        if (mode == AnalysisMode.LIVE && !mkt.open()) {
            return buildMarketClosedResponse(inst, params, baseIv, topIv, days, toDate, mkt, now);
        }

        // 5. Live price (LIVE + CONSOLIDATED only)
        PriceDto price = null;
        if (mode != AnalysisMode.HISTORICAL) {
            price = fetchPrice(inst);
        }

        // 6. MTF indicators (cached — dynamic TTL in live mode)
        LinkedHashMap<String, IndicatorsDto> mtf = computeMtf(
                inst, topIv, baseIv, days, toDate, mode, mkt);

        // 7. Conclusion + trade suggestion
        List<Interval>     tfs        = MTF_HIERARCHY.getOrDefault(topIv, List.of(topIv));
        MtfConclusionDto   conclusion = buildConclusion(mtf, tfs);
        String             highestKey = tfs.get(tfs.size() - 1).name();
        IndicatorsDto      highestTf  = mtf.get(highestKey);
        TradeSuggestionDto trade      = buildTradeSuggestion(conclusion, highestTf);

        // 8. Timeframe status (LIVE mode only)
        Map<String, TimeframeStatusDto> tfStatus = null;
        if (mode == AnalysisMode.LIVE && mkt.open() && mkt.sessionOpen() != null) {
            LocalTime sessionOpen = LocalTime.parse(mkt.sessionOpen());
            tfStatus = computeTimeframeStatus(tfs, baseIv, sessionOpen, now.toLocalTime());
        }

        // 9. Market status DTO
        MarketStatusDto mktDto = toMarketStatusDto(mkt, conclusion, trade, price);

        // 10. ORB — from 15m candles already in MTF (LIVE + CONSOLIDATED only)
        OrbDto orb = null;
        if (mode != AnalysisMode.HISTORICAL) {
            List<Candle> fifteenMin = fetchCandlesForOrb(inst, toDate, days);
            BigDecimal ltpForOrb = price != null ? price.ltp() : null;
            orb = computeOrb(fifteenMin, toDate, ltpForOrb);
        }

        // 11. Day breakdown (optional) — compute + cache so L3 DB save is triggered
        DayBreakdownDto breakdown = null;
        if (params.breakdown()) {
            Interval breakdownIv = isHigherThanHour(topIv) ? Interval.ONE_HOUR : topIv;
            // Check cache first (reuse if already saved)
            String bdKey = cacheKey(inst.symbol(), breakdownIv, days, toDate);
            String dayCacheKey = "day|" + inst.symbol() + "|" + breakdownIv + "|" + days + "|" + toDate;
            List<IndicatorSnapshotDto> bdRows =
                    indicatorCache.<List<IndicatorSnapshotDto>>get(dayCacheKey).orElse(null);
            if (bdRows == null) {
                breakdown = buildDayBreakdown(inst, toDate, breakdownIv, baseIv, days);
                if (breakdown != null && breakdown.rows() != null) {
                    // Cache rows → triggers L2 Redis + L3 DB save via IndicatorCacheStore
                    indicatorCache.put(dayCacheKey, breakdown.rows(), ttlFor(toDate));
                }
            } else {
                // Reconstruct DayBreakdownDto from cached rows
                int expected = expectedBarsForInterval(breakdownIv);
                String progress = bdRows.size() + " of " + expected + " bars complete ("
                        + (bdRows.size() * 100 / Math.max(expected, 1)) + "% of day)";
                breakdown = new DayBreakdownDto(toDate.toString(), breakdownIv.name(),
                        bdRows.size(), expected, progress, bdRows);
            }
        }

        return new StockAnalysisDto(
                inst.symbol(), inst.name(), inst.exchSeg(), mode.name(), baseIv.name(),
                toDate.toString(),
                AnalysisStatus.OK, null,
                false, null, null, null,
                price, mktDto, tfStatus, mtf, conclusion, trade, orb, breakdown, null
        );
    }

    /**
     * Builds the response when live=true but market is currently CLOSED.
     * Computes last session analysis and includes it so users don't need
     * a second API call to see indicators.
     */
    private StockAnalysisDto buildMarketClosedResponse(Instrument inst, AnalysisParams params,
                                                         Interval baseIv, Interval topIv,
                                                         int days, LocalDate toDate,
                                                         MarketStatus mkt, ZonedDateTime now) {
        log.info("[Analysis] LIVE mode but market CLOSED for {} — computing last session", inst.symbol());

        // Compute last session analysis (today's or last trading day's data)
        LinkedHashMap<String, IndicatorsDto> mtf = computeMtf(
                inst, topIv, baseIv, days, toDate, AnalysisMode.CONSOLIDATED, mkt);
        List<Interval>     tfs        = MTF_HIERARCHY.getOrDefault(topIv, List.of(topIv));
        MtfConclusionDto   conclusion = buildConclusion(mtf, tfs);
        TradeSuggestionDto trade      = buildTradeSuggestion(conclusion,
                mtf.get(tfs.get(tfs.size() - 1).name()));

        // Last known price
        PriceDto price = fetchPrice(inst);

        // Suggestions for the user
        String exchange = inst.exchSeg();
        List<String> suggestions = List.of(
                "Use ?asOf=" + toDate + " to analyse the last completed session",
                "Use ?asOf=" + toDate + "&breakdown=true&interval=ONE_HOUR for hourly breakdown",
                "Remove ?live=true for consolidated analysis without live mode"
        );

        // Build market status with CLOSED + nextMarketOpen
        MarketStatusDto mktDto = toMarketStatusDto(mkt, conclusion, trade, price);

        return new StockAnalysisDto(
                inst.symbol(), inst.name(), exchange, AnalysisMode.LIVE.name(), baseIv.name(),
                toDate.toString(),
                AnalysisStatus.OK, null,
                false, null, null, null,
                price, mktDto, null, mtf, conclusion, trade, null, null, suggestions
        );
    }

    // ── Price ─────────────────────────────────────────────────────

    private PriceDto fetchPrice(Instrument inst) {
        try {
            AngelLtpClient.QuoteData q = ltpClient
                    .fetchQuote(inst.exchSeg(), inst.symbol(), inst.token())
                    .orElse(null);
            if (q == null) return null;
            BigDecimal change = (q.ltp() != null && q.previousClose() != null)
                    ? q.ltp().subtract(q.previousClose()) : null;
            BigDecimal changePct = (change != null && q.previousClose() != null
                    && q.previousClose().compareTo(BigDecimal.ZERO) != 0)
                    ? change.divide(q.previousClose(), 4, RoundingMode.HALF_UP)
                             .multiply(BigDecimal.valueOf(100))
                    : null;
            return new PriceDto(q.ltp(), q.open(), q.high(), q.low(),
                    q.previousClose(), change, changePct);
        } catch (Exception e) {
            log.warn("[Analysis] Price fetch failed for {}: {}", inst.symbol(), e.getMessage());
            return null;
        }
    }

    // ── MTF Indicators ────────────────────────────────────────────

    /**
     * Computes indicators for every timeframe in the MTF stack.
     * Uses cache (L1 JVM + L2 Redis) and only hits Angel for cache misses.
     * 2h/3h/4h are synthesized from cached 1h candles — no extra Angel calls.
     * In LIVE mode, today's candles use dynamic TTL (expires at next candle close).
     */
    private LinkedHashMap<String, IndicatorsDto> computeMtf(Instrument inst, Interval topIv,
                                                              Interval baseIv, int days,
                                                              LocalDate toDate,
                                                              AnalysisMode mode,
                                                              MarketStatus mkt) {
        List<Interval> tfs = MTF_HIERARCHY.getOrDefault(topIv, List.of(topIv));
        LinkedHashMap<String, IndicatorsDto> result = new LinkedHashMap<>(tfs.size());

        // Pre-fetch 1h candles once for synthetic 2h/3h/4h aggregation
        List<Candle> oneHourCandles = null;
        boolean needsSynthetic = tfs.stream().anyMatch(
                tf -> tf == Interval.TWO_HOUR || tf == Interval.THREE_HOUR || tf == Interval.FOUR_HOUR);
        if (needsSynthetic) {
            try {
                oneHourCandles = marketDataService.getCandles(
                        "ANGEL", inst.exchange(), inst.token(),
                        Interval.ONE_HOUR, toDate.minusDays(days), toDate);
            } catch (Exception e) {
                log.warn("[Analysis] Could not fetch 1h candles for synthetic TFs: {}", e.getMessage());
            }
        }

        for (Interval tf : tfs) {
            String cacheKey = cacheKey(inst.symbol(), tf, days, toDate);
            IndicatorsDto cached = indicatorCache.<IndicatorsDto>get(cacheKey).orElse(null);
            if (cached != null) {
                log.debug("[Analysis] Cache HIT: {}", cacheKey);
                result.put(tf.name(), cached);
                continue;
            }

            IndicatorsDto ind;
            if ((tf == Interval.TWO_HOUR || tf == Interval.THREE_HOUR || tf == Interval.FOUR_HOUR)
                    && oneHourCandles != null) {
                int multiplier = switch (tf) {
                    case TWO_HOUR   -> 2;
                    case THREE_HOUR -> 3;
                    default         -> 4;
                };
                List<Candle> synthetic = CandleAggregatorUtil.aggregate(oneHourCandles, multiplier);
                ind = buildIndicatorsFromCandles(synthetic, inst, tf, toDate);
            } else {
                ind = computeIndicators(inst, tf, days, toDate);
            }

            if (ind != null) {
                Duration ttl = (mode == AnalysisMode.LIVE && mkt != null && mkt.open()
                        && mkt.sessionOpen() != null && mkt.nextCandleAt() != null)
                        ? dynamicTtlForLive(mkt)
                        : ttlFor(toDate);
                indicatorCache.put(cacheKey, ind, ttl);
                result.put(tf.name(), ind);
            }
        }
        return result;
    }

    /**
     * Computes per-timeframe completion status for the live mode response.
     * Purely time-based — no server state or database needed.
     *
     * @param tfs         ordered list of timeframes (lowest → highest)
     * @param baseIv      base polling interval
     * @param marketOpen  today's session open time
     * @param now         current IST time
     */
    private Map<String, TimeframeStatusDto> computeTimeframeStatus(List<Interval> tfs,
                                                                     Interval baseIv,
                                                                     LocalTime marketOpen,
                                                                     LocalTime now) {
        Map<String, TimeframeStatusDto> status = new LinkedHashMap<>(tfs.size());
        for (Interval tf : tfs) {
            int     completeBars    = calendar.completeBars(tf, marketOpen, now);
            boolean justCompleted   = calendar.justCompleted(tf, baseIv, marketOpen, now);
            int     tfMins          = calendar.toMinutes(tf);

            // latestCandleAt — time of last complete bar
            String latestStr = completeBars > 0
                    ? marketOpen.plusMinutes((long) completeBars * tfMins)
                               .format(TIME_FMT) + " IST"
                    : null;

            // nextBarAt — when next bar closes
            String nextBarStr = marketOpen.plusMinutes((long)(completeBars + 1) * tfMins)
                    .format(TIME_FMT) + " IST";

            status.put(tf.name(), new TimeframeStatusDto(
                    completeBars, justCompleted, latestStr, nextBarStr));
        }
        return status;
    }

    /**
     * Dynamic TTL for live mode — cache today's candles until the NEXT candle closes.
     * This ensures Angel is called exactly once per candle interval, regardless of
     * how often the client polls.
     *
     * <p>Falls back to the configured today TTL if next candle time is unavailable.
     */
    private Duration dynamicTtlForLive(MarketStatus mkt) {
        if (mkt.nextCandleAt() == null) return cacheProps.getTtlToday();
        Duration untilNextCandle = Duration.between(ZonedDateTime.now(IST), mkt.nextCandleAt());
        if (untilNextCandle.isNegative() || untilNextCandle.isZero()) {
            return cacheProps.getTtlToday();
        }
        // Add 5-second buffer to ensure the candle is fully formed before refetching
        return untilNextCandle.plusSeconds(5);
    }

    /**
     * Fetches candles for the instrument and computes all indicators.
     * Returns null if candle fetch fails.
     */
    private IndicatorsDto computeIndicators(Instrument inst, Interval interval,
                                             int days, LocalDate toDate) {
        try {
            List<Candle> candles = marketDataService.getCandles(
                    "ANGEL", inst.exchange(), inst.token(),
                    interval, toDate.minusDays(days), toDate);
            if (candles.isEmpty()) return null;
            return buildIndicatorsFromCandles(candles, inst, interval, toDate);
        } catch (Exception e) {
            log.warn("[Analysis] Indicator compute failed for {}:{}: {}",
                    inst.symbol(), interval, e.getMessage());
            return null;
        }
    }

    /**
     * Computes all indicators from a pre-fetched candle list.
     * Used for both native and synthetic (aggregated) timeframes.
     */
    private IndicatorsDto buildIndicatorsFromCandles(List<Candle> candles, Instrument inst,
                                                      Interval interval, LocalDate toDate) {
        if (candles.isEmpty()) return null;
        int last = candles.size() - 1;

        List<BigDecimal>                           rsi    = new RelativeStrengthIndex(14).compute(candles);
        List<AverageDirectionalIndex.AdxDiValue>   adxDi  = new AverageDirectionalIndex(14).computeWithDi(candles);
        List<BigDecimal>                           atr    = new AverageTrueRange(14).compute(candles);
        List<BigDecimal>         ema9   = new ExponentialMovingAverage(9).compute(candles);
        List<BigDecimal>         ema20  = new ExponentialMovingAverage(20).compute(candles);
        List<BigDecimal>         ema50  = new ExponentialMovingAverage(50).compute(candles);
        List<BigDecimal>        ema200  = new ExponentialMovingAverage(200).compute(candles);
        List<BigDecimal>         sma20  = new SimpleMovingAverage(20).compute(candles);
        List<MACD.MacdValue>      macd  = new MACD(12, 26, 9).computeSeries(candles);
        List<BigDecimal>          vwap  = new VwapIndicator().compute(candles);
        List<SuperTrend.Value>      st  = new SuperTrend(10, 3.0).compute(candles);
        List<BollingerBands.Value>  bb  = new BollingerBands(20, 2.0).compute(candles);

        MACD.MacdValue       mv  = macd.get(last);
        SuperTrend.Value                  sv    = st.get(last);
        BollingerBands.Value              bv    = bb.get(last);
        AverageDirectionalIndex.AdxDiValue adv  = adxDi.get(last);
        BigDecimal                        close = candles.get(last).close();

        SupertrendDto    stDto  = new SupertrendDto(sv != null ? sv.line() : null,
                                                     sv != null ? sv.bullish() : null);
        BollingerBandsDto bbDto = new BollingerBandsDto(bv != null ? bv.upper() : null,
                                                         bv != null ? bv.middle() : null,
                                                         bv != null ? bv.lower() : null);
        MovingAveragesDto maDto = new MovingAveragesDto(ema9.get(last), ema20.get(last),
                ema50.get(last), ema200.get(last), sma20.get(last), vwap.get(last));

        SmcAnalysisService.Result smc = smcAnalysis.analyze(candles, close);
        PivotPointsDto pivots = computePivotPoints(candles, toDate);

        BigDecimal adx14   = adv != null ? adv.adx()     : null;
        BigDecimal plusDi  = adv != null ? adv.plusDi()  : null;
        BigDecimal minusDi = adv != null ? adv.minusDi() : null;

        return new IndicatorsDto(
                new MomentumDto(rsi.get(last),
                        new MacdDto(mv != null ? mv.macd() : null,
                                    mv != null ? mv.signal() : null,
                                    mv != null ? mv.histogram() : null)),
                new TrendDto(adx14, plusDi, minusDi, stDto),
                maDto,
                new VolatilityDto(atr.get(last), bbDto),
                IndicatorSignals.buildSignals(close, rsi.get(last), adx14,
                        mv != null ? mv.histogram() : null, stDto, bbDto, vwap.get(last)),
                IndicatorSignals.buildCrossovers(close, maDto),
                smc.marketStructure(),
                smc.smartMoney(),
                pivots,
                interval.name(), candles.size(),
                DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a").withZone(IST)
                        .format(candles.get(last).timestamp()) + " IST"
        );
    }

    // ── Pivot Points ─────────────────────────────────────────────

    private PivotPointsDto computePivotPoints(List<Candle> candles, LocalDate targetDate) {
        try {
            Map<LocalDate, List<Candle>> byDate = candles.stream()
                    .collect(Collectors.groupingBy(
                            c -> c.timestamp().atZone(IST).toLocalDate()));
            LocalDate prevDay = byDate.keySet().stream()
                    .filter(d -> d.isBefore(targetDate))
                    .max(java.util.Comparator.naturalOrder()).orElse(null);
            if (prevDay == null) return null;

            List<Candle> prev = byDate.get(prevDay);
            BigDecimal pH = prev.stream().map(Candle::high).max(BigDecimal::compareTo).orElse(null);
            BigDecimal pL = prev.stream().map(Candle::low).min(BigDecimal::compareTo).orElse(null);
            BigDecimal pC = prev.get(prev.size() - 1).close();
            if (pH == null || pL == null) return null;

            BigDecimal p  = pH.add(pL).add(pC).divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
            BigDecimal hl = pH.subtract(pL);
            return new PivotPointsDto(p,
                    p.multiply(BigDecimal.valueOf(2)).subtract(pL).setScale(2, RoundingMode.HALF_UP),
                    p.add(hl).setScale(2, RoundingMode.HALF_UP),
                    pH.add(p.subtract(pL).multiply(BigDecimal.valueOf(2))).setScale(2, RoundingMode.HALF_UP),
                    p.multiply(BigDecimal.valueOf(2)).subtract(pH).setScale(2, RoundingMode.HALF_UP),
                    p.subtract(hl).setScale(2, RoundingMode.HALF_UP),
                    pL.subtract(pH.subtract(p).multiply(BigDecimal.valueOf(2))).setScale(2, RoundingMode.HALF_UP),
                    prevDay.toString());
        } catch (Exception e) {
            log.warn("[Analysis] Pivot points failed: {}", e.getMessage());
            return null;
        }
    }

    // ── MTF Conclusion + Trade Suggestion ────────────────────────

    /**
     * Derives the cross-timeframe conclusion: primary bias, confidence,
     * trade setup, aligned and conflicting timeframes.
     */
    MtfConclusionDto buildConclusion(LinkedHashMap<String, IndicatorsDto> timeframes,
                                      List<Interval> tfs) {
        int n = tfs.size();
        List<String> tfNames = tfs.stream().map(Interval::name).toList();

        // Higher TFs = top half — define structural bias
        int    split     = Math.max(1, n / 2);
        List<String> higherTfs = tfNames.subList(split, n);
        List<String> lowerTfs  = tfNames.subList(0, split);

        // Primary bias from highest TF
        String primaryBias = trendOf(timeframes.get(tfNames.get(n - 1)));

        List<String> aligned     = new ArrayList<>();
        List<String> conflicting = new ArrayList<>();
        for (String tf : tfNames) {
            String t = trendOf(timeframes.get(tf));
            if (primaryBias != null && primaryBias.equals(t)) aligned.add(tf);
            else if (t != null)                                conflicting.add(tf);
        }

        // Confidence based on how many higher TFs agree
        long higherAligned = higherTfs.stream()
                .filter(tf -> primaryBias != null && primaryBias.equals(trendOf(timeframes.get(tf))))
                .count();
        String confidence = higherAligned == higherTfs.size()                    ? "STRONG"
                : higherAligned >= Math.ceil(higherTfs.size() / 2.0) ? "MODERATE"
                : "WEAK";

        // Trade setup
        String tradeSetup;
        if (primaryBias == null) {
            tradeSetup = "NO_CLEAR_TREND";
        } else {
            long lowerAligned  = lowerTfs.stream()
                    .filter(tf -> primaryBias.equals(trendOf(timeframes.get(tf)))).count();
            long lowerConflict = lowerTfs.stream()
                    .filter(tf -> { String t = trendOf(timeframes.get(tf));
                                     return t != null && !primaryBias.equals(t); }).count();
            if      (lowerAligned  == lowerTfs.size()) tradeSetup = "TREND_CONTINUATION";
            else if (lowerConflict == lowerTfs.size()) tradeSetup = "WAIT_FOR_PULLBACK_END";
            else                                        tradeSetup = "MIXED_SIGNALS";
        }

        return new MtfConclusionDto(primaryBias, aligned, conflicting, confidence, tradeSetup);
    }

    /**
     * Builds an actionable trade suggestion from the MTF conclusion
     * and the highest timeframe's indicators (OB zones, swing levels, liquidity).
     */
    TradeSuggestionDto buildTradeSuggestion(MtfConclusionDto conclusion,
                                              IndicatorsDto highestTfInd) {
        String bias  = conclusion.primaryBias();
        String setup = conclusion.tradeSetup();

        if (bias == null || "NO_CLEAR_TREND".equals(setup) || "MIXED_SIGNALS".equals(setup)) {
            return new TradeSuggestionDto("NO_TRADE", "NO_TRADE", null, null, null, null, null,
                    "No clear trend across timeframes — stay out.");
        }

        boolean isShort  = "BEARISH".equals(bias);
        String direction = isShort ? "SHORT" : "LONG";
        String timing    = "TREND_CONTINUATION".equals(setup) ? "NOW" : "WAIT";

        String waitFor = null;
        if ("WAIT".equals(timing) && !conclusion.conflictingTimeframes().isEmpty()) {
            waitFor = "Wait for " + String.join(" + ", conclusion.conflictingTimeframes())
                    + " SuperTrend to turn " + bias + " (pullback ending)";
        }

        var sm  = highestTfInd != null ? highestTfInd.smartMoney()     : null;
        var ms  = highestTfInd != null ? highestTfInd.marketStructure() : null;
        var vol = highestTfInd != null ? highestTfInd.volatility()      : null;
        BigDecimal atr = vol != null ? vol.atr14() : null;

        // Entry zone from nearest Order Block
        TradeZoneDto entryZone = null;
        BigDecimal   entryMid  = null;
        var ob = isShort ? (sm != null ? sm.nearestBearishOB() : null)
                         : (sm != null ? sm.nearestBullishOB() : null);
        if (ob != null) {
            entryZone = new TradeZoneDto(ob.bottom(), ob.top(),
                    isShort ? "BEARISH_ORDER_BLOCK" : "BULLISH_ORDER_BLOCK");
            entryMid  = ob.bottom().add(ob.top())
                    .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        }

        // Stop loss — beyond nearest swing + ATR buffer
        TradeLevelDto stopLoss = null;
        BigDecimal buffer = atr != null
                ? atr.multiply(BigDecimal.valueOf(0.3)).setScale(2, RoundingMode.HALF_UP)
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

        // Target 1 — nearest swing
        TradeLevelDto target1 = null;
        if (isShort && ms != null && ms.nearestSwingLow() != null) {
            target1 = new TradeLevelDto(ms.nearestSwingLow(), "NEAREST_SWING_LOW",
                    rr(entryMid, stopLoss, ms.nearestSwingLow(), true));
        } else if (!isShort && ms != null && ms.nearestSwingHigh() != null) {
            target1 = new TradeLevelDto(ms.nearestSwingHigh(), "NEAREST_SWING_HIGH",
                    rr(entryMid, stopLoss, ms.nearestSwingHigh(), false));
        }

        // Target 2 — liquidity
        TradeLevelDto target2 = null;
        if (isShort && sm != null && sm.sellSideLiquidityBelow() != null) {
            target2 = new TradeLevelDto(sm.sellSideLiquidityBelow(), "SELL_SIDE_LIQUIDITY",
                    rr(entryMid, stopLoss, sm.sellSideLiquidityBelow(), true));
        } else if (!isShort && sm != null && sm.buySideLiquidityAbove() != null) {
            target2 = new TradeLevelDto(sm.buySideLiquidityAbove(), "BUY_SIDE_LIQUIDITY",
                    rr(entryMid, stopLoss, sm.buySideLiquidityAbove(), false));
        }

        String note = direction + " setup (" + conclusion.confidence() + " confidence). "
                + ("NOW".equals(timing) ? "All timeframes aligned — enter now."
                                        : "Higher TFs " + bias + " but lower TFs in pullback. " + waitFor + ".")
                + (stopLoss != null ? " Stop: ₹" + stopLoss.price() + "." : "")
                + (target1  != null ? " T1: ₹"  + target1.price() + " (" + target1.riskReward() + ")." : "");

        return new TradeSuggestionDto(direction, timing, waitFor,
                entryZone, stopLoss, target1, target2, note);
    }

    // ── Day Breakdown ─────────────────────────────────────────────

    /**
     * Builds the optional day breakdown section — one indicator row per candle
     * for the specified date. Reusable for both analysis and standalone endpoints.
     */
    DayBreakdownDto buildDayBreakdown(Instrument inst, LocalDate date, Interval interval,
                                       Interval baseInterval, int days) {
        try {
            List<Candle> candles = marketDataService.getCandles(
                    "ANGEL", inst.exchange(), inst.token(),
                    interval, date.minusDays(days), date);
            if (candles.isEmpty()) return null;

            List<BigDecimal>                           rsi    = new RelativeStrengthIndex(14).compute(candles);
            List<AverageDirectionalIndex.AdxDiValue>   adxDiB = new AverageDirectionalIndex(14).computeWithDi(candles);
            List<BigDecimal>                           atr    = new AverageTrueRange(14).compute(candles);
            List<BigDecimal>         ema9   = new ExponentialMovingAverage(9).compute(candles);
            List<BigDecimal>         ema20  = new ExponentialMovingAverage(20).compute(candles);
            List<BigDecimal>         ema50  = new ExponentialMovingAverage(50).compute(candles);
            List<BigDecimal>        ema200  = new ExponentialMovingAverage(200).compute(candles);
            List<BigDecimal>         sma20  = new SimpleMovingAverage(20).compute(candles);
            List<MACD.MacdValue>      macd  = new MACD(12, 26, 9).computeSeries(candles);
            List<SuperTrend.Value>      st  = new SuperTrend(10, 3.0).compute(candles);
            List<BollingerBands.Value>  bb  = new BollingerBands(20, 2.0).compute(candles);

            // Session VWAP — computed only on the day's candles
            List<Candle> dayCandles = candles.stream()
                    .filter(c -> c.timestamp().atZone(IST).toLocalDate().equals(date))
                    .toList();
            List<BigDecimal> vwapDay = new VwapIndicator().compute(dayCandles);

            SmcAnalysisService.Result smc = smcAnalysis.analyze(candles);
            PivotPointsDto pivots         = computePivotPoints(candles, date);

            DateTimeFormatter istFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(IST);
            List<IndicatorSnapshotDto> rows = new ArrayList<>();
            int vwapIdx    = 0;
            int dayBarIdx  = 0;   // index within today's candles (for ORB computation)

            // Pre-sort today's candles for ORB
            List<Candle> todayCandles = candles.stream()
                    .filter(c -> c.timestamp().atZone(IST).toLocalDate().equals(date))
                    .sorted(Comparator.comparing(Candle::timestamp))
                    .toList();

            for (int i = 0; i < candles.size(); i++) {
                Candle c = candles.get(i);
                if (!c.timestamp().atZone(IST).toLocalDate().equals(date)) continue;

                MACD.MacdValue                    mv   = macd.get(i);
                SuperTrend.Value                  sv   = st.get(i);
                BollingerBands.Value              bv   = bb.get(i);
                AverageDirectionalIndex.AdxDiValue adv = adxDiB.get(i);
                BigDecimal vwap = vwapIdx < vwapDay.size() ? vwapDay.get(vwapIdx++) : null;

                SupertrendDto    stDto  = new SupertrendDto(sv != null ? sv.line() : null,
                                                             sv != null ? sv.bullish() : null);
                BollingerBandsDto bbDto = new BollingerBandsDto(bv != null ? bv.upper() : null,
                                                                 bv != null ? bv.middle() : null,
                                                                 bv != null ? bv.lower() : null);
                MovingAveragesDto maDto = new MovingAveragesDto(ema9.get(i), ema20.get(i),
                        ema50.get(i), ema200.get(i), sma20.get(i), vwap);
                BigDecimal adxVal  = adv != null ? adv.adx()     : null;
                BigDecimal pDiVal  = adv != null ? adv.plusDi()  : null;
                BigDecimal mDiVal  = adv != null ? adv.minusDi() : null;

                // ORB — computed up to current bar index within the day
                OrbDto rowOrb = computeOrb(todayCandles.subList(0, dayBarIdx + 1), date, c.close());
                dayBarIdx++;

                rows.add(new IndicatorSnapshotDto(
                        c.timestamp(), istFmt.format(c.timestamp()),
                        new CandleDataDto(c.open(), c.high(), c.low(), c.close(), c.volume()),
                        new MomentumDto(rsi.get(i),
                                new MacdDto(mv != null ? mv.macd() : null,
                                            mv != null ? mv.signal() : null,
                                            mv != null ? mv.histogram() : null)),
                        new TrendDto(adxVal, pDiVal, mDiVal, stDto),
                        maDto,
                        new VolatilityDto(atr.get(i), bbDto),
                        IndicatorSignals.buildSignals(c.close(), rsi.get(i), adxVal,
                                mv != null ? mv.histogram() : null, stDto, bbDto, vwap),
                        IndicatorSignals.buildCrossovers(c.close(), maDto),
                        smc.marketStructure(), smc.smartMoney(), pivots, rowOrb
                ));
            }

            int expectedBars = expectedBarsForInterval(interval);
            String progress = rows.size() + " of " + expectedBars + " bars complete ("
                    + (rows.size() * 100 / Math.max(expectedBars, 1)) + "% of day)";

            return new DayBreakdownDto(date.toString(), interval.name(),
                    rows.size(), expectedBars, progress, rows);
        } catch (Exception e) {
            log.warn("[Analysis] Day breakdown failed for {} on {}: {}",
                    inst.symbol(), date, e.getMessage());
            return null;
        }
    }

    // ── Market Status DTO ─────────────────────────────────────────

    private MarketStatusDto toMarketStatusDto(MarketStatus mkt, MtfConclusionDto conclusion,
                                               TradeSuggestionDto trade, PriceDto price) {
        String nextCandle   = mkt.nextCandleAt() != null ? ISO_IST_FMT.format(mkt.nextCandleAt()) : null;
        String nextMktOpen  = mkt.nextMarketOpen() != null ? ISO_IST_FMT.format(mkt.nextMarketOpen()) : null;

        LastSessionDto lastSession = null;
        if (!mkt.open() && conclusion != null) {
            lastSession = new LastSessionDto(
                    LocalDate.now(IST).toString(),
                    mkt.sessionClose() != null ? mkt.sessionClose() + " IST" : null,
                    price, conclusion, trade);
        }

        return new MarketStatusDto(
                mkt.open() ? "OPEN" : "CLOSED",
                mkt.sessionOpen(),
                mkt.sessionClose(),
                mkt.statusMessage(),
                nextCandle,
                nextMktOpen,
                mkt.sessionProgress(),
                mkt.sessionNote(),
                lastSession
        );
    }

    // ── Cache helpers ─────────────────────────────────────────────

    private static String cacheKey(String symbol, Interval interval, int days, LocalDate date) {
        return "indicators|" + symbol + "|" + interval + "|" + days + "|" + date;
    }

    private Duration ttlFor(LocalDate date) {
        return date.isBefore(LocalDate.now())
                ? cacheProps.getTtlPast()
                : cacheProps.getTtlToday();
    }

    // ── Small utility helpers ─────────────────────────────────────

    private static String trendOf(IndicatorsDto dto) {
        if (dto == null || dto.signals() == null) return null;
        return dto.signals().trend();
    }

    private static String rr(BigDecimal entry, TradeLevelDto stop,
                               BigDecimal target, boolean isShort) {
        if (entry == null || stop == null || target == null) return null;
        BigDecimal risk = stop.price().subtract(entry).abs();
        if (risk.compareTo(BigDecimal.ZERO) == 0) return null;
        return "1:" + entry.subtract(target).abs().divide(risk, 1, RoundingMode.HALF_UP);
    }

    /** Fetches 15m candles for ORB computation — reuses cache. */
    private List<Candle> fetchCandlesForOrb(Instrument inst, LocalDate toDate, int days) {
        try {
            return marketDataService.getCandles(
                    "ANGEL", inst.exchange(), inst.token(),
                    Interval.FIFTEEN_MINUTE, toDate.minusDays(days), toDate);
        } catch (Exception e) {
            log.warn("[Analysis] ORB candle fetch failed for {}: {}", inst.symbol(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Computes Opening Range Breakout for 15m, 30m and 1h timeframes
     * using today's 15m candles already fetched in the MTF stack.
     * Works for ANY symbol — no live stream required.
     *
     * @param fifteenMinCandles the 15m candle series from the MTF computation
     * @param date              the analysis date (today for LIVE/CONSOLIDATED)
     * @param currentPrice      current LTP to determine breakout (may be null)
     */
    private OrbDto computeOrb(List<Candle> fifteenMinCandles, LocalDate date,
                               BigDecimal currentPrice) {
        if (fifteenMinCandles == null || fifteenMinCandles.isEmpty()) return null;
        try {
            // Filter to the analysis date's candles, sorted by time
            List<Candle> today = fifteenMinCandles.stream()
                    .filter(c -> c.timestamp().atZone(IST).toLocalDate().equals(date))
                    .sorted(Comparator.comparing(Candle::timestamp))
                    .toList();
            if (today.isEmpty()) return null;

            List<OrbTimeframeDto> tfs = new ArrayList<>();

            // 15m ORB — first 1 candle (9:15–9:30)
            orbTf(tfs, 15, 1, today, currentPrice);

            // 30m ORB — first 2 candles (9:15–9:45)
            orbTf(tfs, 30, 2, today, currentPrice);

            // 1h ORB — first 4 candles (9:15–10:15)
            orbTf(tfs, 60, 4, today, currentPrice);

            return new OrbDto(tfs);
        } catch (Exception e) {
            log.warn("[Analysis] ORB computation failed: {}", e.getMessage());
            return null;
        }
    }

    private void orbTf(List<OrbTimeframeDto> list, int minutes, int candlesNeeded,
                       List<Candle> todayCandles, BigDecimal price) {
        List<Candle> slice = todayCandles.subList(0, Math.min(candlesNeeded, todayCandles.size()));
        boolean forming = slice.size() < candlesNeeded;

        BigDecimal orHigh = slice.stream().map(Candle::high).max(BigDecimal::compareTo).orElse(null);
        BigDecimal orLow  = slice.stream().map(Candle::low).min(BigDecimal::compareTo).orElse(null);
        BigDecimal orRange = (orHigh != null && orLow != null)
                ? orHigh.subtract(orLow).setScale(2, RoundingMode.HALF_UP) : null;

        String signal;
        if (forming) {
            signal = "FORMING";
        } else if (price != null && orHigh != null && orLow != null) {
            if (price.compareTo(orHigh) > 0)      signal = "BUY";
            else if (price.compareTo(orLow) < 0)  signal = "SHORT";
            else                                   signal = "WAITING";
        } else {
            signal = "WAITING";
        }
        list.add(new OrbTimeframeDto(minutes, signal, orHigh, orLow, orRange));
    }

    /**
     * Returns true if the interval is longer than ONE_HOUR.
     * Used to select a sensible breakdown interval — we cap at ONE_HOUR
     * to avoid returning a single row (e.g. ONE_DAY breakdown = 1 row, not useful).
     */
    private static boolean isHigherThanHour(Interval interval) {
        return switch (interval) {
            case TWO_HOUR, THREE_HOUR, FOUR_HOUR, ONE_DAY -> true;
            default -> false;
        };
    }

    private static int expectedBarsForInterval(Interval interval) {
        return switch (interval) {
            case FIVE_MINUTE    -> 75;
            case FIFTEEN_MINUTE -> 26;
            case THIRTY_MINUTE  -> 13;
            case ONE_HOUR       -> 7;
            case ONE_DAY        -> 1;
            default             -> 7;
        };
    }
}
