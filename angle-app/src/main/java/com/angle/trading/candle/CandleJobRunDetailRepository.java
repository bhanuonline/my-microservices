package com.angle.trading.candle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CandleJobRunDetailRepository extends JpaRepository<CandleJobRunDetailEntity, Long> {

    List<CandleJobRunDetailEntity> findByRunId(Long runId);

    /** Fetch instruments to retry: FAILED, SKIPPED, or CANCELLED from a given run. */
    @Query("""
           SELECT d FROM CandleJobRunDetailEntity d
           WHERE d.runId = :runId
             AND d.status IN ('FAILED', 'SKIPPED', 'CANCELLED')
           """)
    List<CandleJobRunDetailEntity> findRetryable(@Param("runId") Long runId);
}
