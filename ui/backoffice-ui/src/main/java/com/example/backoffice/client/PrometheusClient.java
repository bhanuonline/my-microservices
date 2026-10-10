package com.example.backoffice.client;

import com.example.backoffice.config.BackendProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Thin wrapper around Prometheus' HTTP API. Used for dashboard tiles only —
 * for live time-series we point operators at Grafana.
 *
 * All methods return defaults on error so the dashboard renders even if
 * Prometheus is down.
 */
@Component
public class PrometheusClient {

    private final RestTemplate rest;
    private final BackendProperties props;

    public PrometheusClient(RestTemplate backendRestTemplate, BackendProperties props) {
        this.rest = backendRestTemplate;
        this.props = props;
    }

    /** Evaluate an instant query; return the first scalar in the first result. */
    public Double query(String promQl) {
        try {
            String url = props.getPrometheusUrl() + "/api/v1/query?query="
                    + java.net.URLEncoder.encode(promQl, java.nio.charset.StandardCharsets.UTF_8);
            JsonNode body = rest.getForObject(url, JsonNode.class);
            if (body == null) return null;
            JsonNode result = body.path("data").path("result");
            if (!result.isArray() || result.isEmpty()) return null;
            // Prometheus returns [timestamp, "value"] — take the second element.
            JsonNode value = result.get(0).path("value");
            if (!value.isArray() || value.size() < 2) return null;
            return Double.parseDouble(value.get(1).asText("0"));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public Long queryLong(String promQl) {
        Double d = query(promQl);
        return d == null ? null : d.longValue();
    }
}
