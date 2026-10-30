package com.angle.trading.hedge;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.indicator.AverageDirectionalIndex;
import com.angle.trading.indicator.ExponentialMovingAverage;
import com.angle.trading.indicator.RelativeStrengthIndex;
import com.angle.trading.marketdata.MarketDataService;
import com.angle.trading.persistence.BiasInstrumentEntity;
import com.angle.trading.persistence.BiasInstrumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HedgeComparisonAnalyzer {

    private static final int LOOKBACK_DAYS = 120;
    private static final int DEFAULT_DAYS_TO_EXPIRY = 30;

    // 7 points for the scenario table
    private static final double[] TABLE_MULTS  = {0.80, 0.90, 0.95, 1.00, 1.05, 1.10, 1.20};
    private static final String[] TABLE_LABELS = {"-20%", "-10%", "-5%", "0%", "+5%", "+10%", "+20%"};

    // 25 points for the payoff chart (-30% to +30% in 2.5% steps)
    private static final double[] CHART_MULTS;
    private static final String[] CHART_LABELS;
    static {
        CHART_MULTS  = new double[25];
        CHART_LABELS = new String[25];
        for (int i = 0; i < 25; i++) {
            double pct = -0.30 + i * 0.025;
            CHART_MULTS[i]  = 1.0 + pct;
            CHART_LABELS[i] = (pct >= 0 ? "+" : "") + String.format("%.0f%%", pct * 100);
        }
    }

    // [PROTECTIVE_MARRIED_PUT, COVERED_CALL, COLLAR, FUTURES_HEDGE]
    private static final Map<MarketRegime, int[]> REGIME_SCORES;
    static {
        REGIME_SCORES = new EnumMap<>(MarketRegime.class);
        REGIME_SCORES.put(MarketRegime.STRONG_BULLISH,  new int[]{5, 4, 3, 1});
        REGIME_SCORES.put(MarketRegime.MILD_BULLISH,    new int[]{4, 5, 4, 2});
        REGIME_SCORES.put(MarketRegime.SIDEWAYS,        new int[]{2, 5, 4, 3});
        REGIME_SCORES.put(MarketRegime.MILD_BEARISH,    new int[]{3, 2, 5, 4});
        REGIME_SCORES.put(MarketRegime.STRONG_BEARISH,  new int[]{2, 1, 4, 5});
    }

    private final BiasInstrumentRepository instrumentRepo;
    private final MarketDataService         marketDataService;

    public HedgeComparisonResult compare(HedgeComparisonRequest req) {
        BiasInstrumentEntity instrument = instrumentRepo.findBySymbolToken(req.symbolToken())
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + req.symbolToken()));

        List<Candle> candles = marketDataService.getCandles(
                instrument.getBroker(),
                Exchange.valueOf(instrument.getExchange()),
                req.symbolToken(),
                Interval.ONE_DAY,
                LocalDate.now().minusDays(LOOKBACK_DAYS),
                LocalDate.now());

        if (candles.isEmpty()) {
            throw new IllegalStateException("No daily candle data for " + instrument.getSymbol());
        }

        int dte = req.daysToExpiry() != null && req.daysToExpiry() > 0
                ? req.daysToExpiry() : DEFAULT_DAYS_TO_EXPIRY;

        IndicatorSnapshot snapshot    = computeSnapshot(candles);
        double            hv30        = computeHv30(candles);
        String            hv30Assess  = assessPremium(hv30, req.putPremium(), req.entryPrice(), dte);
        int               lotSize     = instrument.getLotSize() > 0 ? instrument.getLotSize() : 1;

        int[] scores = REGIME_SCORES.get(snapshot.regime());
        List<HedgeStrategyAnalysis> raw = List.of(
                buildProtectivePut(req, scores[0], candles, hv30, dte),
                buildCoveredCall  (req, scores[1], candles, hv30, dte),
                buildCollar       (req, scores[2], candles, hv30, dte),
                buildFuturesHedge (req, scores[3], candles, hv30, dte, lotSize)
        );

        int maxScore = raw.stream().mapToInt(HedgeStrategyAnalysis::regimeFitScore).max().orElse(0);
        List<HedgeStrategyAnalysis> analyses = raw.stream()
                .map(a -> a.regimeFitScore() == maxScore ? withRecommended(a, true) : a)
                .collect(Collectors.toList());

        HedgeStrategyType winner = analyses.stream()
                .filter(HedgeStrategyAnalysis::recommended)
                .map(HedgeStrategyAnalysis::strategy).findFirst()
                .orElse(HedgeStrategyType.PROTECTIVE_MARRIED_PUT);

        String winnerRationale = analyses.stream()
                .filter(HedgeStrategyAnalysis::recommended)
                .map(HedgeStrategyAnalysis::rationale).findFirst().orElse("");

        return new HedgeComparisonResult(
                instrument.getSymbol(), req.entryPrice(), req.quantity(),
                lotSize, (double) req.quantity() / lotSize,
                snapshot, analyses,
                winner, winnerRationale,
                LocalDateTime.now(),
                hv30, hv30Assess,
                computeExpiryDates(3),
                computeZeroCostCallStrike(req.entryPrice(), req.putStrike()),
                dte);
    }

    // ── Indicator snapshot ────────────────────────────────────────────────────

    private IndicatorSnapshot computeSnapshot(List<Candle> candles) {
        BigDecimal price = candles.get(candles.size() - 1).close();
        BigDecimal rsi   = lastNonNull(new RelativeStrengthIndex(14).compute(candles));
        BigDecimal adx   = lastNonNull(new AverageDirectionalIndex(14).compute(candles));
        BigDecimal ema21 = lastNonNull(new ExponentialMovingAverage(21).compute(candles));

        int rsiScore; String rsiSignal;
        if (rsi == null) {
            rsiScore = 0; rsiSignal = "Insufficient data";
        } else if (inRange(rsi, 40, 60)) {
            rsiScore = 2; rsiSignal = "Neutral — ideal entry zone";
        } else if (inRange(rsi, 30, 40) || inRange(rsi, 60, 70)) {
            rsiScore = 1; rsiSignal = rsi.compareTo(BigDecimal.valueOf(50)) < 0 ? "Mild oversold" : "Mild overbought";
        } else {
            rsiScore = 0; rsiSignal = rsi.compareTo(BigDecimal.valueOf(70)) > 0 ? "Overbought — wait" : "Deep oversold — caution";
        }

        int adxScore; String adxSignal;
        if (adx == null) {
            adxScore = 0; adxSignal = "Insufficient data";
        } else if (adx.compareTo(BigDecimal.valueOf(25)) >= 0) {
            adxScore = 2; adxSignal = "Strong trend";
        } else if (adx.compareTo(BigDecimal.valueOf(20)) >= 0) {
            adxScore = 1; adxSignal = "Developing trend";
        } else {
            adxScore = 0; adxSignal = "Ranging / choppy";
        }

        int ema21Score; String ema21Signal;
        if (ema21 == null) {
            ema21Score = 0; ema21Signal = "Insufficient data";
        } else if (price.compareTo(ema21) >= 0) {
            ema21Score = 2; ema21Signal = "Bullish — price above EMA-21";
        } else {
            ema21Score = 0; ema21Signal = "Bearish — price below EMA-21";
        }

        int totalScore = rsiScore + adxScore + ema21Score;

        boolean bullish     = ema21Score == 2;
        boolean strongTrend = adxScore == 2;
        boolean midTrend    = adxScore == 1;
        boolean rsiUp       = rsi != null && rsi.compareTo(BigDecimal.valueOf(50)) >= 0;
        boolean rsiDown     = rsi != null && rsi.compareTo(BigDecimal.valueOf(40)) < 0;

        MarketRegime regime;
        if (bullish && strongTrend && rsiUp)            regime = MarketRegime.STRONG_BULLISH;
        else if (bullish && (strongTrend || midTrend))  regime = MarketRegime.MILD_BULLISH;
        else if (!bullish && strongTrend && rsiDown)    regime = MarketRegime.STRONG_BEARISH;
        else if (!bullish && rsiDown)                   regime = MarketRegime.MILD_BEARISH;
        else                                            regime = MarketRegime.SIDEWAYS;

        return new IndicatorSnapshot(
                rsi, rsiScore, rsiSignal,
                adx, adxScore, adxSignal,
                ema21, price, ema21Score, ema21Signal,
                totalScore, regime,
                List.of(
                        "RSI(14): " + fmt(rsi, 1) + " — " + rsiSignal,
                        "ADX(14): " + fmt(adx, 1) + " — " + adxSignal,
                        "EMA-21: " + fmt(ema21, 2) + " (price " + fmt(price, 2) + ") — " + ema21Signal));
    }

    // ── Historical Volatility ────────────────────────────────────────────────

    private double computeHv30(List<Candle> candles) {
        int n = Math.min(31, candles.size());
        if (n < 5) return 0.20; // fallback
        List<Candle> window = candles.subList(candles.size() - n, candles.size());
        double[] returns = new double[window.size() - 1];
        for (int i = 1; i < window.size(); i++) {
            double prev = window.get(i - 1).close().doubleValue();
            double curr = window.get(i).close().doubleValue();
            returns[i - 1] = Math.log(curr / prev);
        }
        double mean     = Arrays.stream(returns).average().orElse(0);
        double variance = Arrays.stream(returns).map(r -> (r - mean) * (r - mean)).sum()
                / Math.max(returns.length - 1, 1);
        return Math.sqrt(variance) * Math.sqrt(252); // annualised
    }

    // premium is CHEAP if actual < 80% of BS-estimated, EXPENSIVE if > 120%
    private String assessPremium(double hv30, BigDecimal premium, BigDecimal spot, int dte) {
        if (hv30 <= 0 || premium == null || spot == null) return "FAIR";
        double expected = spot.doubleValue() * hv30 * Math.sqrt(dte / 252.0);
        double actual   = premium.doubleValue();
        if (actual < expected * 0.80) return "CHEAP";
        if (actual > expected * 1.20) return "EXPENSIVE";
        return "FAIR";
    }

    // ── Probability of profit ────────────────────────────────────────────────

    private double computeProbabilityOfProfit(List<Candle> candles,
                                               BigDecimal breakeven, BigDecimal entry, int dte) {
        double threshold = (breakeven.doubleValue() - entry.doubleValue()) / entry.doubleValue();
        int n = candles.size(), count = 0, total = 0;
        for (int i = dte; i < n; i++) {
            double start = candles.get(i - dte).close().doubleValue();
            double end   = candles.get(i).close().doubleValue();
            double ret   = (end - start) / start;
            if (ret > threshold) count++;
            total++;
        }
        return total > 10 ? (double) count / total : 0.50;
    }

    // ── Days to breakeven (random-walk estimate) ──────────────────────────────

    private int estimateDaysToBreakeven(double hv30, BigDecimal breakeven, BigDecimal entry) {
        double dailyVol = hv30 / Math.sqrt(252);
        if (dailyVol <= 0) return 30;
        double pctMove = Math.abs(breakeven.doubleValue() - entry.doubleValue()) / entry.doubleValue();
        return (int) Math.ceil(Math.pow(pctMove / dailyVol, 2));
    }

    // ── Time decay (stock stays at entry price) ───────────────────────────────

    private List<HedgeTimeDecayRow> computeTimeDecay(HedgeStrategyType type,
                                                      HedgeComparisonRequest req, int dte) {
        int[] days = { dte, (dte * 2) / 3, dte / 3, 0 };
        String[] labels = {
            "Today (" + dte + "d)",
            (dte * 2 / 3) + "d remaining",
            (dte / 3) + "d remaining",
            "At Expiry"
        };
        List<HedgeTimeDecayRow> rows = new ArrayList<>();
        for (int i = 0; i < days.length; i++) {
            rows.add(new HedgeTimeDecayRow(labels[i], days[i],
                    decayPnl(type, req, days[i], dte)));
        }
        return rows;
    }

    // Simplified sqrt-of-time theta decay model, stock assumed at entry
    private BigDecimal decayPnl(HedgeStrategyType type, HedgeComparisonRequest req,
                                 int daysLeft, int dte) {
        double T     = dte > 0 ? dte : 1;
        double t     = daysLeft;
        double sqrtT = Math.sqrt(t / T); // 1 at start, 0 at expiry
        double qty   = req.quantity();

        return switch (type) {
            case PROTECTIVE_MARRIED_PUT -> {
                // intrinsic at entry (OTM put: intrinsic = 0 if entry > strike)
                double intr = Math.max(req.putStrike().doubleValue() - req.entryPrice().doubleValue(), 0);
                double tv   = req.putPremium().doubleValue() - intr; // time value
                double optVal = intr + tv * sqrtT;
                yield bd((optVal - req.putPremium().doubleValue()) * qty);
            }
            case COVERED_CALL -> {
                // We sold call; liability decays — we profit as time passes
                double intr = Math.max(req.entryPrice().doubleValue() - req.callStrike().doubleValue(), 0);
                double tv   = req.callPremium().doubleValue() - intr;
                double remaining = intr + tv * sqrtT; // remaining obligation
                yield bd((req.callPremium().doubleValue() - remaining) * qty);
            }
            case COLLAR -> {
                // Put leg (we own)
                double pIntr = Math.max(req.putStrike().doubleValue() - req.entryPrice().doubleValue(), 0);
                double ptv   = req.putPremium().doubleValue() - pIntr;
                double putVal = pIntr + ptv * sqrtT;
                // Call leg (we sold)
                double cIntr = Math.max(req.entryPrice().doubleValue() - req.callStrike().doubleValue(), 0);
                double ctv   = req.callPremium().doubleValue() - cIntr;
                double callObligation = cIntr + ctv * sqrtT;
                double netPnl = (putVal - req.putPremium().doubleValue()
                        + req.callPremium().doubleValue() - callObligation) * qty;
                yield bd(netPnl);
            }
            case FUTURES_HEDGE -> {
                // No time decay — futures converge linearly to spot
                double carry = (req.futurePrice().doubleValue() - req.entryPrice().doubleValue());
                double remaining = carry * (t / T); // carry reduces as time passes
                yield bd(remaining * qty);
            }
        };
    }

    // ── Expiry dates (NSE monthly = last Thursday of each month) ─────────────

    private List<LocalDate> computeExpiryDates(int count) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate today = LocalDate.now();
        YearMonth month = YearMonth.now();
        while (dates.size() < count) {
            LocalDate lastThursday = month.atEndOfMonth()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.THURSDAY));
            if (lastThursday.isAfter(today)) dates.add(lastThursday);
            month = month.plusMonths(1);
        }
        return dates;
    }

    // ── Zero-cost collar call strike (symmetric OTM) ─────────────────────────

    private BigDecimal computeZeroCostCallStrike(BigDecimal entry, BigDecimal putStrike) {
        double raw     = 2.0 * entry.doubleValue() - putStrike.doubleValue();
        double rounded = Math.round(raw / 5.0) * 5.0;
        return BigDecimal.valueOf(rounded);
    }

    // ── Strategy builders ─────────────────────────────────────────────────────

    private HedgeStrategyAnalysis buildProtectivePut(HedgeComparisonRequest req, int score,
                                                      List<Candle> candles, double hv30, int dte) {
        BigDecimal entry  = req.entryPrice();
        BigDecimal strike = req.putStrike();
        BigDecimal prem   = req.putPremium();
        BigDecimal qty    = BigDecimal.valueOf(req.quantity());

        BigDecimal totalCost = prem.multiply(qty);
        BigDecimal breakeven = entry.add(prem);
        BigDecimal maxLoss   = entry.subtract(strike).add(prem).multiply(qty);

        Function<BigDecimal, HedgeScenario> fn = price -> {
            BigDecimal stock  = price.subtract(entry).multiply(qty);
            BigDecimal option = strike.subtract(price).max(BigDecimal.ZERO).multiply(qty);
            return new HedgeScenario("", price, stock, option, BigDecimal.ZERO,
                    stock.add(option).subtract(totalCost));
        };

        return new HedgeStrategyAnalysis(
                HedgeStrategyType.PROTECTIVE_MARRIED_PUT,
                HedgeStrategyType.PROTECTIVE_MARRIED_PUT.getDisplayName(),
                HedgeStrategyType.PROTECTIVE_MARRIED_PUT.getPosition(),
                prem.negate(), totalCost, breakeven, null, maxLoss,
                buildScenarios(entry, TABLE_MULTS, TABLE_LABELS, fn),
                buildScenarios(entry, CHART_MULTS, CHART_LABELS, fn),
                score, false,
                "Best in bullish markets — unlimited upside retained, downside capped at PUT strike.",
                "Married PUT = same P&L; difference is timing (both legs opened on day 1).",
                computeProbabilityOfProfit(candles, breakeven, entry, dte),
                estimateDaysToBreakeven(hv30, breakeven, entry),
                computeTimeDecay(HedgeStrategyType.PROTECTIVE_MARRIED_PUT, req, dte),
                riskReward(null, maxLoss),
                breakevenPct(breakeven, entry),
                theta(prem.doubleValue(), 0, req.quantity(), dte));
    }

    private HedgeStrategyAnalysis buildCoveredCall(HedgeComparisonRequest req, int score,
                                                    List<Candle> candles, double hv30, int dte) {
        BigDecimal entry  = req.entryPrice();
        BigDecimal strike = req.callStrike();
        BigDecimal prem   = req.callPremium();
        BigDecimal qty    = BigDecimal.valueOf(req.quantity());

        BigDecimal receipt   = prem.multiply(qty);
        BigDecimal breakeven = entry.subtract(prem);
        BigDecimal maxGain   = strike.subtract(entry).add(prem).multiply(qty);
        BigDecimal maxLoss   = entry.subtract(prem).multiply(qty);

        Function<BigDecimal, HedgeScenario> fn = price -> {
            BigDecimal stock  = price.subtract(entry).multiply(qty);
            BigDecimal option = price.subtract(strike).max(BigDecimal.ZERO).negate().multiply(qty);
            return new HedgeScenario("", price, stock, option, BigDecimal.ZERO,
                    stock.add(option).add(receipt));
        };

        return new HedgeStrategyAnalysis(
                HedgeStrategyType.COVERED_CALL,
                HedgeStrategyType.COVERED_CALL.getDisplayName(),
                HedgeStrategyType.COVERED_CALL.getPosition(),
                prem, receipt.negate(), breakeven, maxGain, maxLoss,
                buildScenarios(entry, TABLE_MULTS, TABLE_LABELS, fn),
                buildScenarios(entry, CHART_MULTS, CHART_LABELS, fn),
                score, false,
                "Best in sideways/mild bullish — earns premium income; upside capped at CALL strike.",
                "Downside protection = premium received only. Large falls are only partially cushioned.",
                computeProbabilityOfProfit(candles, breakeven, entry, dte),
                estimateDaysToBreakeven(hv30, breakeven, entry),
                computeTimeDecay(HedgeStrategyType.COVERED_CALL, req, dte),
                riskReward(maxGain, maxLoss),
                breakevenPct(breakeven, entry),
                theta(0, prem.doubleValue(), req.quantity(), dte));
    }

    private HedgeStrategyAnalysis buildCollar(HedgeComparisonRequest req, int score,
                                               List<Candle> candles, double hv30, int dte) {
        BigDecimal entry     = req.entryPrice();
        BigDecimal putStrike = req.putStrike();
        BigDecimal callStrike= req.callStrike();
        BigDecimal putPrem   = req.putPremium();
        BigDecimal callPrem  = req.callPremium();
        BigDecimal qty       = BigDecimal.valueOf(req.quantity());

        BigDecimal net       = callPrem.subtract(putPrem); // positive = credit
        BigDecimal breakeven = entry.subtract(net);
        BigDecimal maxGain   = callStrike.subtract(entry).add(net).multiply(qty);
        BigDecimal maxLoss   = entry.subtract(putStrike).subtract(net).multiply(qty);

        Function<BigDecimal, HedgeScenario> fn = price -> {
            BigDecimal stock   = price.subtract(entry).multiply(qty);
            BigDecimal putPay  = putStrike.subtract(price).max(BigDecimal.ZERO).multiply(qty);
            BigDecimal callPay = price.subtract(callStrike).max(BigDecimal.ZERO).negate().multiply(qty);
            return new HedgeScenario("", price, stock, putPay.add(callPay), BigDecimal.ZERO,
                    stock.add(putPay).add(callPay).add(net.multiply(qty)));
        };

        return new HedgeStrategyAnalysis(
                HedgeStrategyType.COLLAR,
                HedgeStrategyType.COLLAR.getDisplayName(),
                HedgeStrategyType.COLLAR.getPosition(),
                net, net.negate().multiply(qty), breakeven, maxGain, maxLoss,
                buildScenarios(entry, TABLE_MULTS, TABLE_LABELS, fn),
                buildScenarios(entry, CHART_MULTS, CHART_LABELS, fn),
                score, false,
                "Best in uncertain markets — both sides capped, often near-zero net cost.",
                "Selling the CALL funds the PUT. Aim for zero-cost: callPremium ≈ putPremium.",
                computeProbabilityOfProfit(candles, breakeven, entry, dte),
                estimateDaysToBreakeven(hv30, breakeven, entry),
                computeTimeDecay(HedgeStrategyType.COLLAR, req, dte),
                riskReward(maxGain, maxLoss),
                breakevenPct(breakeven, entry),
                theta(putPrem.doubleValue(), callPrem.doubleValue(), req.quantity(), dte));
    }

    private HedgeStrategyAnalysis buildFuturesHedge(HedgeComparisonRequest req, int score,
                                                     List<Candle> candles, double hv30, int dte,
                                                     int lotSize) {
        BigDecimal entry    = req.entryPrice();
        BigDecimal futPrice = req.futurePrice();
        int        qty      = req.quantity();
        int        lots     = Math.max(1, (int) Math.round((double) qty / lotSize));
        int        effQty   = lots * lotSize;

        String hedgeNote = effQty == qty
                ? "Perfect hedge: " + lots + " lot(s) covers all " + qty + " shares."
                : effQty > qty
                ? "Over-hedged: " + lots + " lot(s) = " + effQty + " shares vs your " + qty + "."
                : "Under-hedged: " + lots + " lot(s) = " + effQty + " of your " + qty + " shares.";

        BigDecimal carry = futPrice.subtract(entry).multiply(BigDecimal.valueOf(effQty));
        BigDecimal beqn  = entry;

        final int eff = effQty;
        Function<BigDecimal, HedgeScenario> fn = price -> {
            BigDecimal stock  = price.subtract(entry).multiply(BigDecimal.valueOf(qty));
            BigDecimal futPnl = futPrice.subtract(price).multiply(BigDecimal.valueOf(eff));
            return new HedgeScenario("", price, stock, BigDecimal.ZERO, futPnl, stock.add(futPnl));
        };

        BigDecimal maxGL = carry.abs().max(BigDecimal.ONE);
        return new HedgeStrategyAnalysis(
                HedgeStrategyType.FUTURES_HEDGE,
                HedgeStrategyType.FUTURES_HEDGE.getDisplayName(),
                HedgeStrategyType.FUTURES_HEDGE.getPosition(),
                BigDecimal.ZERO, BigDecimal.ZERO, beqn, maxGL, maxGL,
                buildScenarios(entry, TABLE_MULTS, TABLE_LABELS, fn),
                buildScenarios(entry, CHART_MULTS, CHART_LABELS, fn),
                score, false,
                "Best in bearish/uncertain markets — near-neutral P&L, locks in cost-of-carry.",
                hedgeNote,
                0.50, 0,
                computeTimeDecay(HedgeStrategyType.FUTURES_HEDGE, req, dte),
                "1 : 1",
                breakevenPct(beqn, entry),
                BigDecimal.ZERO);
    }

    // ── Greeks / metrics helpers ─────────────────────────────────────────────

    /** Risk/reward = maxGain / maxLoss.  maxGain null = unlimited → "∞ : 1". */
    private static String riskReward(BigDecimal maxGain, BigDecimal maxLoss) {
        if (maxGain == null) return "∞ : 1";
        if (maxLoss == null || maxLoss.signum() == 0) return "N/A";
        double ratio = maxGain.doubleValue() / maxLoss.doubleValue();
        return String.format("%.1f : 1", ratio);
    }

    /** % the stock must move from entry to reach breakeven.  Positive = needs to rise. */
    private static double breakevenPct(BigDecimal breakeven, BigDecimal entry) {
        if (entry == null || entry.signum() == 0) return 0;
        return (breakeven.doubleValue() - entry.doubleValue()) / entry.doubleValue() * 100.0;
    }

    /**
     * Simplified linear theta (₹/day).
     *  boughtPremium > 0  → we paid, theta is negative (daily cost)
     *  soldPremium   > 0  → we received, theta is positive (daily income)
     * Using linear decay: total / daysToExpiry (conservative / educational approximation).
     */
    private static BigDecimal theta(double boughtPremium, double soldPremium, int qty, int dte) {
        if (dte <= 0) return BigDecimal.ZERO;
        double net = (soldPremium - boughtPremium) * qty / dte;
        return bd(net);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<HedgeScenario> buildScenarios(BigDecimal entry, double[] mults, String[] labels,
                                                Function<BigDecimal, HedgeScenario> fn) {
        List<HedgeScenario> list = new ArrayList<>(mults.length);
        for (int i = 0; i < mults.length; i++) {
            BigDecimal price = entry.multiply(BigDecimal.valueOf(mults[i]))
                    .setScale(2, RoundingMode.HALF_UP);
            HedgeScenario raw = fn.apply(price);
            list.add(new HedgeScenario(labels[i], raw.priceAtExpiry(),
                    round(raw.stockPnl()), round(raw.optionPayoff()),
                    round(raw.futurePnl()), round(raw.netPnl())));
        }
        return list;
    }

    private static BigDecimal round(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP);
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(0, RoundingMode.HALF_UP);
    }

    private static BigDecimal lastNonNull(List<BigDecimal> s) {
        for (int i = s.size() - 1; i >= 0; i--) if (s.get(i) != null) return s.get(i);
        return null;
    }

    private static boolean inRange(BigDecimal v, double lo, double hi) {
        return v.compareTo(BigDecimal.valueOf(lo)) >= 0 && v.compareTo(BigDecimal.valueOf(hi)) < 0;
    }

    private static String fmt(BigDecimal v, int scale) {
        return v == null ? "N/A" : v.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    private static HedgeStrategyAnalysis withRecommended(HedgeStrategyAnalysis a, boolean rec) {
        return new HedgeStrategyAnalysis(
                a.strategy(), a.displayName(), a.position(),
                a.netPremiumPerShare(), a.totalPremiumCost(), a.breakeven(),
                a.maxGain(), a.maxLoss(),
                a.scenarios(), a.chartScenarios(),
                a.regimeFitScore(), rec, a.rationale(), a.hedgeNote(),
                a.probabilityOfProfit(), a.estimatedDaysToBreakeven(), a.timeDecay(),
                a.riskRewardRatio(), a.breakevenDistancePct(), a.dailyTheta());
    }
}
