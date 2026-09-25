package com.angle.trading.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link CandleEntity}.
 *
 * Custom queries kept minimal — Spring Data derives most from method names.
 * Two custom ones because range queries are the hot path and named-native
 * queries let us tune later without changing callers.
 */
public interface CandleRepository extends JpaRepository<CandleEntity, Long> {

    /**
     * Range query — the primary read path used by MysqlCandleStore.
     * Ordered by timestamp ascending so callers get candles chronologically.
     */
    @Query("""
           SELECT c FROM CandleEntity c
           WHERE c.broker = :broker
             AND c.exchange = :exchange
             AND c.symbolToken = :symbolToken
             AND c.intervalType = :intervalType
             AND c.ts BETWEEN :from AND :to
           ORDER BY c.ts ASC
           """)
    List<CandleEntity> findRange(
            @Param("broker") String broker,
            @Param("exchange") String exchange,
            @Param("symbolToken") String symbolToken,
            @Param("intervalType") String intervalType,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Delete rows older than the given timestamp. Used by the retention cron.
     * Modifying + Transactional required on the caller.
     */
    @Modifying
    @Query("DELETE FROM CandleEntity c WHERE c.savedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

    /** Wipe all candles for one instrument (across all intervals + dates). */
    @Modifying
    @Query("DELETE FROM CandleEntity c WHERE c.symbolToken = :symbolToken")
    int deleteByToken(@Param("symbolToken") String symbolToken);

    /**
     * Latest candle timestamp for one (broker, exchange, token, interval).
     * Cheap — uses the (symbol_token, interval_type, ts) index.
     * Returns empty when no rows match.
     */
    @Query("""
           SELECT MAX(c.ts) FROM CandleEntity c
           WHERE c.broker = :broker
             AND c.exchange = :exchange
             AND c.symbolToken = :symbolToken
             AND c.intervalType = :intervalType
           """)
    Optional<Instant> findLatestTimestamp(
            @Param("broker") String broker,
            @Param("exchange") String exchange,
            @Param("symbolToken") String symbolToken,
            @Param("intervalType") String intervalType);
}
