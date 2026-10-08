package com.example.paymentservice.provider;

import com.example.paymentservice.model.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Refund;
import com.razorpay.Utils;
import jakarta.annotation.PostConstruct;
import org.json.JSONObject;
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
 * Razorpay payment provider — PHASE 3.
 *
 * <h3>Order + Checkout.js flow (how it differs from Stripe)</h3>
 * Razorpay does NOT host the checkout page itself. We call Orders API
 * server-side to get an {@code order_id}, then the frontend uses
 * {@code Checkout.js} to open a modal with that id. The user pays, Razorpay
 * sends a webhook to our server. The "redirect URL" we return is really
 * our own URL that serves an HTML page with the Checkout.js snippet
 * configured for this order id.
 *
 * <h3>What {@link #initiate} does</h3>
 * <ol>
 *   <li>Convert amount to paise ({@code × 100}).</li>
 *   <li>Call {@code razorpayClient.orders.create()} with {@code receipt}
 *       set to {@code "saga-<sagaId>"}. Razorpay's own de-dup is on the
 *       {@code receipt} field — same receipt returns the same order.</li>
 *   <li>Return the Razorpay order_id as providerRef and a URL on the
 *       order-service that serves the Checkout.js page.</li>
 * </ol>
 *
 * <h3>Webhook flow</h3>
 * <ol>
 *   <li>Razorpay signs the raw body with the shared webhook secret
 *       (HMAC-SHA256) and puts the result in {@code X-Razorpay-Signature}.</li>
 *   <li>{@code Utils.verifyWebhookSignature(body, signature, secret)}
 *       returns true/false. We throw on false.</li>
 *   <li>Dispatch on event:
 *       <ul>
 *         <li>{@code order.paid} → CAPTURED</li>
 *         <li>{@code payment.failed} → DECLINED</li>
 *         <li>{@code refund.processed} → REFUNDED</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <h3>Config</h3>
 * <ul>
 *   <li>{@code payment.razorpay.enabled=true} — activates this bean.</li>
 *   <li>{@code payment.razorpay.key-id} — rzp_test_... (from Razorpay dashboard).</li>
 *   <li>{@code payment.razorpay.key-secret} — the paired secret.</li>
 *   <li>{@code payment.razorpay.webhook-secret} — set when creating the
 *       webhook in the Razorpay dashboard.</li>
 *   <li>{@code payment.razorpay.checkout-url-base} — base URL served by
 *       order-service that renders the Checkout.js page.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(value = "payment.razorpay.enabled", havingValue = "true")
public class RazorpayPaymentProvider implements PaymentProvider {

    public static final String NAME = "razorpay";
    private static final Logger log = LoggerFactory.getLogger(RazorpayPaymentProvider.class);
    private static final String SIG_HEADER = "x-razorpay-signature";

    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;
    private final String checkoutUrlBase;

    private RazorpayClient client;

    public RazorpayPaymentProvider(
            @Value("${payment.razorpay.key-id}") String keyId,
            @Value("${payment.razorpay.key-secret}") String keySecret,
            @Value("${payment.razorpay.webhook-secret}") String webhookSecret,
            @Value("${payment.razorpay.checkout-url-base:http://localhost:8083/razorpay/checkout}") String checkoutUrlBase) {
        this.keyId = Objects.requireNonNull(keyId, "payment.razorpay.key-id must be set");
        this.keySecret = Objects.requireNonNull(keySecret, "payment.razorpay.key-secret must be set");
        this.webhookSecret = Objects.requireNonNull(webhookSecret, "payment.razorpay.webhook-secret must be set");
        this.checkoutUrlBase = checkoutUrlBase;
    }

    @PostConstruct
    void initClient() throws RazorpayException {
        this.client = new RazorpayClient(keyId, keySecret);
        log.info("RazorpayPaymentProvider enabled. checkoutUrlBase={}", checkoutUrlBase);
    }

    @Override
    public String name() { return NAME; }

    @Override
    public ProviderSession initiate(Payment payment) {
        long paise = payment.getAmount().multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP).longValueExact();

        JSONObject orderReq = new JSONObject();
        orderReq.put("amount", paise);
        orderReq.put("currency", payment.getCurrency().toUpperCase());
        // Receipt is Razorpay's de-dup key — same receipt returns the same order id.
        orderReq.put("receipt", "saga-" + payment.getSagaId());
        JSONObject notes = new JSONObject();
        notes.put("sagaId", payment.getSagaId().toString());
        notes.put("orderId", payment.getOrderId());
        notes.put("paymentId", payment.getId());
        orderReq.put("notes", notes);

        try {
            com.razorpay.Order order = client.orders.create(orderReq);
            String rzpOrderId = order.get("id");
            String redirectUrl = checkoutUrlBase + "?orderId=" + rzpOrderId
                    + "&amount=" + paise + "&currency=" + payment.getCurrency().toUpperCase()
                    + "&keyId=" + keyId;
            log.info("Razorpay order created: sagaId={} rzpOrderId={}",
                    payment.getSagaId(), rzpOrderId);
            return ProviderSession.pending(rzpOrderId, redirectUrl);
        } catch (RazorpayException e) {
            log.error("Razorpay order creation failed: sagaId={}", payment.getSagaId(), e);
            return ProviderSession.failed("razorpay_error");
        }
    }

    @Override
    public ProviderSession refund(Payment payment) {
        // Razorpay refunds target the PAYMENT id (populated by the webhook as
        // one of the notes/data fields). providerRef on our Payment is the
        // ORDER id. We need to fetch the order's payments to find the one to
        // refund. For a simple study flow, assume one successful payment per
        // order and refund that.
        try {
            @SuppressWarnings("unchecked")
            var payments = (java.util.List<com.razorpay.Payment>) client.orders.fetchPayments(payment.getProviderRef());
            String rzpPaymentId = payments.stream()
                    .filter(p -> "captured".equals(p.get("status")))
                    .map(p -> (String) p.get("id"))
                    .findFirst()
                    .orElse(null);
            if (rzpPaymentId == null) {
                return ProviderSession.failed("refund_no_captured_payment");
            }
            JSONObject refundReq = new JSONObject();
            refundReq.put("amount", payment.getAmount().multiply(BigDecimal.valueOf(100)).longValue());
            Refund refund = client.payments.refund(rzpPaymentId, refundReq);
            log.info("Razorpay refund issued: paymentId={} refundId={}",
                    payment.getId(), refund.get("id").toString());
            return ProviderSession.refunded(payment.getProviderRef());
        } catch (RazorpayException e) {
            log.error("Razorpay refund failed: paymentId={}", payment.getId(), e);
            return ProviderSession.failed("refund_error");
        }
    }

    @Override
    public WebhookResult handleWebhook(byte[] rawBody, Map<String, String> headers) {
        String signature = headers.get(SIG_HEADER);
        if (signature == null || signature.isBlank()) {
            throw new WebhookVerificationException("Missing " + SIG_HEADER + " header");
        }

        String body = new String(rawBody);
        try {
            boolean valid = Utils.verifyWebhookSignature(body, signature, webhookSecret);
            if (!valid) {
                throw new WebhookVerificationException("Razorpay webhook signature mismatch");
            }
        } catch (RazorpayException e) {
            throw new WebhookVerificationException("Razorpay verifyWebhookSignature threw: " + e.getMessage(), e);
        }

        JSONObject event = new JSONObject(body);
        String eventType = event.optString("event");
        log.info("Razorpay webhook: type={}", eventType);

        // Payload shape differs per event. "payload.order.entity.id" for order.paid,
        // "payload.payment.entity.order_id" for payment.failed, etc.
        JSONObject payload = event.optJSONObject("payload");
        String rzpOrderId = extractOrderId(payload, eventType);
        if (rzpOrderId == null) {
            return WebhookResult.ignored(null);
        }

        return switch (eventType) {
            case "order.paid" -> WebhookResult.captured(rzpOrderId);
            case "payment.failed" -> WebhookResult.declined(rzpOrderId,
                    extractFailureReason(payload));
            case "refund.processed" -> WebhookResult.refunded(rzpOrderId);
            default -> WebhookResult.ignored(rzpOrderId);
        };
    }

    private static String extractOrderId(JSONObject payload, String eventType) {
        if (payload == null) return null;
        try {
            if (payload.has("order")) {
                var order = payload.getJSONObject("order").optJSONObject("entity");
                if (order != null) return order.optString("id", null);
            }
            if (payload.has("payment")) {
                var p = payload.getJSONObject("payment").optJSONObject("entity");
                if (p != null) {
                    String oid = p.optString("order_id", null);
                    if (oid != null) return oid;
                }
            }
            if (payload.has("refund")) {
                var r = payload.getJSONObject("refund").optJSONObject("entity");
                if (r != null) {
                    // refund.processed carries payment_id; follow to find the order.
                    // For study simplicity, we accept the refund even if we can't
                    // trace the order id here — caller gets ignored() in that case.
                    return null;
                }
            }
        } catch (RuntimeException e) {
            log.warn("Could not extract rzp order id from {} payload", eventType, e);
        }
        return null;
    }

    private static String extractFailureReason(JSONObject payload) {
        try {
            var p = payload.getJSONObject("payment").optJSONObject("entity");
            if (p == null) return "payment_failed";
            String code = p.optString("error_code", null);
            String desc = p.optString("error_description", null);
            return (code != null ? code : "payment_failed") + (desc != null ? ":" + desc : "");
        } catch (RuntimeException e) {
            return "payment_failed";
        }
    }
}
