package com.example.backoffice.client;

import com.example.backoffice.config.BackendProperties;
import com.example.backoffice.dto.OrderSummary;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Hits the CQRS order-query (ES) for all list views. Point lookups go through
 * the gateway because that's the authoritative write-side state.
 */
@Component
public class OrderQueryClient {

    private final RestTemplate rest;
    private final BackendProperties props;

    public OrderQueryClient(RestTemplate backendRestTemplate, BackendProperties props) {
        this.rest = backendRestTemplate;
        this.props = props;
    }

    public List<OrderSummary> list(String status, Long productId, int page, int size) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromHttpUrl(props.getOrderQueryUrl())
                .path("/orders/search")
                .queryParam("page", page).queryParam("size", size);
        if (status != null && !status.isBlank())  uri.queryParam("status", status);
        if (productId != null)                    uri.queryParam("productId", productId);

        JsonNode body = rest.getForObject(uri.toUriString(), JsonNode.class);
        return parse(body);
    }

    private static List<OrderSummary> parse(JsonNode body) {
        List<OrderSummary> out = new ArrayList<>();
        if (body == null) return out;
        JsonNode content = body.has("content") ? body.get("content") : body;
        for (JsonNode d : content) {
            out.add(new OrderSummary(
                    d.path("orderId").asText(d.path("id").asText(null)),
                    d.path("status").asText(null),
                    d.path("amount").asText(null),
                    d.hasNonNull("productId") ? d.path("productId").asLong() : null,
                    parseInstant(d.path("createdAt").asText(null))
            ));
        }
        return out;
    }

    private static Instant parseInstant(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Instant.parse(s); } catch (RuntimeException e) { return null; }
    }
}
