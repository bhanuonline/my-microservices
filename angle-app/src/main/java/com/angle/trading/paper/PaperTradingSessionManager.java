package com.angle.trading.paper;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.marketdata.MarketDataService;
import com.angle.trading.marketdata.NiftyFileLoader;
import com.angle.trading.paper.model.CreateSessionRequest;
import com.angle.trading.paper.model.SessionSnapshot;
import com.angle.trading.paper.source.AngelHistoricalReplayCandleSource;
import com.angle.trading.paper.source.AngelLiveCandleSource;
import com.angle.trading.paper.source.CandleSource;
import com.angle.trading.paper.source.HistoricalReplayCandleSource;
import com.angle.trading.strategy.Strategy;
import com.angle.trading.strategy.StrategyRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns all live paper-trading sessions.
 *
 * Three source types recognised:
 *   "replay-nifty-csv"        — historical replay of the bundled Nifty CSV
 *   "angel-live"              — real-time Angel SmartAPI polling
 *   "angel-historical-replay" — Angel history for a date range, replayed at speed
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperTradingSessionManager {

    private static final String SOURCE_REPLAY_CSV       = "replay-nifty-csv";
    private static final String SOURCE_ANGEL_LIVE       = "angel-live";
    private static final String SOURCE_ANGEL_HISTORICAL = "angel-historical-replay";

    private static final int DEFAULT_CANDLES_PER_SECOND = 50;
    private static final int DEFAULT_WARMUP_CANDLES     = 100;
    private static final int DEFAULT_POLL_SECONDS       = 30;
    private static final Exchange DEFAULT_EXCHANGE      = Exchange.NSE;
    private static final Interval DEFAULT_INTERVAL_LIVE = Interval.ONE_MINUTE;
    private static final Interval DEFAULT_INTERVAL_HIST = Interval.ONE_DAY;

    private final StrategyRegistry  strategyRegistry;
    private final NiftyFileLoader   niftyFileLoader;
    private final MarketDataService marketDataService;

    private final Map<String, PaperTradingSession> sessions = new ConcurrentHashMap<>();

    public SessionSnapshot create(CreateSessionRequest req) {
        Strategy strategy = strategyRegistry.get(req.strategyName());
        CandleSource source = buildSource(req);

        PaperTradingSession session = new PaperTradingSession(strategy, source);
        sessions.put(session.getId(), session);
        session.start();

        log.info("Created session {} — strategy={}, source={}",
                session.getId(), strategy.name(), source.name());
        return session.snapshot();
    }

    public SessionSnapshot get(String id) {
        PaperTradingSession s = sessions.get(id);
        if (s == null) throw new IllegalArgumentException("No session with id: " + id);
        return s.snapshot();
    }

    public SessionSnapshot stop(String id) {
        PaperTradingSession s = sessions.get(id);
        if (s == null) throw new IllegalArgumentException("No session with id: " + id);
        s.stop();
        return s.snapshot();
    }

    public List<SessionSnapshot> list() {
        return sessions.values().stream()
                .map(PaperTradingSession::snapshot)
                .toList();
    }

    private CandleSource buildSource(CreateSessionRequest req) {
        String type = req.sourceType() == null ? SOURCE_REPLAY_CSV : req.sourceType();

        if (SOURCE_REPLAY_CSV.equals(type)) {
            int cps = req.candlesPerSecond() == null ? DEFAULT_CANDLES_PER_SECOND : req.candlesPerSecond();
            List<Candle> candles = niftyFileLoader.load();
            return new HistoricalReplayCandleSource(candles, cps);
        }

        if (SOURCE_ANGEL_LIVE.equals(type)) {
            if (req.symbolToken() == null || req.symbolToken().isBlank()) {
                throw new IllegalArgumentException("angel-live requires symbolToken");
            }
            Exchange exchange = req.exchange() == null ? DEFAULT_EXCHANGE       : req.exchange();
            Interval interval = req.interval() == null ? DEFAULT_INTERVAL_LIVE  : req.interval();
            int warmup   = req.warmupCandles()       == null ? DEFAULT_WARMUP_CANDLES : req.warmupCandles();
            int pollSec  = req.pollIntervalSeconds() == null ? DEFAULT_POLL_SECONDS   : req.pollIntervalSeconds();
            return new AngelLiveCandleSource(marketDataService, exchange, req.symbolToken(),
                    interval, warmup, pollSec);
        }

        if (SOURCE_ANGEL_HISTORICAL.equals(type)) {
            if (req.symbolToken() == null || req.symbolToken().isBlank()) {
                throw new IllegalArgumentException("angel-historical-replay requires symbolToken");
            }
            if (req.from() == null || req.to() == null) {
                throw new IllegalArgumentException("angel-historical-replay requires from and to (YYYY-MM-DD)");
            }
            Exchange exchange = req.exchange() == null ? DEFAULT_EXCHANGE      : req.exchange();
            Interval interval = req.interval() == null ? DEFAULT_INTERVAL_HIST : req.interval();
            int cps = req.candlesPerSecond() == null ? DEFAULT_CANDLES_PER_SECOND : req.candlesPerSecond();
            return new AngelHistoricalReplayCandleSource(marketDataService, exchange, req.symbolToken(),
                    interval, req.from(), req.to(), cps);
        }

        throw new IllegalArgumentException("Unknown sourceType: " + type
                + ". Supported: [" + SOURCE_REPLAY_CSV + ", " + SOURCE_ANGEL_LIVE + ", " + SOURCE_ANGEL_HISTORICAL + "]");
    }
}
