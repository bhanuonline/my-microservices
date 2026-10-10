package com.example.paymentservice.provider;

import com.example.paymentservice.model.Payment;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Deterministic rule-based payment provider for development and tests.
 * Behaviour:
 *
 * <pre>
 *   amount &lt; success-below (default 50000)  → immediate CAPTURED
 *   amount &lt; decline-above (default 100000) → DECLINED  (fraud rule)
 *   amount &gt;= decline-above                  → FAILED    (system cap)
 * </pre>
 *
 * The thresholds are tunable via {@code payment.mock.*} so Postman flows can
 * trigger specific failure paths deterministically. In particular, F2 (payment
 * fails) sends {@code amount=75000}; F3 (compensation) sends a successful
 * amount but needs the notification side to fail.
 *
 * <p><b>Webhook path.</b> MockPaymentProvider is synchronous — it returns a
 * terminal {@link ProviderSession} directly from {@link #initiate}. For
 * parity with real providers that use webhooks, there's also
 * {@link #handleWebhook}: Postman flows can POST
 * {@code /webhooks/mock {"providerRef": "...", "outcome": "captured"}} to
 * simulate an async webhook arrival.
 */
@Component
public class MockPaymentProvider implements PaymentProvider {

    public static final String NAME = "mock";
    private static final Logger log = LoggerFactory.getLogger(MockPaymentProvider.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final BigDecimal successBelow;
    private final BigDecimal declineAbove;

    public MockPaymentProvider(
            @Value("${payment.mock.success-below:50000}") BigDecimal successBelow,
            @Value("${payment.mock.decline-above:100000}") BigDecimal declineAbove) {
        this.successBelow = successBelow;
        this.declineAbove = declineAbove;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ProviderSession initiate(Payment payment) {
        BigDecimal amt = payment.getAmount();
        String ref = "mock_" + UUID.randomUUID().toString().substring(0, 12);

        if (amt.compareTo(successBelow) < 0) {
            log.info("Mock CAPTURED: amount={} < threshold={} sagaId={}",
                    amt, successBelow, payment.getSagaId());
            return ProviderSession.captured(ref);
        }
        if (amt.compareTo(declineAbove) < 0) {
            log.info("Mock DECLINED: amount={} in [{}, {}) sagaId={}",
                    amt, successBelow, declineAbove, payment.getSagaId());
            return ProviderSession.declined("fraud_rule_triggered");
        }
        log.warn("Mock FAILED: amount={} >= cap={} sagaId={}",
                amt, declineAbove, payment.getSagaId());
        return ProviderSession.failed("system_cap_exceeded");
    }

    @Override
    public ProviderSession refund(Payment payment) {
        log.info("Mock REFUNDED: paymentId={} sagaId={}", payment.getId(), payment.getSagaId());
        return ProviderSession.refunded(payment.getProviderRef());
    }

    @Override
    public WebhookResult handleWebhook(byte[] rawBody, Map<String, String> headers) {
        // Mock has no signature to verify — this exists so Postman flows can
        // simulate an async webhook arrival for debugging the webhook plumbing
        // without any real provider involvement.
        try {
            Map<String, Object> body = JSON.readValue(rawBody, Map.class);
            String providerRef = (String) body.get("providerRef");
            String outcome = (String) body.get("outcome");
            if (providerRef == null || outcome == null) {
                throw new WebhookVerificationException("Mock webhook requires providerRef and outcome fields");
            }
            return switch (outcome.toLowerCase()) {
                case "captured" -> WebhookResult.captured(providerRef);
                case "declined" -> WebhookResult.declined(providerRef,
                        (String) body.getOrDefault("reason", "declined_by_mock"));
                case "refunded" -> WebhookResult.refunded(providerRef);
                default -> throw new WebhookVerificationException("Unknown outcome: " + outcome);
            };
        } catch (java.io.IOException e) {
            throw new WebhookVerificationException("Could not parse mock webhook body", e);
        }
    }
}
