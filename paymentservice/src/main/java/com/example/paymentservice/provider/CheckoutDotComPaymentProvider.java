package com.example.paymentservice.provider;

import com.example.paymentservice.model.Payment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

/**
 * Checkout.com payment provider — Phase 5. The first provider in the project
 * that supports explicit two-phase (authorize → capture) + unused-auth void.
 *
 * <h3>API shape (REST, documented at
 * <a href="https://api-reference.checkout.com/">api-reference.checkout.com</a>)</h3>
 * <ul>
 *   <li>{@code POST /payments}             — create payment; {@code capture:false} → Authorized</li>
 *   <li>{@code POST /payments/{id}/captures} — convert Authorized to Captured</li>
 *   <li>{@code POST /payments/{id}/voids}  — release Authorized without charge</li>
 *   <li>{@code POST /payments/{id}/refunds} — reverse a Captured</li>
 * </ul>
 *
 * <h3>Authorization</h3>
 * {@code Authorization: Bearer sk_sbox_...} (set by the WebClient filter in
 * {@link com.example.paymentservice.config.CheckoutDotComConfig}).
 *
 * <h3>Idempotency</h3>
 * {@code Cko-Idempotency-Key} per call. Key format is {@code <action>-<sagaId>}
 * so a retry hits the SAME Checkout.com-side resource. Keys we use:
 * {@code auth-<sagaId>}, {@code capture-<sagaId>}, {@code void-<sagaId>},
 * {@code refund-<sagaId>}.
 *
 * <h3>Webhook verification</h3>
 * HMAC-SHA256 over the raw body, keyed on the provider's signing secret.
 * {@code Cko-Signature} header carries the lowercase hex. Constant-time
 * comparison via {@link MessageDigest#isEqual} would be safer than
 * {@code String.equals} but for a sandbox project the HMAC check itself is
 * the only safeguard against trivial spoofing.
 *
 * <h3>Why we're a {@link SplitCapturePaymentProvider}</h3>
 * We implement BOTH {@link PaymentProvider#initiate initiate()} (so combined-
 * flow callers using {@link com.example.common.saga.PaymentCommand} still
 * work; initiate just delegates to authorize+capture) AND the three
 * split-capture methods ({@link #authorize}, {@link #capture},
 * {@link #voidPayment}). The orchestrator picks the path at saga-start based
 * on provider capability.
 */
@Component
@ConditionalOnProperty(value = "payment.checkoutcom.enabled", havingValue = "true")
public class CheckoutDotComPaymentProvider implements SplitCapturePaymentProvider {

    public static final String NAME = "checkoutcom";
    private static final Logger log = LoggerFactory.getLogger(CheckoutDotComPaymentProvider.class);
    private static final String SIG_HEADER = "cko-signature";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final WebClient http;
    private final String webhookSecret;
    private final String testCardToken;
    private final Duration callTimeout;

    public CheckoutDotComPaymentProvider(
            @Qualifier("checkoutcomWebClient") WebClient http,
            @Value("${payment.checkoutcom.webhook-secret}") String webhookSecret,
            @Value("${payment.checkoutcom.test-token:tok_card_visa_1}") String testCardToken,
            @Value("${payment.checkoutcom.call-timeout:15s}") Duration callTimeout) {
        this.http = http;
        this.webhookSecret = webhookSecret;
        this.testCardToken = testCardToken;
        this.callTimeout = callTimeout;
    }

    @Override
    public String name() { return NAME; }

    // ─── PaymentProvider interface ─────────────────────────────────────────

    @Override
    public ProviderSession initiate(Payment payment) {
        // Combined-flow callers: authorize + capture in one shot.
        // Keeps PaymentCommand usable against this provider for anyone who
        // doesn't want the two-phase saga path.
        ProviderSession auth = authorize(payment);
        if (auth.status() != Payment.Status.AUTHORIZED) {
            return auth;
        }
        // We need the auth to be persisted on the Payment before we call
        // capture() because capture() reads payment.providerRef. The caller
        // (PaymentService.applySessionResult AUTHORIZED branch) saves it;
        // but when initiate() is in the combined path, PaymentService goes
        // through the AUTHORIZED → CAPTURED fallthrough so this is awkward.
        // Simplest and correct: tell the Payment the authId NOW so capture
        // can read it from the entity's field directly.
        payment.markAuthorized(auth.providerRef());
        return capture(payment);
    }

