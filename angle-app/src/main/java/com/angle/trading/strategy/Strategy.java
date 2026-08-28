package com.angle.trading.strategy;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.strategy.model.TradeIntent;

import java.util.List;

/**
 * A trading strategy: given a series of candles, emit a {@link TradeIntent}
 * per candle.
 *
 * The returned list is the same length as the input. Callers can zip
 * candles with intents to backtest, or read the last element for a live
 * decision.
 *
 * Simple strategies emit ENTER_LONG / EXIT with null stops/targets.
 * SMC-style strategies fill in stop and target for a proper risk-managed
 * trade — the backtester then respects them.
 */
public interface Strategy {

    String name();

    List<TradeIntent> evaluate(List<Candle> candles);
}
