package com.angle.trading.cache;

import com.angle.trading.dto.IndicatorSnapshotDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * L3 — DB persistence for computed indicator snapshots.
 *
 * Only day-breakdown snapshots (List<IndicatorSnapshotDto>) are persisted here.
 * Single-bar IndicatorsDto (latest values) is served from L1+L2 only; it is
 * cheap to recompute and would just duplicate the last row of the day breakdown.
 *
 * All public methods are best-effort — exceptions are logged, never re-thrown.
 * A DB blip never breaks a live cache read; L1/L2 continue to serve.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndicatorDbStore {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final IndicatorSnapshotRepository repo;

    /**
     * Look up all indicator snapshots for a specific trading day.
     * Returns an empty list (not null) if nothing is found.
     */
    public List<IndicatorSnapshotDto> findDayBreakdown(String symbol, String interval, LocalDate date) {
        try {
            Instant from = date.atStartOfDay(IST).toInstant();
            Instant to   = date.plusDays(1).atStartOfDay(IST).toInstant();
            List<IndicatorSnapshotEntity> rows = repo.findBySymbolIntervalAndTimeRange(symbol, interval, from, to);
            List<IndicatorSnapshotDto> dtos = rows.stream().map(IndicatorSnapshotEntity::toDto).toList();
            log.debug("L3 DB HIT — {} rows for {}|{}|{}", dtos.size(), symbol, interval, date);
            return dtos;
        } catch (Exception e) {
            log.warn("L3 DB read failed for {}|{}|{}: {}", symbol, interval, date, e.getMessage());
            return List.of();
        }
    }

    /**
     * Persist a full day's indicator snapshots.
     * Deletes any existing rows for the same symbol+interval+date first (idempotent).
     */
    @Transactional
    public void saveDayBreakdown(String symbol, String interval, LocalDate date,
                                  List<IndicatorSnapshotDto> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) return;
        try {
            Instant from = date.atStartOfDay(IST).toInstant();
            Instant to   = date.plusDays(1).atStartOfDay(IST).toInstant();
            repo.deleteBySymbolIntervalAndTimeRange(symbol, interval, from, to);
            List<IndicatorSnapshotEntity> entities = snapshots.stream()
                    .map(dto -> IndicatorSnapshotEntity.from(symbol, interval, dto))
                    .toList();
            repo.saveAll(entities);
            log.debug("L3 DB saved {} snapshots for {}|{}|{}", entities.size(), symbol, interval, date);
        } catch (Exception e) {
            log.warn("L3 DB save failed for {}|{}|{}: {}", symbol, interval, date, e.getMessage());
        }
    }

    /** Delete all snapshots for a specific symbol (all intervals, all dates). */
    @Transactional
    public int deleteBySymbol(String symbol) {
        try {
            repo.deleteAllBySymbol(symbol);
            log.debug("L3 DB evicted all snapshots for symbol={}", symbol);
            return 1; // row count not critical — just signal success
        } catch (Exception e) {
            log.warn("L3 DB evict by symbol failed for {}: {}", symbol, e.getMessage());
            return 0;
        }
    }

    /** Delete every indicator snapshot row. */
    @Transactional
    public int deleteAll() {
        try {
            long count = repo.count();
            repo.deleteAllInBatch();
            log.info("L3 DB evicted all indicator snapshots ({} rows)", count);
            return (int) count;
        } catch (Exception e) {
            log.warn("L3 DB evict-all failed: {}", e.getMessage());
            return 0;
        }
    }
}
