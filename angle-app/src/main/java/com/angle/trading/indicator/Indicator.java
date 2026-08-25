package com.angle.trading.indicator;

import com.angle.trading.broker.model.Candle;

import java.math.BigDecimal;
import java.util.List;

/**
 * A technical indicator: takes a list of candles, returns a series of values.
 *
 * The returned list has the same length as the input; positions where the
 * indicator cannot be computed (e.g. first N-1 points of a period-N SMA)
 * are null. Strategies must handle nulls.
 */
public interface Indicator {

    String name();

    List<BigDecimal> compute(List<Candle> candles);
}
