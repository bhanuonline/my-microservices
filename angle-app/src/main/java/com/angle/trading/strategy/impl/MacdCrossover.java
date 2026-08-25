package com.angle.trading.strategy.impl;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.config.AnalysisProperties;
import com.angle.trading.indicator.MACD;
import com.angle.trading.indicator.MACD.MacdValue;
import com.angle.trading.strategy.Strategy;
import com.angle.trading.strategy.model.TradeIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * MACD line / signal line crossover.
 *
 *   MACD line crosses ABOVE signal line → enter long
 *   MACD line crosses BELOW signal line → exit
 *   otherwise                            → hold
 *
 * Config keys (application.properties):
 *   analysis.strategy.macd.fast-period    — fast EMA period,   default 12
 *   analysis.strategy.macd.slow-period    — slow EMA period,   default 26
 *   analysis.strategy.macd.signal-period  — signal EMA period, default 9
 */
@Component
@RequiredArgsConstructor
public class MacdCrossover implements Strategy {

    private final AnalysisProperties analysisProperties;

    @Override
    public String name() {
        return "macd-crossover";
    }

    @Override
    public List<TradeIntent> evaluate(List<Candle> candles) {
        AnalysisProperties.Macd cfg = analysisProperties.getStrategy().getMacd();
        List<MacdValue> series = new MACD(
                cfg.getFastPeriod(), cfg.getSlowPeriod(), cfg.getSignalPeriod()
        ).computeSeries(candles);

        List<TradeIntent> intents = new ArrayList<>(candles.size());
        for (int i = 0; i < candles.size(); i++) {
            intents.add(intentAt(i, candles, series));
        }
        return intents;
    }

    private static TradeIntent intentAt(int i, List<Candle> candles, List<MacdValue> series) {
        if (i == 0) return TradeIntent.hold();
        MacdValue now  = series.get(i);
        MacdValue prev = series.get(i - 1);
        if (now.macd() == null || now.signal() == null
                || prev.macd() == null || prev.signal() == null) {
            return TradeIntent.hold();
        }
        BigDecimal mNow = now.macd(),  sNow = now.signal();
        BigDecimal mPrev = prev.macd(), sPrev = prev.signal();

        boolean crossedUp   = mPrev.compareTo(sPrev) <= 0 && mNow.compareTo(sNow) > 0;
        boolean crossedDown = mPrev.compareTo(sPrev) >= 0 && mNow.compareTo(sNow) < 0;

        if (crossedUp) {
            return TradeIntent.enterLong(candles.get(i).close(), null, null,
                    "MACD line crossed above signal");
        }
        if (crossedDown) {
            return TradeIntent.exit("MACD line crossed below signal");
        }
        return TradeIntent.hold();
    }
}
