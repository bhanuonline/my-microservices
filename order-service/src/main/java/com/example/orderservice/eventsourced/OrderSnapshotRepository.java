package com.example.orderservice.eventsourced;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderSnapshotRepository extends JpaRepository<OrderSnapshot, String> {
}
