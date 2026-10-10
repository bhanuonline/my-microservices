package com.example.orderservice.eventsourced;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {
    List<OrderEvent> findByOrderIdAndVersionGreaterThanOrderByVersionAsc(String orderId, int version);
    List<OrderEvent> findByOrderIdOrderByVersionAsc(String orderId);
}
