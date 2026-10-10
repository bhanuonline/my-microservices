package com.example.paymentservice.provider;

import com.example.paymentservice.model.Payment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paypal.sdk.Environment;
import com.paypal.sdk.PaypalServerSdkClient;
import com.paypal.sdk.authentication.ClientCredentialsAuthModel;
import com.paypal.sdk.controllers.OrdersController;
import com.paypal.sdk.controllers.PaymentsController;
import com.paypal.sdk.exceptions.ApiException;
import com.paypal.sdk.models.AmountWithBreakdown;
import com.paypal.sdk.models.CaptureOrderInput;
import com.paypal.sdk.models.CheckoutPaymentIntent;
import com.paypal.sdk.models.CreateOrderInput;
import com.paypal.sdk.models.LinkDescription;
import com.paypal.sdk.models.Money;
import com.paypal.sdk.models.OAuthToken;
import com.paypal.sdk.models.Order;
import com.paypal.sdk.models.OrderApplicationContext;
import com.paypal.sdk.models.OrderRequest;
import com.paypal.sdk.models.PurchaseUnitRequest;
import com.paypal.sdk.models.RefundCapturedPaymentInput;
import com.paypal.sdk.models.RefundRequest;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * PayPal payment provider — PHASE 4.
 *
 * <h3>Why PayPal is different from Stripe/Razorpay</h3>
 * <ul>
 *   <li><b>OAuth per request.</b> Not a static API key — the SDK trades
 *       client-id + secret for a short-lived access token and refreshes
 *       it as needed. Transparent for Orders/Payments calls through the
 *       SDK; we only touch the token manually for webhook verification.</li>
 *   <li><b>Server-side verification of webhooks.</b> PayPal doesn't give
 *       us an HMAC secret. Instead we POST the webhook back to PayPal's
 *       {@code /v1/notifications/verify-webhook-signature} endpoint with
 *       the headers + body + our configured webhook id — PayPal returns
 *       {@code {"verification_status":"SUCCESS"}} if the signature is valid.
 *       The SDK v2 doesn't wrap this endpoint, so we call it with
 *       {@link HttpClient} directly.</li>
 *   <li><b>CAPTURE intent means two API calls.</b> {@code Orders.create}
 *       returns an order in {@code CREATED} state with an approve link.
 *       The user clicks approve. The SDK/webhook then triggers
 *       {@code Orders.capture} server-side; the webhook
 *       {@code PAYMENT.CAPTURE.COMPLETED} fires once PayPal moves the funds.</li>
 * </ul>
 *
 * <h3>Config</h3>
 * <ul>
 *   <li>{@code payment.paypal.enabled=true}</li>
 *   <li>{@code payment.paypal.client-id}, {@code .client-secret} — from the
 *       sandbox app at https://developer.paypal.com/dashboard/applications/sandbox.</li>
 *   <li>{@code payment.paypal.webhook-id} — assigned when you create a webhook
 *       in the PayPal dashboard.</li>
 *   <li>{@code payment.paypal.environment} — {@code sandbox} (default) or
 *       {@code production}.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(value = "payment.paypal.enabled", havingValue = "true")
public class PayPalPaymentProvider implements PaymentProvider {

