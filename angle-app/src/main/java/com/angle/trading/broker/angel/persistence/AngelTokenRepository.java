package com.angle.trading.broker.angel.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AngelTokenRepository extends JpaRepository<AngelTokenEntity, String> {
}
