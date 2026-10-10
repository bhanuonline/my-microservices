package com.example.backoffice.client;

import com.example.backoffice.config.BackendProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

/**
 * One client for every admin endpoint behind the gateway + direct product writes.
 * Returns raw JsonNode so templates stay independent of DTO versioning.
 */
@Component
public class GatewayClient {

    private final RestTemplate rest;
    private final BackendProperties props;

    public GatewayClient(RestTemplate backendRestTemplate, BackendProperties props) {
        this.rest = backendRestTemplate;
        this.props = props;
    }

    // ─── Products (SQL + Redis cache in product-service) ───
    public JsonNode listProducts() {
        return safeGet(props.getGatewayUrl() + "/api/v1/products");
    }

    public JsonNode getProduct(Long id) {
        return safeGet(props.getGatewayUrl() + "/api/v1/products/" + id);
    }

    public JsonNode createProduct(JsonNode body) {
        return rest.postForObject(props.getGatewayUrl() + "/api/v1/products",
                new HttpEntity<>(body, jsonHeaders()), JsonNode.class);
    }

    public void updateProduct(Long id, JsonNode body) {
        rest.put(props.getGatewayUrl() + "/api/v1/products/" + id,
                new HttpEntity<>(body, jsonHeaders()));
    }

    public void deleteProduct(Long id) {
        rest.delete(props.getGatewayUrl() + "/api/v1/products/" + id);
    }

    // ─── Orders ───
    public JsonNode getOrder(String id) {
        return safeGet(props.getGatewayUrl() + "/api/v1/orders/" + id);
    }

    // ─── Sagas admin ───
    public JsonNode listSagas(String state) {
        String url = props.getGatewayUrl() + "/admin/sagas";
        if (state != null && !state.isBlank()) url += "?state=" + state;
        return safeGet(url);
    }

    public JsonNode sagaDetail(String id) {
        return safeGet(props.getGatewayUrl() + "/admin/sagas/" + id);
    }

    public JsonNode compensateSaga(String id, String reason) {
        String url = props.getGatewayUrl() + "/admin/sagas/" + id
                + "/compensate?reason=" + reason;
        return rest.postForObject(url, new HttpEntity<>(null, jsonHeaders()), JsonNode.class);
    }

    // ─── DLQ admin ───
    public JsonNode listDlq(String status) {
        String url = props.getGatewayUrl() + "/admin/dlq";
        if (status != null && !status.isBlank()) url += "?status=" + status;
        return safeGet(url);
    }

    public JsonNode dlqDetail(Long id) {
        return safeGet(props.getGatewayUrl() + "/admin/dlq/" + id);
    }

    public JsonNode replayDlq(Long id) {
        return rest.postForObject(props.getGatewayUrl() + "/admin/dlq/" + id + "/replay",
                new HttpEntity<>(null, jsonHeaders()), JsonNode.class);
    }

    public JsonNode ackDlq(Long id) {
        return rest.postForObject(props.getGatewayUrl() + "/admin/dlq/" + id + "/ack",
                new HttpEntity<>(null, jsonHeaders()), JsonNode.class);
    }

    // ─── Feature flags admin ───
    public JsonNode listFlags() {
        return safeGet(props.getGatewayUrl() + "/admin/flags");
    }

    public JsonNode upsertFlag(String key, JsonNode body) {
        String url = props.getGatewayUrl() + "/admin/flags/" + key;
        rest.put(url, new HttpEntity<>(body, jsonHeaders()));
        return listFlags();   // return refreshed list for the view
    }

    public JsonNode toggleFlag(String key) {
        return rest.postForObject(props.getGatewayUrl() + "/admin/flags/" + key + "/toggle",
                new HttpEntity<>(null, jsonHeaders()), JsonNode.class);
    }

    // ─── helpers ───
    private JsonNode safeGet(String url) {
        try {
            return rest.getForObject(url, JsonNode.class);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) return null;
            throw e;
        }
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    /** Exposed for templates that want the raw URL for a "see the saga JSON" link. */
    public String gatewayUrl() { return props.getGatewayUrl(); }
}
