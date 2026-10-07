package com.example.orderservice.saga;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderSagaRepository extends JpaRepository<OrderSaga, UUID> {

    Optional<OrderSaga> findByOrderId(String orderId);

    /**
     * Non-terminal sagas older than the cutoff, ordered by createdAt ASC
     * (oldest first — those are the most "stuck"). Used by OrderSagaResumer
     * on boot to re-fire whichever command is next in that saga's lifecycle.
     *
     * NOTE: NOTIFIED is treated as terminal success in the state machine
     * (markNotified() leaves state=NOTIFIED and no later transition exists),
     * so it is excluded. FAILED is explicitly terminal.
     */
    @Query("""
        select s from OrderSaga s
        where s.state in (
            com.example.orderservice.saga.OrderSaga$State.STARTED,
            com.example.orderservice.saga.OrderSaga$State.PAID,
            com.example.orderservice.saga.OrderSaga$State.COMPENSATING)
          and s.createdAt < :cutoff
        order by s.createdAt asc
        """)
    List<OrderSaga> findNonTerminalOlderThan(@Param("cutoff") Instant cutoff, Pageable pageable);
}
