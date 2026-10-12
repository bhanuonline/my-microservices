package com.example.orderservice.service;

import com.example.orderservice.client.PaymentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentClient paymentClient;

    public PaymentService(PaymentClient paymentClient) {
        this.paymentClient = paymentClient;
    }

    // Retries up to 3 times (1 initial + 2 retries) with exponential backoff:
    // attempt 1 → wait 500ms → attempt 2 → wait 1000ms → attempt 3 → @Recover
    @Retryable(
            retryFor = RuntimeException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2)
    )
    public PaymentClient.InitiateResponse initiate(PaymentClient.InitiateRequest req) {
        log.debug("Calling payment-service initiate: sagaId={} orderId={}", req.sagaId(), req.orderId());
        return paymentClient.initiate(req);
    }

    // Called only after all 3 attempts fail. Returns null so CheckoutController
    // can still return a 201 — the saga continues via Kafka even without a redirect URL.
    @Recover
    public PaymentClient.InitiateResponse recover(RuntimeException e, PaymentClient.InitiateRequest req) {
        log.error("All retries exhausted for payment initiate: sagaId={} orderId={} error={}",
                req.sagaId(), req.orderId(), e.getMessage());
        return null;
    }
}