    public static final String NAME = "paypal";
    private static final Logger log = LoggerFactory.getLogger(PayPalPaymentProvider.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String clientId;
    private final String clientSecret;
    private final String webhookId;
    private final Environment environment;
    private final String returnUrl;
    private final String cancelUrl;

    private PaypalServerSdkClient sdk;
    private OrdersController orders;
    private PaymentsController payments;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public PayPalPaymentProvider(
            @Value("${payment.paypal.client-id}") String clientId,
            @Value("${payment.paypal.client-secret}") String clientSecret,
            @Value("${payment.paypal.webhook-id}") String webhookId,
            @Value("${payment.paypal.environment:sandbox}") String envName,
            @Value("${payment.paypal.return-url:${payment.default-return-url}}") String returnUrl,
            @Value("${payment.paypal.cancel-url:${payment.default-cancel-url}}") String cancelUrl) {
        this.clientId = Objects.requireNonNull(clientId, "payment.paypal.client-id must be set");
        this.clientSecret = Objects.requireNonNull(clientSecret, "payment.paypal.client-secret must be set");
        this.webhookId = Objects.requireNonNull(webhookId, "payment.paypal.webhook-id must be set");
        this.environment = "production".equalsIgnoreCase(envName)
                ? Environment.PRODUCTION
                : Environment.SANDBOX;
        this.returnUrl = returnUrl;
        this.cancelUrl = cancelUrl;
    }

    @PostConstruct
    void initClient() {
        this.sdk = new PaypalServerSdkClient.Builder()
                .environment(environment)
                .clientCredentialsAuth(new ClientCredentialsAuthModel.Builder(clientId, clientSecret).build())
                .build();
        this.orders = sdk.getOrdersController();
        this.payments = sdk.getPaymentsController();
        log.info("PayPalPaymentProvider enabled. environment={} webhookId.present={}",
                environment, webhookId != null && !webhookId.isBlank());
    }

    @Override
    public String name() { return NAME; }

    @Override
    public ProviderSession initiate(Payment payment) {
        PurchaseUnitRequest unit = new PurchaseUnitRequest.Builder(
                new AmountWithBreakdown.Builder(
                        payment.getCurrency().toUpperCase(),
                        payment.getAmount().toPlainString()).build())
                .referenceId(payment.getId())
                .description("Order " + payment.getOrderId())
                .build();

        OrderRequest body = new OrderRequest.Builder(
                CheckoutPaymentIntent.CAPTURE,
                List.of(unit))
                .applicationContext(new OrderApplicationContext.Builder()
                        .returnUrl(returnUrl)
                        .cancelUrl(cancelUrl)
                        .build())
                .build();

        CreateOrderInput input = new CreateOrderInput.Builder()
                .body(body)
                // sagaId as idempotency key — PayPal's PayPal-Request-Id header
                .paypalRequestId("saga-" + payment.getSagaId())
                .build();

        try {
            Order ppOrder = orders.createOrder(input).getResult();
            String approveUrl = findLink(ppOrder, "approve");
            log.info("PayPal order created: sagaId={} ppOrderId={}",
                    payment.getSagaId(), ppOrder.getId());
            return ProviderSession.pending(ppOrder.getId(), approveUrl);
        } catch (ApiException | IOException e) {
            log.error("PayPal order creation failed: sagaId={}", payment.getSagaId(), e);
            return ProviderSession.failed("paypal_error");
        }
    }

    @Override
    public ProviderSession refund(Payment payment) {
        // PayPal refunds target a CAPTURE id, not the order id. We only know
        // the order id (providerRef). Fetch the order to get its capture.
        try {
            Order ppOrder = orders.getOrder(new com.paypal.sdk.models.GetOrderInput.Builder(payment.getProviderRef()).build()).getResult();
            String captureId = extractCaptureId(ppOrder);
            if (captureId == null) {
                return ProviderSession.failed("refund_no_capture_id");
            }
            RefundCapturedPaymentInput refundInput = new RefundCapturedPaymentInput.Builder()
                    .captureId(captureId)
                    .body(new RefundRequest.Builder()
                            .amount(new Money.Builder(
                                    payment.getCurrency().toUpperCase(),
                                    payment.getAmount().toPlainString()).build())
                            .build())
                    .paypalRequestId("refund-" + payment.getSagaId())
                    .build();
            payments.refundCapturedPayment(refundInput);
            log.info("PayPal refund issued: paymentId={} captureId={}",
                    payment.getId(), captureId);
            return ProviderSession.refunded(payment.getProviderRef());
        } catch (ApiException | IOException e) {
            log.error("PayPal refund failed: paymentId={}", payment.getId(), e);
            return ProviderSession.failed("refund_error");
        }
    }

    @Override
    public WebhookResult handleWebhook(byte[] rawBody, Map<String, String> headers) {
        try {
            verifyWebhookSignature(rawBody, headers);
        } catch (RuntimeException e) {
            throw new WebhookVerificationException("PayPal webhook verification failed: " + e.getMessage(), e);
        }

        JsonNode event;
        try {
            event = JSON.readTree(rawBody);
        } catch (IOException e) {
            throw new WebhookVerificationException("PayPal webhook body not JSON", e);
        }

        String eventType = event.path("event_type").asText("");
        String resourceId = event.path("resource").path("id").asText(null);
        // For PAYMENT.CAPTURE.* events, the order id is nested under supplementary_data.
        String orderId = event.path("resource").path("supplementary_data")
                .path("related_ids").path("order_id").asText(null);
        if (orderId == null) {
            // CHECKOUT.ORDER.* events put the order id directly on resource.id.
            orderId = resourceId;
        }
        log.info("PayPal webhook: type={} orderId={} resourceId={}", eventType, orderId, resourceId);
        if (orderId == null) {
            return WebhookResult.ignored(null);
        }

        return switch (eventType) {
            case "CHECKOUT.ORDER.APPROVED" -> {
                // User approved but not yet captured. For CAPTURE intent this triggers
                // auto-capture via PayPal; we'll see PAYMENT.CAPTURE.COMPLETED next.
                yield WebhookResult.ignored(orderId);
            }
            case "PAYMENT.CAPTURE.COMPLETED" -> WebhookResult.captured(orderId);
            case "PAYMENT.CAPTURE.DENIED",
                 "CHECKOUT.ORDER.VOIDED",
                 "PAYMENT.CAPTURE.DECLINED" -> WebhookResult.declined(orderId, eventType);
            case "PAYMENT.CAPTURE.REFUNDED" -> WebhookResult.refunded(orderId);
            default -> WebhookResult.ignored(orderId);
        };
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    /**
     * Call PayPal's verify-webhook-signature endpoint. The SDK v2 doesn't
     * wrap this one, so we HTTP it directly with an OAuth token fetched
     * via the SDK's own auth helper.
     */
    private void verifyWebhookSignature(byte[] rawBody, Map<String, String> headers) {
        try {
            OAuthToken token = sdk.getClientCredentialsAuth().fetchToken();
            String base = environment == Environment.PRODUCTION
                    ? "https://api-m.paypal.com"
                    : "https://api-m.sandbox.paypal.com";

            String bodyStr = new String(rawBody);
            String payload = JSON.writeValueAsString(Map.of(
                    "auth_algo", required(headers, "paypal-auth-algo"),
                    "cert_url", required(headers, "paypal-cert-url"),
                    "transmission_id", required(headers, "paypal-transmission-id"),
                    "transmission_sig", required(headers, "paypal-transmission-sig"),
                    "transmission_time", required(headers, "paypal-transmission-time"),
                    "webhook_id", webhookId,
                    "webhook_event", JSON.readTree(bodyStr)
            ));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/v1/notifications/verify-webhook-signature"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token.getAccessToken())
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new RuntimeException("verify endpoint returned " + resp.statusCode() + ": " + resp.body());
            }
            String status = JSON.readTree(resp.body()).path("verification_status").asText("");
            if (!"SUCCESS".equals(status)) {
                throw new RuntimeException("verification_status=" + status);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("verify call threw: " + e.getMessage(), e);
        }
    }

    private static String required(Map<String, String> headers, String name) {
        String v = headers.get(name);
        if (v == null || v.isBlank()) {
            throw new RuntimeException("Missing required header: " + name);
        }
        return v;
    }

    private static String findLink(Order ppOrder, String rel) {
        List<LinkDescription> links = ppOrder.getLinks();
        if (links == null) return null;
        for (LinkDescription link : links) {
            if (rel.equalsIgnoreCase(link.getRel())) return link.getHref();
        }
        return null;
    }

    private static String extractCaptureId(Order ppOrder) {
        if (ppOrder.getPurchaseUnits() == null) return null;
        for (var unit : ppOrder.getPurchaseUnits()) {
            var paymentCollection = unit.getPayments();
            if (paymentCollection == null) continue;
            var captures = paymentCollection.getCaptures();
            if (captures == null || captures.isEmpty()) continue;
            // Return the first captured one.
            return captures.get(0).getId();
        }
        return null;
    }
}
