package com.angle.trading.strategy.impl;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.config.AnalysisProperties;
import com.angle.trading.indicator.SimpleMovingAverage;
import com.angle.trading.strategy.Signal;
import com.angle.trading.strategy.Strategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Classic short/long SMA crossover.
 *
 *   short SMA crosses ABOVE long SMA → BUY (bullish momentum)
 *   short SMA crosses BELOW long SMA → SELL (bearish momentum)
 *   otherwise                        → HOLD
 *
 * Periods come from analysis.strategy.sma.short-period / long-period.
 */
@Component
@RequiredArgsConstructor
public class MovingAverageCrossover implements Strategy {

    private final AnalysisProperties analysisProperties;

    @Override
    public String name() {
        return "moving-average-crossover";
    }

    @Override
    public List<Signal> evaluate(List<Candle> candles) {
        int shortPeriod = analysisProperties.getStrategy().getSma().getShortPeriod();
        int longPeriod = analysisProperties.getStrategy().getSma().getLongPeriod();

        List<BigDecimal> shortSma = new SimpleMovingAverage(shortPeriod).compute(candles);
        List<BigDecimal> longSma = new SimpleMovingAverage(longPeriod).compute(candles);

        List<Signal> signals = new ArrayList<>(candles.size());
        for (int i = 0; i < candles.size(); i++) {
            signals.add(signalAt(i, shortSma, longSma));
        }
        return signals;
    }

    private static Signal signalAt(int i, List<BigDecimal> shortSma, List<BigDecimal> longSma) {
        if (i == 0) return Signal.HOLD;
        BigDecimal sNow = shortSma.get(i),   sPrev = shortSma.get(i - 1);
        BigDecimal lNow = longSma.get(i),    lPrev = longSma.get(i - 1);
        if (sNow == null || sPrev == null || lNow == null || lPrev == null) {
            return Signal.HOLD;
        }
        boolean crossedUp   = sPrev.compareTo(lPrev) <= 0 && sNow.compareTo(lNow) > 0;
        boolean crossedDown = sPrev.compareTo(lPrev) >= 0 && sNow.compareTo(lNow) < 0;
        if (crossedUp) return Signal.BUY;
        if (crossedDown) return Signal.SELL;
        return Signal.HOLD;
    }
}
