package com.angle.trading.strategy.impl;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.config.AnalysisProperties;
import com.angle.trading.strategy.Strategy;
import com.angle.trading.strategy.model.TradeIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Volume-Breakout strategy.
 *
 * Enter when price breaks out of the recent N-candle range AND the candle
 * closes with unusually high volume (M-period avg × multiplier). Volume
 * confirmation filters out low-conviction false breakouts.
 *
 * Rules per candle (all must hold):
 *   BULLISH  (long):
 *     • close &gt; max(high) of the previous N candles
 *     • volume &gt; avg(volume last M) × multiplier
 *     • candle is green (close &gt; open)
 *
 *   BEARISH  (short):
 *     • close &lt; min(low) of the previous N candles
 *     • volume &gt; avg(volume last M) × multiplier
 *     • candle is red (close &lt; open)
 *
 * On entry, stop = the OTHER side of the range (bullish → range low, bearish → range high).
 * Target = entry ± (|entry - stop| × riskReward).
 *
 * Emits HOLD when no breakout, EXIT on the OPPOSITE breakout (natural flip).
 *
 * Config keys (application.properties):
 *   analysis.strategy.volume-breakout.lookback           — default 20
 *   analysis.strategy.volume-breakout.volume-avg-period  — default 20
 *   analysis.strategy.volume-breakout.volume-multiplier  — default 1.5
 *   analysis.strategy.volume-breakout.risk-reward        — default 2.0
 */
@Component
@RequiredArgsConstructor
public class VolumeBreakoutStrategy implements Strategy {

    private static final MathContext MC = MathContext.DECIMAL64;

    private final AnalysisProperties analysisProperties;

    @Override
    public String name() {
        return "volume-breakout";
    }

    @Override
    public List<TradeIntent> evaluate(List<Candle> candles) {
        AnalysisProperties.VolumeBreakout cfg = analysisProperties.getStrategy().getVolumeBreakout();
        int lookback = cfg.getLookback();
        int volAvgN  = cfg.getVolumeAvgPeriod();
        BigDecimal volMult = BigDecimal.valueOf(cfg.getVolumeMultiplier());
        BigDecimal rr      = BigDecimal.valueOf(cfg.getRiskReward());
        int warmup = Math.max(lookback, volAvgN);

        List<TradeIntent> intents = new ArrayList<>(candles.size());
        for (int i = 0; i < candles.size(); i++) {
            intents.add(intentAt(i, candles, lookback, volAvgN, volMult, rr, warmup));
        }
        return intents;
    }

    private TradeIntent intentAt(int i, List<Candle> candles,
                                  int lookback, int volAvgN,
                                  BigDecimal volMult, BigDecimal rr, int warmup) {
        if (i < warmup) return TradeIntent.hold();

        Candle c = candles.get(i);
        // 1. Range of previous N candles (exclude the current one)
        BigDecimal rangeHigh = candles.get(i - 1).high();
        BigDecimal rangeLow  = candles.get(i - 1).low();
        for (int k = i - lookback; k < i; k++) {
            if (k < 0) continue;
            Candle p = candles.get(k);
            if (p.high().compareTo(rangeHigh) > 0) rangeHigh = p.high();
            if (p.low().compareTo(rangeLow)   < 0) rangeLow  = p.low();
        }

        // 2. Volume vs M-period average (exclude current bar)
        long volSum = 0;
        int volCount = 0;
        for (int k = i - volAvgN; k < i; k++) {
            if (k < 0) continue;
            volSum += candles.get(k).volume();
            volCount++;
        }
        if (volCount == 0) return TradeIntent.hold();
        BigDecimal avgVol = BigDecimal.valueOf((double) volSum / volCount);
        BigDecimal threshold = avgVol.multiply(volMult, MC);

        boolean volConfirmed = BigDecimal.valueOf(c.volume()).compareTo(threshold) > 0;
        if (!volConfirmed) return TradeIntent.hold();

        boolean green = c.close().compareTo(c.open()) > 0;
        boolean red   = c.close().compareTo(c.open()) < 0;

        // 3. Bullish breakout
        if (green && c.close().compareTo(rangeHigh) > 0) {
            BigDecimal entry  = c.close();
            BigDecimal stop   = rangeLow;
            BigDecimal risk   = entry.subtract(stop).abs();
            BigDecimal target = entry.add(risk.multiply(rr, MC)).setScale(2, RoundingMode.HALF_UP);
            return TradeIntent.enterLong(entry, stop, target,
                    String.format("Volume breakout: close %s > %d-bar high %s, vol %d > %.1f× avg %.0f",
                            entry, lookback, rangeHigh, c.volume(),
                            volMult.doubleValue(), avgVol.doubleValue()));
        }

        // 4. Bearish breakout
        if (red && c.close().compareTo(rangeLow) < 0) {
            BigDecimal entry  = c.close();
            BigDecimal stop   = rangeHigh;
            BigDecimal risk   = stop.subtract(entry).abs();
            BigDecimal target = entry.subtract(risk.multiply(rr, MC)).setScale(2, RoundingMode.HALF_UP);
            return TradeIntent.enterShort(entry, stop, target,
                    String.format("Volume breakdown: close %s < %d-bar low %s, vol %d > %.1f× avg %.0f",
                            entry, lookback, rangeLow, c.volume(),
                            volMult.doubleValue(), avgVol.doubleValue()));
        }

        return TradeIntent.hold();
    }
}
