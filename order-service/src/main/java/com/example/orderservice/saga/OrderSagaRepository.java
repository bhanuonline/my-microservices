package com.example.orderservice.saga;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderSagaRepository extends JpaRepository<OrderSaga, UUID> {

    Optional<OrderSaga> findByOrderId(String orderId);
}
