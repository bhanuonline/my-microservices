package com.example.shop.client;

import com.example.shop.config.BackendProperties;
import com.example.shop.dto.ProductView;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Writes + authoritative reads go through the API gateway so we get the
 * same rate-limit / breaker / trace propagation as any other client.
 *
 * Point lookup on product goes through product-service directly (not the ES
 * read model) so a second call demonstrates the Redis cache win from Tier 4.
 */
@Component
public class GatewayClient {

    private final RestTemplate rest;
    private final BackendProperties props;

    public GatewayClient(RestTemplate backendRestTemplate, BackendProperties props) {
        this.rest = backendRestTemplate;
        this.props = props;
    }

    /** Reads the authoritative product record (SQL + Redis cache in product-service). */
    public ProductView getProduct(Long id) {
        try {
            JsonNode body = rest.getForObject(
                    props.getGatewayUrl() + "/api/v1/products/" + id, JsonNode.class);
            if (body == null) return null;
            return new ProductView(
                    body.path("id").asText(null),
                    body.path("name").asText(null),
                    body.path("description").asText(null),
                    body.hasNonNull("price") ? body.path("price").asDouble() : null,
                    body.hasNonNull("quantityInStock") ? body.path("quantityInStock").asInt() : null,
                    body.path("category").asText(null),
                    body.path("brand").asText(null)
            );
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) return null;
            throw e;
        }
    }

    /**
     * Fire the checkout — single-product-per-order since that's what the
     * current OrderController accepts. The multi-item cart case needs an API
     * change (OrderController.createOrder taking a list of lines). Noted
     * in docs/microservices/shop-ui.md.
     *
     * Returns the created order's JSON body (contains id + status).
     */
    public JsonNode placeOrder(Long productId, int quantity,
                               String idempotencyKey, String correlationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        headers.set("X-Correlation-Id", correlationId);
        String body = "{\"productId\":" + productId + ",\"quantity\":" + quantity + "}";
        ResponseEntity<JsonNode> resp = rest.postForEntity(
                props.getGatewayUrl() + "/api/v1/orders",
                new HttpEntity<>(body, headers),
                JsonNode.class);
        return resp.getBody();
    }

    /** Poll order status on the confirm page. */
    public JsonNode getOrder(String orderId) {
        try {
            return rest.getForObject(
                    props.getGatewayUrl() + "/api/v1/orders/" + orderId, JsonNode.class);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) return null;
            throw e;
        }
    }

    /** Exposed for templates that want to render "status: ???" tersely. */
    public static String statusOf(JsonNode order) {
        return order == null ? "UNKNOWN" : order.path("status").asText("UNKNOWN");
    }

    /** Expose server url for debugging in templates. */
    public String gatewayUrl() { return props.getGatewayUrl(); }

    /** Pulled by Thymeleaf for the gateway URL display block. */
    public Map<String, String> publicBackends() {
        return Map.of(
                "gateway", props.getGatewayUrl(),
                "productQuery", props.getProductQueryUrl()
        );
    }
}
