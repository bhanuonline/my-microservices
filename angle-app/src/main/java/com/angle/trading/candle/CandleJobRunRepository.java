package com.angle.trading.candle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CandleJobRunRepository extends JpaRepository<CandleJobRunEntity, Long> {

    List<CandleJobRunEntity> findAllByOrderByStartedAtDesc();

    List<CandleJobRunEntity> findTop20ByOrderByStartedAtDesc();

    Optional<CandleJobRunEntity> findFirstByOrderByStartedAtDesc();

    /** Crash recovery: mark stale RUNNING rows as FAILED on app restart. */
    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("""
           UPDATE CandleJobRunEntity r
           SET r.status = 'FAILED',
               r.endedAt = :now,
               r.errorMessage = 'App restarted — job was interrupted'
           WHERE r.status = 'RUNNING' AND r.startedAt < :threshold
           """)
    int markStaleAsFailed(@Param("threshold") Instant threshold,
                          @Param("now")       Instant now);
}
