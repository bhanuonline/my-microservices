package com.angle.trading.marketdata;

import com.angle.trading.broker.BrokerClient;
import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.cache.CandleCache;
import com.angle.trading.cache.CandleCacheKey;
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
 *
 * All getCandles() results flow through the pluggable {@link CandleCache}.
 * On a cache HIT the broker is not called; on a MISS the loader runs once
 * (atomic per key — no thundering herd) and the result is cached.
 * Swap cache providers via bias.cache.provider.
 */
@Slf4j
@Service
public class MarketDataService {

    private final Map<String, BrokerClient> brokersByName;
    private final CandleCache candleCache;

    public MarketDataService(List<BrokerClient> brokers, CandleCache candleCache) {
        this.brokersByName = brokers.stream()
                .collect(Collectors.toMap(b -> b.name().toUpperCase(), Function.identity()));
        this.candleCache = candleCache;
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

        CandleCacheKey key = new CandleCacheKey(
                brokerName, exchange.name(), symbolToken, interval.name(), from, to);
        // Pass a RangeLoader so the cache can request a smaller (from, to) than
        // the original key when doing gap-fill (Phase D).
        return candleCache.get(key,
                (loaderFrom, loaderTo) -> broker.getCandles(exchange, symbolToken, interval, loaderFrom, loaderTo));
    }
}
