package com.angle.trading.strategy.impl;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.config.AnalysisProperties;
import com.angle.trading.indicator.BollingerBands;
import com.angle.trading.indicator.BollingerBands.Value;
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
 * Bollinger Bounce — mean-reversion strategy with confirmation entry.
 *
 * The bet: when price stretches outside a Bollinger Band, it usually reverts
 * toward the middle. We wait for a REVERSAL candle (closed back inside the
 * band + opposite colour of the stretch) before entering, which filters out
 * strong trend days that would blow through the band.
 *
 * Rules per candle i:
 *   BULLISH bounce (long):
 *     • prev.close &lt; prev.lower  (bar i-1 was extended below)
 *     • curr.close &gt; curr.lower  (bar i closed back inside)
 *     • curr green (close &gt; open) (reversal confirmed)
 *     → ENTER_LONG entry=close, stop=min(prev.low, curr.low) - buffer,
 *                  target=curr.middle
 *
 *   BEARISH bounce (short):
 *     • prev.close &gt; prev.upper
 *     • curr.close &lt; curr.upper
 *     • curr red (close &lt; open)
 *     → ENTER_SHORT entry=close, stop=max(prev.high, curr.high) + buffer,
 *                   target=curr.middle
 *
 * Otherwise HOLD. Positions self-manage via stop/target — no explicit EXIT signal.
 *
 * Config keys (application.properties):
 *   analysis.strategy.bollinger.period               — SMA + std period, default 20
 *   analysis.strategy.bollinger.std-multiplier       — band width,       default 2.0
 *   analysis.strategy.bollinger.stop-buffer-percent  — stop padding %,   default 0.1
 */
@Component
@RequiredArgsConstructor
public class BollingerBounceStrategy implements Strategy {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final AnalysisProperties analysisProperties;

    @Override
    public String name() {
        return "bollinger-bounce";
    }

    @Override
    public List<TradeIntent> evaluate(List<Candle> candles) {
        AnalysisProperties.Bollinger cfg = analysisProperties.getStrategy().getBollinger();
        List<Value> bb = new BollingerBands(cfg.getPeriod(), cfg.getStdMultiplier()).compute(candles);
        BigDecimal bufferPct = BigDecimal.valueOf(cfg.getStopBufferPercent());

        List<TradeIntent> intents = new ArrayList<>(candles.size());
        for (int i = 0; i < candles.size(); i++) {
            intents.add(intentAt(i, candles, bb, bufferPct));
        }
        return intents;
    }

    private TradeIntent intentAt(int i, List<Candle> candles, List<Value> bb, BigDecimal bufferPct) {
        if (i < 1) return TradeIntent.hold();
        Value curr = bb.get(i);
        Value prev = bb.get(i - 1);
        if (curr == null || prev == null) return TradeIntent.hold();

        Candle c  = candles.get(i);
        Candle pC = candles.get(i - 1);

        boolean green = c.close().compareTo(c.open()) > 0;
        boolean red   = c.close().compareTo(c.open()) < 0;

        // Bullish bounce: prev closed below lower band, curr closed back inside on a green candle
        if (green
                && pC.close().compareTo(prev.lower()) < 0
                && c.close().compareTo(curr.lower())  > 0) {
            BigDecimal reversalLow = pC.low().min(c.low());
            BigDecimal buffer = reversalLow.multiply(bufferPct, MC).divide(HUNDRED, MC);
            BigDecimal stop   = reversalLow.subtract(buffer).setScale(2, RoundingMode.HALF_UP);
            BigDecimal entry  = c.close();
            BigDecimal target = curr.middle();
            return TradeIntent.enterLong(entry, stop, target,
                    String.format("Bollinger bounce ↑ — prev close %s < lower %s, curr close %s back inside; target middle %s",
                            pC.close(), prev.lower(), entry, target));
        }

        // Bearish bounce: prev closed above upper band, curr closed back inside on a red candle
        if (red
                && pC.close().compareTo(prev.upper()) > 0
                && c.close().compareTo(curr.upper())  < 0) {
            BigDecimal reversalHigh = pC.high().max(c.high());
            BigDecimal buffer = reversalHigh.multiply(bufferPct, MC).divide(HUNDRED, MC);
            BigDecimal stop   = reversalHigh.add(buffer).setScale(2, RoundingMode.HALF_UP);
            BigDecimal entry  = c.close();
            BigDecimal target = curr.middle();
            return TradeIntent.enterShort(entry, stop, target,
                    String.format("Bollinger bounce ↓ — prev close %s > upper %s, curr close %s back inside; target middle %s",
                            pC.close(), prev.upper(), entry, target));
        }

        return TradeIntent.hold();
    }
}
