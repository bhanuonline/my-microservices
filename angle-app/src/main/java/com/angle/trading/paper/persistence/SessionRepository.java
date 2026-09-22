package com.angle.trading.paper.persistence;

import com.angle.trading.paper.model.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SessionRepository extends JpaRepository<SessionEntity, String> {

    List<SessionEntity> findAllByOrderByStartedAtDesc();

    List<SessionEntity> findByStatusOrderByStartedAtDesc(SessionStatus status);

    List<SessionEntity> findByStrategyNameOrderByStartedAtDesc(String strategyName);
}
