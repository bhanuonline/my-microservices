package com.example.backoffice.client;

import com.example.backoffice.config.BackendProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ES-backed product search. The operator's product manager uses this for
 * listing / filtering + a total-doc-count for the dashboard tile.
 */
@Component
public class ProductQueryClient {

    public record Product(String id, String name, Double price, Integer stock,
                          String category, String brand) {}
    public record Page(List<Product> items, long total, int page, int size,
                       Map<String, List<Facet>> facets) {}
    public record Facet(String key, long count) {}

    private final RestTemplate rest;
    private final BackendProperties props;

    public ProductQueryClient(RestTemplate backendRestTemplate, BackendProperties props) {
        this.rest = backendRestTemplate;
        this.props = props;
    }

    public Page search(String q, String category, String brand,
                       Double minPrice, Double maxPrice, Boolean inStock,
                       int page, int size, boolean withFacets) {
        UriComponentsBuilder u = UriComponentsBuilder.fromHttpUrl(props.getProductQueryUrl())
                .path("/products/search/query")
                .queryParam("page", page).queryParam("size", size);
        if (q != null && !q.isBlank())              u.queryParam("q", q);
        if (category != null && !category.isBlank()) u.queryParam("category", category);
        if (brand != null && !brand.isBlank())       u.queryParam("brand", brand);
        if (minPrice != null)                        u.queryParam("minPrice", minPrice);
        if (maxPrice != null)                        u.queryParam("maxPrice", maxPrice);
        if (Boolean.TRUE.equals(inStock))            u.queryParam("inStock", true);
        if (withFacets)                              u.queryParam("aggregations", "category,brand,price");

        JsonNode body = rest.getForObject(u.toUriString(), JsonNode.class);
        return parse(body, page, size);
    }

    /** Fast tile for the dashboard: just the total count. */
    public long totalCount() {
        try {
            Page p = search(null, null, null, null, null, null, 0, 1, false);
            return p.total();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static Page parse(JsonNode body, int page, int size) {
        if (body == null) return new Page(List.of(), 0, page, size, Map.of());
        List<Product> items = new ArrayList<>();
        for (JsonNode h : body.path("hits")) {
            JsonNode d = h.path("doc");
            if (d.isMissingNode()) continue;
            items.add(new Product(
                    d.path("id").asText(null),
                    d.path("name").asText(null),
                    d.hasNonNull("price") ? d.path("price").asDouble() : null,
                    d.hasNonNull("stock") ? d.path("stock").asInt() : null,
                    d.path("category").asText(null),
                    d.path("brand").asText(null)
            ));
        }
        long total = body.path("total").asLong(items.size());
        Map<String, List<Facet>> facets = new LinkedHashMap<>();
        JsonNode fNode = body.path("facets");
        if (fNode.isObject()) {
            fNode.fields().forEachRemaining(e -> {
                List<Facet> list = new ArrayList<>();
                for (JsonNode b : e.getValue()) {
                    list.add(new Facet(b.path("key").asText(), b.path("count").asLong(0)));
                }
                facets.put(e.getKey(), list);
            });
        }
        return new Page(items, total, page, size, facets);
    }
}
