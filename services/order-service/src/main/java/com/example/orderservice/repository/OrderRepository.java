package com.example.orderservice.repository;

import com.example.orderservice.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, String> {

    List<Order> findByStatusAndCreatedAtBefore(Order.Status status, Instant before);
}
