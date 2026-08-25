package com.angle.trading.strategy;

import com.angle.trading.broker.model.Candle;

import java.util.List;

/**
 * A trading strategy: given a series of candles, emit a Signal per candle.
 *
 * The returned list is the same length as the input. Callers can zip candles
 * with signals to backtest, or read the last element for a live decision.
 */
public interface Strategy {

    String name();

    List<Signal> evaluate(List<Candle> candles);
}
