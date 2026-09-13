package com.angle.trading.bias;

import com.angle.trading.analyst.AnalystService;
import com.angle.trading.analyst.model.AnalystReport;
import com.angle.trading.bias.model.BiasSheet;
import com.angle.trading.bias.model.ConsolidatedScore;
import com.angle.trading.bias.model.MarketContextSection;
import com.angle.trading.bias.model.StructureSection;
import com.angle.trading.bias.model.TimeframeBias;
import com.angle.trading.bias.model.TrendFilters;
import com.angle.trading.bias.model.ZonesSection;
import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.config.BiasProperties;
import com.angle.trading.indicator.AverageDirectionalIndex;
import com.angle.trading.indicator.AverageTrueRange;
import com.angle.trading.indicator.ExponentialMovingAverage;
import com.angle.trading.indicator.MACD;
import com.angle.trading.indicator.RelativeStrengthIndex;
import com.angle.trading.indicator.VwapIndicator;
import com.angle.trading.marketdata.MarketDataService;
import com.angle.trading.marketstructure.MarketContextBuilder;
import com.angle.trading.marketstructure.model.LiquidityLevel;
import com.angle.trading.marketstructure.model.MarketContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles a full {@link BiasSheet} for one instrument.
 *
 * Data pipeline per instrument:
 *   1. Fetch candles for each configured timeframe
 *   2. Compute indicators on the intraday timeframe (EMA, VWAP, ADX, ATR, RSI, MACD)
 *   3. Ask AnalystService for the bias on each timeframe
 *   4. Ask MarketContextBuilder for SMC structure + zones
 *   5. Score everything → ConsolidatedScore
 *   6. Return BiasSheet snapshot
 *
 * All external calls (Angel candles, Analyst) can throw — we log and
 * insert nulls into the sheet so the UI degrades gracefully.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BiasSheetService {

    private static final int NEARBY_ZONES = 3;
    private static final int RECENT_SWEEPS_WINDOW = 20;
    private static final MathContext MC = MathContext.DECIMAL64;

    private final BiasProperties biasProperties;
    private final MarketDataService marketDataService;
    private final AnalystService analystService;
    private final MarketContextBuilder marketContextBuilder;

    public BiasSheet build(BiasProperties.Instrument cfg) {
        Instant asOf = Instant.now();
        LocalDate to   = LocalDate.now();
        LocalDate from = to.minusDays(biasProperties.getLookbackDays());

        // Intraday candles = indicator + structure base
        List<Candle> intraday = safeFetchCandles(cfg, cfg.getIntradayInterval(), from, to);

        MarketContextSection market   = buildMarketContext(intraday);
        List<TimeframeBias>  multiTf  = buildMultiTfBias(cfg, from, to);
        TrendFilters         trend    = buildTrendFilters(intraday);
        MarketContext        smcCtx   = intraday.isEmpty() ? null : marketContextBuilder.build(intraday);
        StructureSection     structure = buildStructure(smcCtx);
        ZonesSection         zones    = buildZones(smcCtx);
        ConsolidatedScore    score    = buildConsolidatedScore(multiTf, trend, structure);

        return new BiasSheet(
                asOf, cfg.getSymbol(), cfg.getSymbolToken(), cfg.getExchange().name(),
                market, multiTf, trend, structure, zones, score
        );
    }

    // ---------- individual sections ----------

    private MarketContextSection buildMarketContext(List<Candle> candles) {
        if (candles.isEmpty()) {
            return new MarketContextSection(null, null, null, null, null, null, null, null, null, null);
        }
        Candle latest = candles.get(candles.size() - 1);
        BigDecimal previousClose = candles.size() >= 2 ? candles.get(candles.size() - 2).close() : null;

        // "Previous day" = 24h before latest bar (approximate; fine for MVP)
        BigDecimal pdh = null, pdl = null;
        Instant cutoff24 = latest.timestamp().minusSeconds(24 * 3600);
        Instant cutoff48 = latest.timestamp().minusSeconds(48 * 3600);
        BigDecimal openToday = null, dayHigh = null, dayLow = null;
        Instant startOfDay = latest.timestamp().minusSeconds(6 * 3600 + 30 * 60);  // rough IST 9AM cutoff
        for (Candle c : candles) {
            if (c.timestamp().isAfter(cutoff24)) {
                if (openToday == null) openToday = c.open();
                dayHigh = (dayHigh == null || c.high().compareTo(dayHigh) > 0) ? c.high() : dayHigh;
                dayLow  = (dayLow  == null || c.low().compareTo(dayLow)  < 0) ? c.low()  : dayLow;
            } else if (c.timestamp().isAfter(cutoff48)) {
                pdh = (pdh == null || c.high().compareTo(pdh) > 0) ? c.high() : pdh;
                pdl = (pdl == null || c.low().compareTo(pdl)  < 0) ? c.low()  : pdl;
            }
        }
        BigDecimal gap = (openToday != null && previousClose != null)
                ? openToday.subtract(previousClose) : null;
        BigDecimal gapPct = (gap != null && previousClose != null && previousClose.signum() > 0)
                ? gap.divide(previousClose, MC).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : null;
        return new MarketContextSection(latest.close(), previousClose, pdh, pdl, openToday,
                dayHigh, dayLow, gap, gapPct, /* indiaVix */ null);
    }

    private List<TimeframeBias> buildMultiTfBias(BiasProperties.Instrument cfg, LocalDate from, LocalDate to) {
        List<TimeframeBias> out = new ArrayList<>(biasProperties.getTimeframes().size());
        for (Interval tf : biasProperties.getTimeframes()) {
            try {
                AnalystReport r = analystService.analyseLive(cfg.getBroker(), cfg.getExchange(),
                        cfg.getSymbolToken(), tf, from, to);
                out.add(new TimeframeBias(tf, r.market().bias(), reasonFromAnalyst(r), r.consensus().confidence()));
            } catch (Exception e) {
                log.debug("Analyst call failed for tf={}: {}", tf, e.getMessage());
                out.add(new TimeframeBias(tf, null, "unavailable: " + e.getMessage(), null));
            }
        }
        return out;
    }

    private static String reasonFromAnalyst(AnalystReport r) {
        if (r == null || r.recommendation() == null) return null;
        return r.recommendation().rationale();
    }

    private TrendFilters buildTrendFilters(List<Candle> candles) {
        if (candles.isEmpty()) {
            return new TrendFilters(null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null);
        }
        int i = candles.size() - 1;
        BigDecimal price = candles.get(i).close();

        BigDecimal ema20  = last(new ExponentialMovingAverage(20).compute(candles));
        BigDecimal ema50  = last(new ExponentialMovingAverage(50).compute(candles));
        BigDecimal ema200 = last(new ExponentialMovingAverage(200).compute(candles));
        BigDecimal vwap   = last(new VwapIndicator().compute(candles));
        BigDecimal rsi    = last(new RelativeStrengthIndex(14).compute(candles));
        BigDecimal adx    = last(new AverageDirectionalIndex(14).compute(candles));
        BigDecimal atr    = last(new AverageTrueRange(14).compute(candles));
        MACD.MacdValue macd = last(new MACD(12, 26, 9).computeSeries(candles));

        Boolean ema20AboveEma50 = (ema20 != null && ema50 != null) ? ema20.compareTo(ema50) > 0 : null;
        Boolean priceAboveEma200 = (ema200 != null) ? price.compareTo(ema200) > 0 : null;
        Boolean priceAboveVwap = (vwap != null) ? price.compareTo(vwap) > 0 : null;

        String adxStrength = null;
        if (adx != null) {
            int i2 = adx.intValue();
            if      (i2 < 20) adxStrength = "WEAK";
            else if (i2 <= 25) adxStrength = "DEVELOPING";
            else if (i2 <= 40) adxStrength = "STRONG";
            else               adxStrength = "VERY_STRONG";
        }

        BigDecimal atrPct = (atr != null && price.signum() > 0)
                ? atr.divide(price, MC).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : null;

        Boolean rsiBullish = (rsi != null) ? rsi.compareTo(BigDecimal.valueOf(50)) > 0 : null;
        Boolean macdBullish = (macd != null && macd.histogram() != null) ? macd.histogram().signum() > 0 : null;

        return new TrendFilters(
                ema20, ema50, ema200, ema20AboveEma50, priceAboveEma200,
                vwap, priceAboveVwap,
                adx, adxStrength,
                atr, atrPct,
                rsi, rsiBullish,
                macd == null ? null : macd.macd(),
                macd == null ? null : macd.signal(),
                macd == null ? null : macd.histogram(),
                macdBullish
        );
    }

    private StructureSection buildStructure(MarketContext ctx) {
        if (ctx == null) return new StructureSection(null, null, null, null);
        return new StructureSection(ctx.bias(), ctx.lastEvent(), ctx.latestSwingHigh(), ctx.latestSwingLow());
    }

    private ZonesSection buildZones(MarketContext ctx) {
        if (ctx == null) return new ZonesSection(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        BigDecimal price = ctx.currentPrice();
        return new ZonesSection(
                ctx.activeBullishOBs().stream().limit(NEARBY_ZONES).toList(),
                ctx.activeBearishOBs().stream().limit(NEARBY_ZONES).toList(),
                ctx.activeBullishFvgs().stream().limit(NEARBY_ZONES).toList(),
                ctx.activeBearishFvgs().stream().limit(NEARBY_ZONES).toList(),
                ctx.unsweptBSL().stream()
                        .filter(l -> l.price().compareTo(price) > 0)
                        .sorted(Comparator.comparing(LiquidityLevel::price))
                        .limit(NEARBY_ZONES).toList(),
                ctx.unsweptSSL().stream()
                        .filter(l -> l.price().compareTo(price) < 0)
                        .sorted(Comparator.comparing(LiquidityLevel::price).reversed())
                        .limit(NEARBY_ZONES).toList(),
                ctx.sweepsWithin(RECENT_SWEEPS_WINDOW)
        );
    }

    private ConsolidatedScore buildConsolidatedScore(List<TimeframeBias> multiTf,
                                                     TrendFilters trend,
                                                     StructureSection structure) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("MultiTF",   scoreMultiTf(multiTf));
        scores.put("EMA",       toScore(trend.ema20AboveEma50()));
        scores.put("VWAP",      toScore(trend.priceAboveVwap()));
        scores.put("ADX",       scoreAdx(trend));
        scores.put("Structure", scoreStructure(structure));
        scores.put("RSI",       toScore(trend.rsiBullish()));
        scores.put("MACD",      toScore(trend.macdBullish()));

        int total = scores.values().stream().mapToInt(Integer::intValue).sum();
        int maxPossible = scores.size();  // each contributes at most +1 magnitude

        String strength;
        int abs = Math.abs(total);
        BiasProperties.Scoring t = biasProperties.getScoring();
        if      (abs >= t.getStrongThreshold())   strength = "STRONG";
        else if (abs >= t.getModerateThreshold()) strength = "MODERATE";
        else if (abs >= t.getWeakThreshold())     strength = "WEAK";
        else                                       strength = "NONE";

        String recommendation;
        if (strength.equals("NONE") || strength.equals("WEAK")) recommendation = "NO-TRADE";
        else if (total > 0)                                     recommendation = "LONG-ONLY";
        else                                                    recommendation = "SHORT-ONLY";

        double confidence = maxPossible == 0 ? 0.0 : (100.0 * abs / maxPossible);
        return new ConsolidatedScore(scores, total, strength, recommendation,
                Math.round(confidence * 10) / 10.0);
    }

    private static int scoreMultiTf(List<TimeframeBias> mt) {
        int longs = 0, shorts = 0;
        for (TimeframeBias t : mt) {
            if (t.bias() == null) continue;
            switch (t.bias()) { case BULLISH -> longs++; case BEARISH -> shorts++; }
        }
        if (longs > shorts && longs >= mt.size() * 0.5) return 1;
        if (shorts > longs && shorts >= mt.size() * 0.5) return -1;
        return 0;
    }

    private static int scoreAdx(TrendFilters t) {
        if (t.adxStrength() == null) return 0;
        // ADX only tells strength — direction from EMA
        if (!"STRONG".equals(t.adxStrength()) && !"VERY_STRONG".equals(t.adxStrength())) return 0;
        if (t.ema20AboveEma50() == null) return 0;
        return t.ema20AboveEma50() ? 1 : -1;
    }

    private static int scoreStructure(StructureSection s) {
        if (s.bias() == null) return 0;
        return switch (s.bias()) { case BULLISH -> 1; case BEARISH -> -1; };
    }

    private static int toScore(Boolean bullish) {
        return bullish == null ? 0 : (bullish ? 1 : -1);
    }

    // ---------- helpers ----------

    private List<Candle> safeFetchCandles(BiasProperties.Instrument cfg, Interval interval,
                                          LocalDate from, LocalDate to) {
        try {
            return marketDataService.getCandles(cfg.getBroker(), cfg.getExchange(),
                    cfg.getSymbolToken(), interval, from, to);
        } catch (Exception e) {
            log.warn("Candle fetch failed for {} @ {}: {}", cfg.getSymbolToken(), interval, e.getMessage());
            return List.of();
        }
    }

    private static <T> T last(List<T> series) {
        return series == null || series.isEmpty() ? null : series.get(series.size() - 1);
    }
}
