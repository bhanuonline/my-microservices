package com.angle.trading.signal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SignalRecordRepository
        extends JpaRepository<SignalRecordEntity, Long>,
                JpaSpecificationExecutor<SignalRecordEntity> {
}
