package com.angle.trading.paper.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeRepository extends JpaRepository<TradeEntity, Long> {

    List<TradeEntity> findBySessionIdOrderByEntryTimeAsc(String sessionId);

    Page<TradeEntity> findAllByOrderByEntryTimeDesc(Pageable pageable);

    long countBySessionId(String sessionId);
}
