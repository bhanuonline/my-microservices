package com.example.paymentservice.web;

import com.example.common.saga.PaymentCommand;
import com.example.paymentservice.service.PaymentOutcome;
import com.example.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Synchronous entry point used by order-service's CheckoutController when the
 * client picks a hosted-checkout provider (Stripe/Razorpay/PayPal) and needs
 * the redirect URL back immediately.
 *
 * <p>The Kafka saga command still fires — this HTTP call is NOT a replacement.
 * It's a side-channel to get the provider's redirect URL back to the caller
 * without waiting for the next Kafka round-trip. {@link PaymentService}'s
 * find-or-create + race guard makes both orderings safe.
 *
 * <p>Mock requests return {@code redirectUrl: null} because the mock is
 * synchronous — the saga already has its answer.
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentInitiateController {

    private static final Logger log = LoggerFactory.getLogger(PaymentInitiateController.class);

    private final PaymentService paymentService;

    public PaymentInitiateController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/initiate")
    public ResponseEntity<InitiateResponse> initiate(@Valid @RequestBody InitiateRequest req) {
        PaymentCommand cmd = new PaymentCommand(
                req.sagaId(),
                req.orderId(),
                req.amount(),
                req.provider());
        PaymentOutcome outcome = paymentService.handlePayment(cmd);

        log.info("Initiate OK: sagaId={} provider={} paymentId={} redirect={}",
                req.sagaId(), req.provider(), outcome.payment().getId(),
                outcome.redirectUrl() != null);

        return ResponseEntity.ok(new InitiateResponse(
                outcome.payment().getId(),
                outcome.payment().getProviderRef(),
                outcome.redirectUrl() != null ? outcome.redirectUrl() : outcome.payment().getRedirectUrl(),
                outcome.payment().getStatus().name()));
    }

    public record InitiateRequest(
            @NotNull UUID sagaId,
            @NotNull String orderId,
            @NotNull @Positive BigDecimal amount,
            @NotNull String provider,
            String returnUrl,
            String cancelUrl
    ) {}

    public record InitiateResponse(
            String paymentId,
            String providerRef,
            String redirectUrl,
            String status
    ) {}
}
