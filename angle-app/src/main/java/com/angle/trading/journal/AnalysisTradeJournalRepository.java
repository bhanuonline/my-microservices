package com.angle.trading.journal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AnalysisTradeJournalRepository extends JpaRepository<AnalysisTradeJournalEntity, Long> {

    List<AnalysisTradeJournalEntity> findByStatusOrderByCreatedAtDesc(String status);

    List<AnalysisTradeJournalEntity> findBySymbolOrderByCreatedAtDesc(String symbol);

    List<AnalysisTradeJournalEntity> findAllByOrderByCreatedAtDesc();

    /** All open trades — used by the auto-monitor. */
    List<AnalysisTradeJournalEntity> findByStatus(String status);

    @Query("SELECT COUNT(t) FROM AnalysisTradeJournalEntity t WHERE t.status <> 'OPEN'")
    long countClosed();

    @Query("SELECT COUNT(t) FROM AnalysisTradeJournalEntity t WHERE t.status IN ('TARGET1_HIT','TARGET2_HIT')")
    long countWins();
}
