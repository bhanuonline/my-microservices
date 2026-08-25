package com.angle.trading.broker.upstox;

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
 * Upstox v2 API implementation of BrokerClient.
 *
 * Only wired up when broker.upstox.enabled=true. Left as a stub so the
 * abstraction compiles; fill in when you add Upstox to the mix.
 *
 * Docs: https://upstox.com/developer/api-documentation/
 */
@Component
@ConditionalOnProperty(prefix = "broker.upstox", name = "enabled", havingValue = "true")
public class UpstoxClient implements BrokerClient {

    @Override
    public String name() {
        return "UPSTOX";
    }

    @Override
    public List<Candle> getCandles(Exchange exchange, String symbolToken,
                                   Interval interval, LocalDate from, LocalDate to) {
        throw new UnsupportedOperationException("Upstox getCandles not implemented yet");
    }

    @Override
    public Quote getQuote(Exchange exchange, String symbolToken) {
        throw new UnsupportedOperationException("Upstox getQuote not implemented yet");
    }
}
