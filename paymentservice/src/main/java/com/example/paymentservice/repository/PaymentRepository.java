package com.example.paymentservice.repository;

import com.example.paymentservice.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, String> {

    /**
     * The core idempotency query. A re-sent PaymentCommand for the same saga
     * must find its existing Payment row (if any) rather than create a new one.
     */
    Optional<Payment> findBySagaIdAndProvider(UUID sagaId, String provider);

    /**
     * Webhook lookup — provider sends their reference (session id, intent id),
     * we need to find the matching Payment to update its state.
     */
    Optional<Payment> findByProviderRef(String providerRef);

    /** Admin / history queries for the REST read API. */
    List<Payment> findByOrderIdOrderByCreatedAtDesc(String orderId);
}
