package com.angle.trading.bias;

import com.angle.trading.bias.model.ConsolidatedScore;
import com.angle.trading.bias.model.OptionSuggestion;
import com.angle.trading.bias.model.PositionSize;
import com.angle.trading.bias.model.TradePlan;
import com.angle.trading.bias.model.VixSection;
import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.OptionType;
import com.angle.trading.config.BiasProperties;
import com.angle.trading.config.TradingProperties;
import com.angle.trading.marketdata.InstrumentMasterService;
import com.angle.trading.marketdata.model.Instrument;
import com.angle.trading.strategy.Strategy;
import com.angle.trading.strategy.StrategyRegistry;
import com.angle.trading.strategy.model.IntentAction;
import com.angle.trading.strategy.model.TradeIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Assembles a {@link TradePlan} for one instrument.
 *
 * Pulls the ensemble strategy's latest TradeIntent → adds position sizing
 * (based on TradingProperties) and an option-strike suggestion.
 *
 * Returns null when:
 *   - Consolidated recommendation is NO-TRADE
 *   - Ensemble strategy has no entry/stop/target (nothing actionable)
 *
 * Rendered as the "Trade Plan" card on the dashboard.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradePlanBuilder {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final String ENSEMBLE_STRATEGY_NAME = "ensemble";

    private final StrategyRegistry strategyRegistry;
    private final TradingProperties tradingProperties;
    private final InstrumentMasterService instrumentMasterService;

    /**
     * Build a plan for the given instrument + already-computed sheet.
     * Uses the ensemble strategy's decision on the intraday candles.
     */
    public TradePlan build(BiasProperties.Instrument cfg,
                           List<Candle> intradayCandles,
                           ConsolidatedScore consolidated,
                           VixSection vix) {
        // Skip if there's nothing to plan for
        if (consolidated == null || !isActionable(consolidated.recommendation())) return null;
        if (intradayCandles == null || intradayCandles.isEmpty()) return null;

        // Get the ensemble's latest intent
        TradeIntent latest = latestEnsembleIntent(intradayCandles);
        if (latest == null || latest.action() == IntentAction.HOLD
                || latest.entry() == null || latest.stop() == null || latest.target() == null) {
            // Ensemble hasn't produced an actionable intent on the latest bar.
            // Fall back to consolidated-only trade plan (no exact levels).
            return null;
        }

        BigDecimal riskPoints   = latest.entry().subtract(latest.stop()).abs();
        BigDecimal rewardPoints = latest.target().subtract(latest.entry()).abs();
        BigDecimal rr = riskPoints.signum() > 0
                ? rewardPoints.divide(riskPoints, 2, RoundingMode.HALF_UP)
                : null;

        PositionSize sizing = calculatePositionSize(cfg.getSymbol(), riskPoints);
        OptionSuggestion optionSug = suggestOption(cfg, latest.action(), consolidated, latest.entry(), vix);

        return new TradePlan(
                latest.action(),
                latest.entry(),
                latest.stop(),
                latest.target(),
                riskPoints,
                rewardPoints,
                rr,
                sizing,
                optionSug,
                latest.rationale()
        );
    }

    // ---------- helpers ----------

    private static boolean isActionable(String recommendation) {
        return "LONG-ONLY".equals(recommendation) || "SHORT-ONLY".equals(recommendation);
    }

    private TradeIntent latestEnsembleIntent(List<Candle> candles) {
        try {
            Strategy ensemble = strategyRegistry.get(ENSEMBLE_STRATEGY_NAME);
            List<TradeIntent> intents = ensemble.evaluate(candles);
            if (intents == null || intents.isEmpty()) return null;
            return intents.get(intents.size() - 1);
        } catch (Exception e) {
            log.debug("Ensemble strategy evaluate failed: {}", e.getMessage());
            return null;
        }
    }

    private PositionSize calculatePositionSize(String symbol, BigDecimal riskPoints) {
        long capital = tradingProperties.getCapital();
        double riskPct = tradingProperties.getRiskPercent();
        int lotSize = tradingProperties.lotSizeFor(symbol);

        BigDecimal maxRisk = BigDecimal.valueOf(capital)
                .multiply(BigDecimal.valueOf(riskPct))
                .divide(BigDecimal.valueOf(100), MC)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal perLotRisk = riskPoints.multiply(BigDecimal.valueOf(lotSize))
                .setScale(2, RoundingMode.HALF_UP);

        int lots = 0;
        String warning = null;
        if (perLotRisk.signum() <= 0) {
            warning = "Cannot compute: risk per lot is zero";
        } else {
            lots = maxRisk.divide(perLotRisk, 0, RoundingMode.DOWN).intValue();
            if (lots == 0) {
                warning = "0 lots — stop too wide for your ₹" + maxRisk + " risk budget. "
                        + "Consider tighter stop or wait for better setup.";
            }
        }

        BigDecimal totalRisk = perLotRisk.multiply(BigDecimal.valueOf(lots));
        return new PositionSize(capital, riskPct, maxRisk, perLotRisk, lotSize, lots, totalRisk, warning);
    }

    /**
     * Suggest an option strike based on 3 factors:
     *   1. Confidence  — STRONG / MODERATE / WEAK (from ConsolidatedScore)
     *   2. VIX regime  — CALM / NORMAL / ELEVATED (from VixSection)
     *   3. Days to expiry — near-expiry forces ITM to survive theta
     *
     * Returns null when the instrument has no listed options or lookup fails.
     */
    private OptionSuggestion suggestOption(BiasProperties.Instrument cfg,
                                            IntentAction action,
                                            ConsolidatedScore consolidated,
                                            BigDecimal spotPrice,
                                            VixSection vix) {
        String underlying = mapToOptionsUnderlying(cfg.getSymbol());
        if (underlying == null) return null;

        TradingProperties.Options opts = tradingProperties.getOptions();

        // Step 1: pick expiry closest to preferred days-to-expiry
        LocalDate expiry = pickExpiry(underlying, opts.getPreferredDaysToExpiry());
        int daysToExpiry = expiry == null ? -1
                : (int) ChronoUnit.DAYS.between(LocalDate.now(), expiry);

        // Step 2: compute strike offset from 3 factors
        String vixRegime = vix == null ? "NORMAL" : (vix.regime() == null ? "NORMAL" : vix.regime());
        String confidence = consolidated.strength();
        int offset;
        String factorNote;

        if (expiry != null && daysToExpiry <= opts.getNearExpiryDays()) {
            // Near expiry overrides — force ITM to avoid theta wipeout.
            offset = opts.getNearExpiryItmOffset();
            factorNote = String.format("near-expiry (%d day%s) → forced ITM", daysToExpiry, daysToExpiry == 1 ? "" : "s");
        } else {
            offset = offsetFor(opts, vixRegime, confidence);
            factorNote = String.format("%s VIX + %s confidence", vixRegime, confidence);
        }

        // Step 3: build strike
        // For long → CE (call); for short → PE (put)
        OptionType type = action == IntentAction.ENTER_LONG ? OptionType.CE : OptionType.PE;
        BigDecimal atm = roundToNearest(spotPrice, opts.getStrikeInterval());
        // CE: OTM = above ATM, ITM = below ATM. Reverse for PE.
        BigDecimal strike = type == OptionType.CE
                ? atm.add(BigDecimal.valueOf(offset))
                : atm.subtract(BigDecimal.valueOf(offset));

        // Step 4: find the actual listed contract (if scrip master has it)
        String symbol = null;
        String token = null;
        if (expiry != null) {
            Optional<Instrument> match = instrumentMasterService.findOption(underlying, expiry, strike, type);
            if (match.isPresent()) {
                symbol = match.get().symbol();
                token = match.get().token();
            }
        }

        // Step 5: human-readable rationale
        String moneyness = offset == 0 ? "ATM" : (offset > 0 ? "OTM +" + offset : "ITM " + offset);
        String rationale = String.format(
                "%s bias · %s → %s strike (%s) · %d days to expiry",
                action == IntentAction.ENTER_LONG ? "LONG" : "SHORT",
                factorNote,
                moneyness,
                strike,
                daysToExpiry);

        return new OptionSuggestion(type, strike, underlying, spotPrice, atm,
                offset, expiry, daysToExpiry, symbol, token, rationale);
    }

    /**
     * Pick the listed expiry closest to {@code preferredDays} days from today.
     * Falls back to nearest future expiry if no expiry is within reasonable range.
     */
    private LocalDate pickExpiry(String underlying, int preferredDays) {
        try {
            List<LocalDate> expiries = instrumentMasterService.availableExpiries(underlying);
            LocalDate today = LocalDate.now();
            LocalDate target = today.plusDays(preferredDays);
            return expiries.stream()
                    .filter(d -> !d.isBefore(today))
                    .min(Comparator.comparingLong(d -> Math.abs(ChronoUnit.DAYS.between(target, d))))
                    .orElse(null);
        } catch (Exception e) {
            log.debug("Expiry lookup failed for {}: {}", underlying, e.getMessage());
            return null;
        }
    }

    /** Two-factor lookup: VIX regime × confidence → OTM offset points. */
    private static int offsetFor(TradingProperties.Options opts, String vixRegime, String confidence) {
        return switch (vixRegime) {
            case "CALM" -> switch (confidence) {
                case "STRONG"   -> opts.getCalmStrongOffset();
                case "MODERATE" -> opts.getCalmModerateOffset();
                default         -> opts.getCalmWeakOffset();
            };
            case "ELEVATED" -> switch (confidence) {
                case "STRONG"   -> opts.getElevatedStrongOffset();
                case "MODERATE" -> opts.getElevatedModerateOffset();
                default         -> opts.getElevatedWeakOffset();
            };
            default -> switch (confidence) {   // NORMAL
                case "STRONG"   -> opts.getNormalStrongOffset();
                case "MODERATE" -> opts.getNormalModerateOffset();
                default         -> opts.getNormalWeakOffset();
            };
        };
    }

    /** Map instrument display name to options-chain underlying. Returns null if not an options underlying. */
    private static String mapToOptionsUnderlying(String symbol) {
        if (symbol == null) return null;
        return switch (symbol.toLowerCase()) {
            case "nifty 50"      -> "NIFTY";
            case "bank nifty"    -> "BANKNIFTY";
            case "fin nifty"     -> "FINNIFTY";
            case "nifty next 50" -> "NIFTYNXT50";
            default -> null;   // stocks / MCX have options too but skip for MVP
        };
    }

    private static BigDecimal roundToNearest(BigDecimal value, int nearest) {
        if (value == null || nearest <= 0) return value;
        BigDecimal n = BigDecimal.valueOf(nearest);
        return value.divide(n, 0, RoundingMode.HALF_UP).multiply(n);
    }
}
