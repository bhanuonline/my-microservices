package com.example.paymentservice.provider;

import com.example.paymentservice.model.Payment;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;

/**
 * Stripe payment provider — PHASE 2.
 *
 * <h3>Checkout Session flow (what {@link #initiate} does)</h3>
 * <ol>
 *   <li>Convert amount to Stripe's smallest-currency-unit (paise/cents).</li>
 *   <li>Create a Checkout Session with {@code sagaId} as the idempotency key
 *       so a replay returns the SAME session, not a duplicate charge.</li>
 *   <li>Return {@code session.id} as providerRef and {@code session.url} as
 *       the hosted-checkout URL for the client to open.</li>
 * </ol>
 *
 * <h3>Webhook flow (what {@link #handleWebhook} does)</h3>
 * <ol>
 *   <li>Verify the {@code Stripe-Signature} HMAC header against
 *       {@code payment.stripe.webhook-secret} via {@link Webhook#constructEvent}.
 *       If verification fails, throw {@link WebhookVerificationException} —
 *       controller returns 400 and Stripe will retry.</li>
 *   <li>Dispatch on event type:
 *       <ul>
 *         <li>{@code checkout.session.completed} → CAPTURED</li>
 *         <li>{@code checkout.session.expired}  → DECLINED</li>
 *         <li>{@code payment_intent.payment_failed} → DECLINED</li>
 *         <li>{@code charge.refunded} → REFUNDED</li>
 *       </ul>
 *     Other events return an "ignored" {@link WebhookResult}.</li>
 * </ol>
 *
 * <h3>Config</h3>
 * <ul>
 *   <li>{@code payment.stripe.enabled=true} — activates this bean.</li>
 *   <li>{@code payment.stripe.secret-key} — sk_test_... (from Stripe dashboard).</li>
 *   <li>{@code payment.stripe.webhook-secret} — whsec_... (printed by
 *       {@code stripe listen --forward-to localhost:8091/webhooks/stripe}).</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(value = "payment.stripe.enabled", havingValue = "true")
public class StripePaymentProvider implements PaymentProvider {

    public static final String NAME = "stripe";
    private static final Logger log = LoggerFactory.getLogger(StripePaymentProvider.class);
    private static final String STRIPE_SIGNATURE_HEADER = "stripe-signature";

    private final String secretKey;
    private final String webhookSecret;
    private final String successUrlTemplate;
    private final String cancelUrlTemplate;

    public StripePaymentProvider(
            @Value("${payment.stripe.secret-key}") String secretKey,
            @Value("${payment.stripe.webhook-secret}") String webhookSecret,
            @Value("${payment.stripe.success-url:${payment.default-return-url}}") String successUrlTemplate,
            @Value("${payment.stripe.cancel-url:${payment.default-cancel-url}}") String cancelUrlTemplate) {
        this.secretKey = Objects.requireNonNull(secretKey, "payment.stripe.secret-key must be set");
        this.webhookSecret = Objects.requireNonNull(webhookSecret, "payment.stripe.webhook-secret must be set");
        this.successUrlTemplate = successUrlTemplate;
        this.cancelUrlTemplate = cancelUrlTemplate;
    }

    @PostConstruct
    void configureStripe() {
        Stripe.apiKey = this.secretKey;
        log.info("StripePaymentProvider enabled. Webhook secret present={} success={} cancel={}",
                webhookSecret != null && !webhookSecret.isBlank(),
                successUrlTemplate, cancelUrlTemplate);
    }

    @Override
    public String name() { return NAME; }

    @Override
    public ProviderSession initiate(Payment payment) {
        long smallestUnit = toSmallestUnit(payment.getAmount(), payment.getCurrency());
        String currency = payment.getCurrency().toLowerCase();

        // Replace a {CHECKOUT_SESSION_ID} placeholder if the config URL carries one,
        // so the hosted page can return it to the shop-ui for order lookup.
        String successUrl = successUrlTemplate.contains("{CHECKOUT_SESSION_ID}")
                ? successUrlTemplate
                : successUrlTemplate + (successUrlTemplate.contains("?") ? "&" : "?")
                        + "session_id={CHECKOUT_SESSION_ID}";

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrlTemplate)
                .putMetadata("sagaId", payment.getSagaId().toString())
                .putMetadata("orderId", payment.getOrderId())
                .putMetadata("paymentId", payment.getId())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(currency)
                                .setUnitAmount(smallestUnit)
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("Order " + payment.getOrderId())
                                        .build())
                                .build())
                        .build())
                .build();

        // sagaId as Stripe-Idempotency-Key: replaying returns the SAME session.
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey("saga-" + payment.getSagaId())
                .build();

        try {
            Session session = Session.create(params, options);
            log.info("Stripe session created: sagaId={} sessionId={}",
                    payment.getSagaId(), session.getId());
            return ProviderSession.pending(session.getId(), session.getUrl());
        } catch (StripeException e) {
            log.error("Stripe session creation failed: sagaId={}", payment.getSagaId(), e);
            // Treat as system error — saga will retry; next replay is idempotent.
            return ProviderSession.failed("stripe_error:" + e.getCode());
        }
    }

    @Override
    public ProviderSession refund(Payment payment) {
        // For Checkout Sessions, we have the session id as providerRef. The actual
        // charge/PaymentIntent is reachable via Session.getPaymentIntent().
        try {
            Session session = Session.retrieve(payment.getProviderRef());
            String paymentIntentId = session.getPaymentIntent();
            if (paymentIntentId == null) {
                return ProviderSession.failed("refund_no_payment_intent");
            }
            RefundCreateParams refundParams = RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId)
                    .build();
            RequestOptions options = RequestOptions.builder()
                    .setIdempotencyKey("refund-" + payment.getSagaId())
                    .build();
            Refund refund = Refund.create(refundParams, options);
            log.info("Stripe refund issued: paymentId={} refundId={}",
                    payment.getId(), refund.getId());
            return ProviderSession.refunded(payment.getProviderRef());
        } catch (StripeException e) {
            log.error("Stripe refund failed: paymentId={}", payment.getId(), e);
            return ProviderSession.failed("refund_error:" + e.getCode());
        }
    }

    @Override
    public WebhookResult handleWebhook(byte[] rawBody, Map<String, String> headers) {
        String signature = headers.get(STRIPE_SIGNATURE_HEADER);
        if (signature == null || signature.isBlank()) {
            throw new WebhookVerificationException("Missing " + STRIPE_SIGNATURE_HEADER + " header");
        }

        Event event;
        try {
            event = Webhook.constructEvent(new String(rawBody), signature, webhookSecret);
        } catch (SignatureVerificationException e) {
            throw new WebhookVerificationException("Stripe signature verification failed: " + e.getMessage(), e);
        }

        log.info("Stripe webhook: type={} id={}", event.getType(), event.getId());
        String providerRef = extractProviderRef(event);
        if (providerRef == null) {
            // Event not tied to any session we care about (billing portal etc.) → ack & drop.
            return WebhookResult.ignored(null);
        }

        return switch (event.getType()) {
            case "checkout.session.completed", "checkout.session.async_payment_succeeded"
                    -> WebhookResult.captured(providerRef);
            case "checkout.session.expired", "checkout.session.async_payment_failed",
                 "payment_intent.payment_failed"
                    -> WebhookResult.declined(providerRef, event.getType());
            case "charge.refunded"
                    -> WebhookResult.refunded(providerRef);
            default -> WebhookResult.ignored(providerRef);
        };
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private static long toSmallestUnit(BigDecimal amount, String currency) {
        // Zero-decimal currencies (JPY, KRW) use units directly; everything else × 100.
        // Minimal list for study purposes — add others as needed.
        boolean zeroDecimal = currency.equalsIgnoreCase("JPY") || currency.equalsIgnoreCase("KRW");
        return zeroDecimal
                ? amount.setScale(0, RoundingMode.HALF_UP).longValueExact()
                : amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /**
     * Pulls the Checkout session id out of a Stripe event. Depending on event
     * type, the id lives either on the Session object (checkout.session.*) or
     * on the PaymentIntent (we stashed sessionId in metadata).
     */
    private String extractProviderRef(Event event) {
        try {
            var dataObject = event.getDataObjectDeserializer().getObject().orElse(null);
            if (dataObject instanceof Session s) {
                return s.getId();
            }
            // For PaymentIntent / Charge events, our Session metadata is what links back.
            if (dataObject instanceof com.stripe.model.PaymentIntent pi) {
                // Our Session was created with checkout.session.completed metadata — fallback
                // to looking at the invoice id; for study we just return the PI id and let the
                // webhook be an "ignored" if it doesn't match a known providerRef.
                return pi.getId();
            }
            if (dataObject instanceof com.stripe.model.Charge c) {
                return c.getPaymentIntent();
            }
            return null;
        } catch (RuntimeException e) {
            log.warn("Could not extract providerRef from event {}: {}", event.getId(), e.getMessage());
            return null;
        }
    }
}
