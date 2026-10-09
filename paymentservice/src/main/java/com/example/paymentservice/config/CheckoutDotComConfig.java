package com.example.paymentservice.config;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * WebClient bean dedicated to Checkout.com. Only created when the provider
 * is enabled; otherwise no WebClient is on the context (saves ~1.5 MB and a
 * handful of threads).
 *
 * <h3>Why WebClient, not HttpClient / RestTemplate</h3>
 * See docs/microservices/payment-gateway-phase5-design.md §6. Short version:
 * Micrometer auto-instruments WebClient (every call becomes a Zipkin span),
 * it integrates cleanly with resilience4j, Jackson in/out is native, and
 * Spring recommends it as the forward-looking HTTP client even in servlet
 * apps. We call {@code .block()} where needed since the processor is on a
 * Kafka consumer thread.
 *
 * <h3>Interceptors in use</h3>
 * One request filter: propagates the current {@code correlationId} from MDC
 * into the outbound {@code X-Correlation-Id} header so Checkout.com's own
 * logs — and any webhook we receive back — can be joined to the original
 * saga trace.
 *
 * <h3>Idempotency</h3>
 * The {@code Cko-Idempotency-Key} header is set per-call in the provider,
 * NOT here. Different calls (authorize, capture, void) need different keys
 * derived from sagaId + action.
 */
@Configuration
@ConditionalOnProperty(value = "payment.checkoutcom.enabled", havingValue = "true")
public class CheckoutDotComConfig {

    @Bean("checkoutcomWebClient")
    public WebClient checkoutcomWebClient(
            @Value("${payment.checkoutcom.base-url}") String baseUrl,
            @Value("${payment.checkoutcom.secret-key}") String secretKey,
            @Value("${payment.checkoutcom.connect-timeout:5s}") Duration connectTimeout,
            @Value("${payment.checkoutcom.response-timeout:15s}") Duration responseTimeout) {

        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .filter(correlationIdFilter())
                .build();
    }

    /**
     * Copies the current MDC correlationId into the outbound request headers
     * so logs on both sides are joinable.
     */
    private ExchangeFilterFunction correlationIdFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            String cid = MDC.get("correlationId");
            if (cid == null || cid.isBlank()) {
                return Mono.just(request);
            }
            return Mono.just(ClientRequest.from(request)
                    .header("X-Correlation-Id", cid)
                    .build());
        });
    }
}
