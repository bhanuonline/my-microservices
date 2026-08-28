package com.angle.trading.marketdata;

import com.angle.trading.broker.BrokerClient;
import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Routes market data requests to the right broker.
 *
 * Spring auto-injects every BrokerClient bean; we index them by name so
 * callers can ask for "ANGEL", "UPSTOX", etc. without knowing which classes
 * are on the classpath.
 */
@Slf4j
@Service
public class MarketDataService {

    private final Map<String, BrokerClient> brokersByName;

    public MarketDataService(List<BrokerClient> brokers) {
        this.brokersByName = brokers.stream()
                .collect(Collectors.toMap(b -> b.name().toUpperCase(), Function.identity()));
        log.info("MarketDataService initialised with brokers: {}", brokersByName.keySet());
    }

    public List<Candle> getCandles(String brokerName,
                                   Exchange exchange,
                                   String symbolToken,
                                   Interval interval,
                                   LocalDate from,
                                   LocalDate to) {
        BrokerClient broker = brokersByName.get(brokerName.toUpperCase());
        if (broker == null) {
            throw new IllegalArgumentException(
                    "Unknown or disabled broker: " + brokerName + ". Available: " + brokersByName.keySet());
        }
        return broker.getCandles(exchange, symbolToken, interval, from, to);
    }
}
