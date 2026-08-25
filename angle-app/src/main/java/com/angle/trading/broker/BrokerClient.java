package com.angle.trading.broker;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.broker.model.Quote;

import java.time.LocalDate;
import java.util.List;

/**
 * Common contract for every broker (Angel One, Upstox, Kite, ...).
 * Strategy code depends only on this interface — swapping brokers
 * is a config change, not a code change.
 */
public interface BrokerClient {

    /** Broker identifier used in routing and logs. */
    String name();

    /** Fetch historical OHLCV candles for a symbol on a specific exchange segment. */
    List<Candle> getCandles(Exchange exchange,
                            String symbolToken,
                            Interval interval,
                            LocalDate from,
                            LocalDate to);

    /** Fetch the latest quote for a symbol on a specific exchange segment. */
    Quote getQuote(Exchange exchange, String symbolToken);
}
