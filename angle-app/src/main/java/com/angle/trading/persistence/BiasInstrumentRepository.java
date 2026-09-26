package com.angle.trading.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BiasInstrumentRepository extends JpaRepository<BiasInstrumentEntity, Long> {

    List<BiasInstrumentEntity> findAllByOrderByPriorityAscIdAsc();

    List<BiasInstrumentEntity> findByEnabledTrueOrderByPriorityAscIdAsc();

    Optional<BiasInstrumentEntity> findBySymbolToken(String symbolToken);

    boolean existsBySymbolToken(String symbolToken);
}
