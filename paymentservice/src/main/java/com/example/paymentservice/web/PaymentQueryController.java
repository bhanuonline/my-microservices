package com.example.paymentservice.web;

import com.example.paymentservice.model.Payment;
import com.example.paymentservice.repository.PaymentRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Read-only payment APIs used by the Postman debugging flows + any admin UI.
 * Writes happen through Kafka → {@link
 * com.example.paymentservice.service.PaymentService}; this controller
 * never mutates state.
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentQueryController {

    private final PaymentRepository payments;

    public PaymentQueryController(PaymentRepository payments) {
        this.payments = payments;
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentDto> getOne(@PathVariable String id) {
        return payments.findById(id)
                .map(PaymentDto::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-order/{orderId}")
    public List<PaymentDto> getByOrder(@PathVariable String orderId) {
        return payments.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .map(PaymentDto::from)
                .toList();
    }

    /** Flat JSON shape — ignores JPA cycles and keeps field names snake-case-friendly for clients. */
    public record PaymentDto(
            String id,
            String sagaId,
            String orderId,
            BigDecimal amount,
            String currency,
            String provider,
            String providerRef,
            String status,
            String failureReason,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static PaymentDto from(Payment p) {
            return new PaymentDto(
                    p.getId(),
                    p.getSagaId().toString(),
                    p.getOrderId(),
                    p.getAmount(),
                    p.getCurrency(),
                    p.getProvider(),
                    p.getProviderRef(),
                    p.getStatus().name(),
                    p.getFailureReason(),
                    p.getCreatedAt(),
                    p.getUpdatedAt());
        }
    }
}
