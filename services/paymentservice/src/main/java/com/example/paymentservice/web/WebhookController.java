package com.example.paymentservice.web;

import com.example.paymentservice.provider.PaymentProvider;
import com.example.paymentservice.provider.PaymentProviderRegistry;
import com.example.paymentservice.provider.WebhookResult;
import com.example.paymentservice.provider.WebhookVerificationException;
import com.example.paymentservice.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Inbound webhook endpoint for every payment provider. Provider dispatches
 * on the {@code {provider}} path segment ({@code /webhooks/stripe},
 * {@code /webhooks/razorpay}, {@code /webhooks/mock}).
 *
 * <h3>Response contract</h3>
 * <ul>
 *   <li><b>200</b> — webhook accepted and processed (or safely ignored as a
 *       replay). Provider will stop retrying.</li>
 *   <li><b>400</b> — payload failed signature verification OR could not be
 *       parsed. Provider will retry (which is what we want — we don't want
 *       to acknowledge requests we couldn't trust).</li>
 *   <li><b>404</b> — unknown provider name.</li>
 *   <li><b>500</b> — unexpected internal error. Provider will retry.</li>
 * </ul>
 */
@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final PaymentProviderRegistry providers;
    private final PaymentService paymentService;

    public WebhookController(PaymentProviderRegistry providers, PaymentService paymentService) {
        this.providers = providers;
        this.paymentService = paymentService;
    }

    @PostMapping("/{providerName}")
    public ResponseEntity<Map<String, Object>> receive(
            @PathVariable String providerName,
            @RequestBody byte[] rawBody,
            HttpServletRequest request) {

        PaymentProvider provider;
        try {
            provider = providers.require(providerName);
        } catch (IllegalArgumentException e) {
            log.warn("Webhook for unknown provider: {}", providerName);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "unknown_provider",
                    "provider", providerName,
                    "available", providers.availableNames()));
        }

        Map<String, String> headers = copyHeaders(request);
        WebhookResult result = provider.handleWebhook(rawBody, headers);
        paymentService.applyWebhook(providerName, result);

        return ResponseEntity.ok(Map.of(
                "received", true,
                "provider", providerName,
                "providerRef", result.providerRef() == null ? "" : result.providerRef(),
                "newStatus", result.newStatus() == null ? "ignored" : result.newStatus().name(),
                "replayable", result.replayable()));
    }

    @ExceptionHandler(WebhookVerificationException.class)
    public ResponseEntity<Map<String, Object>> handleVerificationFailure(WebhookVerificationException e) {
        log.warn("Webhook rejected: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of(
                "error", "verification_failed",
                "message", e.getMessage()));
    }

    private Map<String, String> copyHeaders(HttpServletRequest request) {
        Map<String, String> out = new HashMap<>();
        for (String name : Collections.list(request.getHeaderNames())) {
            out.put(name.toLowerCase(), request.getHeader(name));
        }
        return out;
    }
}
