package com.angle.trading.broker.kite;

import com.angle.trading.broker.BrokerClient;
import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.broker.model.Quote;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Kite Connect (Zerodha) implementation of BrokerClient.
 *
 * Only wired up when broker.kite.enabled=true.
 *
 * Docs: https://kite.trade/docs/connect/v3/
 */
@Component
@ConditionalOnProperty(prefix = "broker.kite", name = "enabled", havingValue = "true")
public class KiteClient implements BrokerClient {

    @Override
    public String name() {
        return "KITE";
    }

    @Override
    public List<Candle> getCandles(Exchange exchange, String symbolToken,
                                   Interval interval, LocalDate from, LocalDate to) {
        throw new UnsupportedOperationException("Kite getCandles not implemented yet");
    }

    @Override
    public Quote getQuote(Exchange exchange, String symbolToken) {
        throw new UnsupportedOperationException("Kite getQuote not implemented yet");
    }
}
