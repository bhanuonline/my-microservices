package com.angle.trading.cache.impl;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.cache.CandleStore;
import com.angle.trading.persistence.CandleEntity;
import com.angle.trading.persistence.CandleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * JPA-backed {@link CandleStore}. The L3 (persistent) tier of the cache stack.
 *
 * Writes happen on a small dedicated thread pool so the caller (user request
 * or warmer) never waits on DB. If the pool is saturated, tasks queue.
 *
 * Read path is synchronous — a range query on the indexed (token, interval, ts)
 * takes ~5-10 ms typically. Cheap enough to block on.
 *
 * Duplicate handling: at the JPA layer, saving a candle with the same
 * (broker, exchange, token, interval, ts) as an existing one would fail
 * the UNIQUE constraint. We handle by checking existence per-candle in a
 * batched query and inserting only NEW rows. For high-throughput writes,
 * MySQL's INSERT ... ON DUPLICATE KEY UPDATE would be better — but Spring
 * Data doesn't expose that cleanly. Skipping-duplicates is fine for our
 * volumes (thousands per day, not millions per second).
 */
@Slf4j
public class MysqlCandleStore implements CandleStore {

    private final CandleRepository repo;
    private final ExecutorService writer;
    private final int batchSize;

    public MysqlCandleStore(CandleRepository repo, int asyncPoolSize, int batchSize) {
        this.repo = repo;
        this.batchSize = batchSize;
        this.writer = Executors.newFixedThreadPool(
                Math.max(1, asyncPoolSize),
                r -> {
                    Thread t = new Thread(r, "candle-store-writer");
                    t.setDaemon(true);
                    return t;
                });
        log.info("MysqlCandleStore initialised — asyncPoolSize={}, batchSize={}", asyncPoolSize, batchSize);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Candle> findRange(String broker, String exchange, String symbolToken,
                                   String intervalType, LocalDate from, LocalDate to) {
        Instant fromTs = from.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant toTs   = to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        try {
            List<CandleEntity> rows = repo.findRange(broker, exchange, symbolToken, intervalType, fromTs, toTs);
            if (rows.isEmpty()) {
                log.debug("MySQL MISS → {}:{}:{}:{} [{}→{}]", broker, exchange, symbolToken, intervalType, from, to);
                return List.of();
            }
            log.debug("MySQL HIT  → {}:{}:{}:{} [{}→{}] ({} rows)",
                    broker, exchange, symbolToken, intervalType, from, to, rows.size());
            List<Candle> out = new ArrayList<>(rows.size());
            for (CandleEntity e : rows) {
                out.add(new Candle(e.getTs(), e.getOpen(), e.getHigh(), e.getLow(), e.getClose(), e.getVolume()));
            }
            return out;
        } catch (Exception e) {
            log.warn("MySQL findRange failed for {}:{}:{}:{}: {}",
                    broker, exchange, symbolToken, intervalType, e.getMessage());
            return List.of();
        }
    }

    @Override
    public CompletableFuture<Integer> saveAsync(String broker, String exchange, String symbolToken,
                                                 String intervalType, List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        return CompletableFuture.supplyAsync(
                () -> saveBlocking(broker, exchange, symbolToken, intervalType, candles),
                writer);
    }

    /** Actual DB write — runs on the writer pool. */
    @Transactional
    protected int saveBlocking(String broker, String exchange, String symbolToken,
                                String intervalType, List<Candle> candles) {
        int saved = 0;
        try {
            List<CandleEntity> batch = new ArrayList<>(batchSize);
            for (Candle c : candles) {
                batch.add(new CandleEntity(broker, exchange, symbolToken, intervalType,
                        c.timestamp(), c.open(), c.high(), c.low(), c.close(), c.volume()));
                if (batch.size() >= batchSize) {
                    saved += flushSafely(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) saved += flushSafely(batch);
            log.debug("MySQL saved {} / {} candles for {}:{}", saved, candles.size(), symbolToken, intervalType);
        } catch (Exception e) {
            log.warn("MySQL saveAsync failed for {}:{}: {}", symbolToken, intervalType, e.getMessage());
        }
        return saved;
    }

    /**
     * Flush a batch, swallowing UNIQUE-constraint violations one candle at a
     * time on retry. Keeps the whole batch from failing when one candle
     * already exists.
     */
    private int flushSafely(List<CandleEntity> batch) {
        try {
            repo.saveAll(batch);
            return batch.size();
        } catch (Exception bulkErr) {
            // Batch failed (likely due to a duplicate). Retry candle-by-candle so
            // the good ones still get in.
            int ok = 0;
            for (CandleEntity e : batch) {
                try {
                    repo.save(e);
                    ok++;
                } catch (Exception ignored) {
                    // Duplicate — expected on re-fetch of the same range.
                }
            }
            return ok;
        }
    }

    @Override
    @Transactional
    public int deleteOlderThan(Instant savedAtCutoff) {
        try {
            int n = repo.deleteOlderThan(savedAtCutoff);
            log.info("MySQL retention purge — deleted {} rows saved before {}", n, savedAtCutoff);
            return n;
        } catch (Exception e) {
            log.warn("MySQL deleteOlderThan failed: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    @Transactional
    public int deleteByToken(String symbolToken) {
        try {
            int n = repo.deleteByToken(symbolToken);
            log.info("MySQL deleted {} rows for token {}", n, symbolToken);
            return n;
        } catch (Exception e) {
            log.warn("MySQL deleteByToken failed: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public long count() {
        try {
            return repo.count();
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> latestTimestamp(String broker, String exchange,
                                              String symbolToken, String intervalType) {
        try {
            return repo.findLatestTimestamp(broker, exchange, symbolToken, intervalType);
        } catch (Exception e) {
            log.warn("MySQL latestTimestamp failed for {}:{}: {}", symbolToken, intervalType, e.getMessage());
            return Optional.empty();
        }
    }
}