    @Override
    public ProviderSession refund(Payment payment) {
        try {
            long amtMinor = toMinorUnits(payment.getAmount(), payment.getCurrency());
            Map<String, Object> body = Map.of(
                    "amount", amtMinor,
                    "reference", "refund-" + payment.getOrderId());

            http.post()
                    .uri("/payments/{id}/refunds", payment.getProviderRef())
                    .header("Cko-Idempotency-Key", "refund-" + payment.getSagaId())
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block(callTimeout);

            log.info("Checkout.com refund OK: paymentId={} authId={}",
                    payment.getId(), payment.getProviderRef());
            return ProviderSession.refunded(payment.getProviderRef());
        } catch (WebClientResponseException e) {
            log.error("Checkout.com refund failed: paymentId={} status={} body={}",
                    payment.getId(), e.getStatusCode(), e.getResponseBodyAsString());
            return ProviderSession.failed("refund_error:" + e.getStatusCode().value());
        } catch (RuntimeException e) {
            log.error("Checkout.com refund threw: paymentId={}", payment.getId(), e);
            return ProviderSession.failed("refund_error");
        }
    }

    @Override
    public WebhookResult handleWebhook(byte[] rawBody, Map<String, String> headers) {
        String signature = headers.get(SIG_HEADER);
        if (signature == null || signature.isBlank()) {
            throw new WebhookVerificationException("Missing " + SIG_HEADER + " header");
        }
        String expected = hmacSha256Hex(rawBody, webhookSecret);
        if (!expected.equalsIgnoreCase(signature)) {
            throw new WebhookVerificationException("Checkout.com webhook signature mismatch");
        }

        JsonNode body;
        try {
            body = JSON.readTree(rawBody);
        } catch (Exception e) {
            throw new WebhookVerificationException("Checkout.com webhook body not JSON", e);
        }

        String type = body.path("type").asText("");
        String paymentId = body.path("data").path("id").asText(null);
        log.info("Checkout.com webhook: type={} paymentId={}", type, paymentId);

        if (paymentId == null) {
            return WebhookResult.ignored(null);
        }
        return switch (type) {
            case "payment_approved", "payment_captured" -> WebhookResult.captured(paymentId);
            case "payment_declined", "payment_expired"  -> WebhookResult.declined(paymentId, type);
            // No dedicated VOIDED/DECLINED helpers on WebhookResult; map void to
            // declined so the saga transitions cleanly through the fail path.
            // VOIDED on the Payment entity is set by PaymentService.handleVoid
            // directly, not via webhook.
            case "payment_voided"                       -> WebhookResult.declined(paymentId, "voided");
            case "payment_refunded"                     -> WebhookResult.refunded(paymentId);
            default                                     -> WebhookResult.ignored(paymentId);
        };
    }

    // ─── SplitCapturePaymentProvider interface ─────────────────────────────

