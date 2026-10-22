package com.angle.trading.journal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TradeJournalRepository extends JpaRepository<TradeJournalEntity, Long> {

    List<TradeJournalEntity> findAllByOrderByEntryDateTimeDesc();

    List<TradeJournalEntity> findByOutcomeOrderByEntryDateTimeDesc(String outcome);

    List<TradeJournalEntity> findBySegmentOrderByEntryDateTimeDesc(String segment);

    @Query("SELECT t FROM TradeJournalEntity t WHERE LOWER(t.symbol) LIKE LOWER(CONCAT('%', :q, '%')) ORDER BY t.entryDateTime DESC")
    List<TradeJournalEntity> searchBySymbol(String q);

    @Query("SELECT COUNT(t) FROM TradeJournalEntity t WHERE t.outcome = :outcome")
    long countByOutcome(String outcome);

    @Query("SELECT COALESCE(SUM(t.pnl), 0) FROM TradeJournalEntity t WHERE t.pnl IS NOT NULL")
    java.math.BigDecimal totalPnl();
}
