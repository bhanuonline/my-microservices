package com.angle.trading.strategy.impl;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.config.AnalysisProperties;
import com.angle.trading.indicator.RelativeStrengthIndex;
import com.angle.trading.strategy.Strategy;
import com.angle.trading.strategy.model.TradeIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Mean-reversion strategy driven by RSI.
 *
 *   RSI crosses BELOW the oversold threshold  → enter long  (price stretched too far down)
 *   RSI crosses ABOVE the overbought threshold → exit
 *   otherwise                                  → hold
 *
 * Config keys (application.properties):
 *   analysis.strategy.rsi.period       — RSI period,          default 14
 *   analysis.strategy.rsi.oversold     — buy threshold,       default 30
 *   analysis.strategy.rsi.overbought   — sell threshold,      default 70
 */
@Component
@RequiredArgsConstructor
public class RsiMeanReversion implements Strategy {

    private final AnalysisProperties analysisProperties;

    @Override
    public String name() {
        return "rsi-mean-reversion";
    }

    @Override
    public List<TradeIntent> evaluate(List<Candle> candles) {
        AnalysisProperties.Rsi cfg = analysisProperties.getStrategy().getRsi();
        BigDecimal oversold   = BigDecimal.valueOf(cfg.getOversold());
        BigDecimal overbought = BigDecimal.valueOf(cfg.getOverbought());

        List<BigDecimal> rsi = new RelativeStrengthIndex(cfg.getPeriod()).compute(candles);

        List<TradeIntent> intents = new ArrayList<>(candles.size());
        for (int i = 0; i < candles.size(); i++) {
            intents.add(intentAt(i, candles, rsi, oversold, overbought));
        }
        return intents;
    }

    private static TradeIntent intentAt(int i, List<Candle> candles, List<BigDecimal> rsi,
                                        BigDecimal oversold, BigDecimal overbought) {
        if (i == 0) return TradeIntent.hold();
        BigDecimal now  = rsi.get(i);
        BigDecimal prev = rsi.get(i - 1);
        if (now == null || prev == null) return TradeIntent.hold();

        boolean crossedBelowOversold   = prev.compareTo(oversold)   >= 0 && now.compareTo(oversold)   < 0;
        boolean crossedAboveOverbought = prev.compareTo(overbought) <= 0 && now.compareTo(overbought) > 0;

        if (crossedBelowOversold) {
            return TradeIntent.enterLong(candles.get(i).close(), null, null,
                    "RSI dipped into oversold (" + now + ")");
        }
        if (crossedAboveOverbought) {
            return TradeIntent.exit("RSI rose into overbought (" + now + ")");
        }
        return TradeIntent.hold();
    }
}