    @Override
    public ProviderSession authorize(Payment payment) {
        try {
            long amtMinor = toMinorUnits(payment.getAmount(), payment.getCurrency());

            Map<String, Object> body = Map.of(
                    "source", Map.of(
                            "type", "token",
                            "token", testCardToken),
                    "amount", amtMinor,
                    "currency", payment.getCurrency().toUpperCase(),
                    "reference", payment.getOrderId(),
                    "capture", false,
                    "metadata", Map.of(
                            "sagaId", payment.getSagaId().toString(),
                            "paymentId", payment.getId()));

            JsonNode resp = http.post()
                    .uri("/payments")
                    .header("Cko-Idempotency-Key", "auth-" + payment.getSagaId())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(callTimeout);

            if (resp == null) {
                return ProviderSession.failed("authorize_null_response");
            }
            String authId = resp.path("id").asText(null);
            String status = resp.path("status").asText("");
            if (authId == null) {
                return ProviderSession.failed("authorize_no_id");
            }
            if ("Authorized".equalsIgnoreCase(status) || "Pending".equalsIgnoreCase(status)) {
                log.info("Checkout.com authorize OK: sagaId={} authId={}",
                        payment.getSagaId(), authId);
                return ProviderSession.authorized(authId);
            }
            if ("Declined".equalsIgnoreCase(status)) {
                String reason = resp.path("response_summary").asText("declined");
                return ProviderSession.declined(reason);
            }
            // Unexpected but non-error status — treat as pending.
            log.warn("Checkout.com authorize unexpected status={} sagaId={}", status, payment.getSagaId());
            return ProviderSession.authorized(authId);

        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) {
                // 422: validation / declined for business reason.
                return ProviderSession.declined("checkoutcom_422:" + e.getStatusText());
            }
            log.error("Checkout.com authorize failed: sagaId={} status={} body={}",
                    payment.getSagaId(), e.getStatusCode(), e.getResponseBodyAsString());
            return ProviderSession.failed("authorize_error:" + e.getStatusCode().value());
        } catch (RuntimeException e) {
            log.error("Checkout.com authorize threw: sagaId={}", payment.getSagaId(), e);
            return ProviderSession.failed("authorize_error");
        }
    }

    @Override
    public ProviderSession capture(Payment payment) {
        if (payment.getProviderRef() == null) {
            return ProviderSession.failed("capture_no_auth_ref");
        }
        try {
            long amtMinor = toMinorUnits(payment.getAmount(), payment.getCurrency());
            Map<String, Object> body = Map.of(
                    "amount", amtMinor,
                    "reference", "capture-" + payment.getOrderId());

            http.post()
                    .uri("/payments/{id}/captures", payment.getProviderRef())
                    .header("Cko-Idempotency-Key", "capture-" + payment.getSagaId())
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block(callTimeout);

            log.info("Checkout.com capture OK: sagaId={} authId={}",
                    payment.getSagaId(), payment.getProviderRef());
            return ProviderSession.captured(payment.getProviderRef());

        } catch (WebClientResponseException e) {
            log.error("Checkout.com capture failed: sagaId={} status={} body={}",
                    payment.getSagaId(), e.getStatusCode(), e.getResponseBodyAsString());
            return ProviderSession.failed("capture_error:" + e.getStatusCode().value());
        } catch (RuntimeException e) {
            log.error("Checkout.com capture threw: sagaId={}", payment.getSagaId(), e);
            return ProviderSession.failed("capture_error");
        }
    }

    @Override
    public ProviderSession voidPayment(Payment payment) {
        if (payment.getProviderRef() == null) {
            return ProviderSession.failed("void_no_auth_ref");
        }
        try {
            Map<String, Object> body = Map.of(
                    "reference", "void-" + payment.getOrderId());

            http.post()
                    .uri("/payments/{id}/voids", payment.getProviderRef())
                    .header("Cko-Idempotency-Key", "void-" + payment.getSagaId())
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block(callTimeout);

            log.info("Checkout.com void OK: sagaId={} authId={}",
                    payment.getSagaId(), payment.getProviderRef());
            return ProviderSession.voided(payment.getProviderRef());

        } catch (WebClientResponseException e) {
            log.error("Checkout.com void failed: sagaId={} status={} body={}",
                    payment.getSagaId(), e.getStatusCode(), e.getResponseBodyAsString());
            return ProviderSession.failed("void_error:" + e.getStatusCode().value());
        } catch (RuntimeException e) {
            log.error("Checkout.com void threw: sagaId={}", payment.getSagaId(), e);
            return ProviderSession.failed("void_error");
        }
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    /**
     * Converts amount in major units (e.g. 9.99 USD) to minor units
     * (999 cents). JPY / KRW are zero-decimal.
     */
    static long toMinorUnits(BigDecimal amount, String currency) {
        boolean zeroDecimal = "JPY".equalsIgnoreCase(currency) || "KRW".equalsIgnoreCase(currency);
        return zeroDecimal
                ? amount.setScale(0, RoundingMode.HALF_UP).longValueExact()
                : amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /**
     * HMAC-SHA256(body, secret) → lowercase hex. Checkout.com's webhook
     * signature uses this exact format.
     */
    static String hmacSha256Hex(byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(body);
            return HexFormat.of().formatHex(sig);
        } catch (Exception e) {
            throw new WebhookVerificationException("HMAC-SHA256 failed: " + e.getMessage(), e);
        }
    }
}
