package com.angle.trading.cache;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface IndicatorSnapshotRepository extends JpaRepository<IndicatorSnapshotEntity, Long> {

    /** All bars for a symbol+interval within a time window (used for day-breakdown lookup). */
    @Query("SELECT e FROM IndicatorSnapshotEntity e " +
           "WHERE e.symbol = :symbol AND e.interval = :interval " +
           "AND e.candleTime >= :from AND e.candleTime < :to " +
           "ORDER BY e.candleTime ASC")
    List<IndicatorSnapshotEntity> findBySymbolIntervalAndTimeRange(
            @Param("symbol")   String symbol,
            @Param("interval") String interval,
            @Param("from")     Instant from,
            @Param("to")       Instant to);

    /** Delete existing rows before re-inserting (idempotent save). */
    @Modifying
    @Query("DELETE FROM IndicatorSnapshotEntity e " +
           "WHERE e.symbol = :symbol AND e.interval = :interval " +
           "AND e.candleTime >= :from AND e.candleTime < :to")
    void deleteBySymbolIntervalAndTimeRange(
            @Param("symbol")   String symbol,
            @Param("interval") String interval,
            @Param("from")     Instant from,
            @Param("to")       Instant to);

    @Modifying
    @Query("DELETE FROM IndicatorSnapshotEntity e WHERE e.symbol = :symbol")
    void deleteAllBySymbol(@Param("symbol") String symbol);
}
