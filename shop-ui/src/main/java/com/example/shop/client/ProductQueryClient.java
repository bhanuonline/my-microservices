package com.example.shop.client;

import com.example.shop.config.BackendProperties;
import com.example.shop.dto.FacetBucket;
import com.example.shop.dto.ProductView;
import com.example.shop.dto.SearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads from the Elasticsearch read-side (product-query). Hits unauth'd —
 * product-query is dev-open. In prod this would get an auth header via the
 * same RestTemplate interceptor that handles the gateway.
 */
@Component
public class ProductQueryClient {

    private static final Logger log = LoggerFactory.getLogger(ProductQueryClient.class);


    private final RestTemplate rest;
    private final BackendProperties props;

    public ProductQueryClient(RestTemplate backendRestTemplate, BackendProperties props) {
        this.rest = backendRestTemplate;
        this.props = props;
    }

    /**
     * Query the ES-backed search endpoint. Params match ProductSearchController.
     * We parse the JSON tree directly (not the typed SearchResponse record)
     * so this module doesn't depend on product-query at compile time.
     */
    public SearchResult search(String q, String category, String brand,
                               Double minPrice, Double maxPrice, Boolean inStock,
                               int page, int size, boolean withFacets) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromHttpUrl(props.getProductQueryUrl())
                .path("/products/search/query")
                .queryParam("page", page)
                .queryParam("size", size);
        if (q != null && !q.isBlank())        uri.queryParam("q", q);
        if (category != null && !category.isBlank())  uri.queryParam("category", category);
        if (brand != null && !brand.isBlank())        uri.queryParam("brand", brand);
        if (minPrice != null)                 uri.queryParam("minPrice", minPrice);
        if (maxPrice != null)                 uri.queryParam("maxPrice", maxPrice);
        if (Boolean.TRUE.equals(inStock))     uri.queryParam("inStock", true);
        if (withFacets)                       uri.queryParam("aggregations", "category,brand,price");

        try {
            JsonNode body = rest.getForObject(uri.toUriString(), JsonNode.class);
            return parse(body, page, size);
        } catch (RuntimeException e) {
            // product-query may be absent (minimal profile) or temporarily down.
            // Fall back to an empty result so the shop still renders a usable page.
            log.warn("product-query unreachable at {} ({}), returning empty result",
                    props.getProductQueryUrl(), e.getClass().getSimpleName());
            return new SearchResult(List.of(), 0, page, size, Map.of());
        }
    }

    public ProductView byId(String id) {
        try {
            JsonNode node = rest.getForObject(
                    props.getProductQueryUrl() + "/products/search/" + id, JsonNode.class);
            return node == null || node.isMissingNode() ? null : toProduct(node);
        } catch (RuntimeException e) {
            log.debug("product-query byId failed for {}: {}", id, e.toString());
            return null;
        }
    }

    private static SearchResult parse(JsonNode body, int page, int size) {
        if (body == null) return new SearchResult(List.of(), 0, page, size, Map.of());

        List<ProductView> products = new ArrayList<>();
        JsonNode hits = body.path("hits");
        for (JsonNode h : hits) {
            JsonNode doc = h.path("doc");
            if (!doc.isMissingNode()) products.add(toProduct(doc));
        }

        long total = body.path("total").asLong(products.size());

        Map<String, List<FacetBucket>> facets = new LinkedHashMap<>();
        JsonNode facetsNode = body.path("facets");
        if (facetsNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = facetsNode.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                List<FacetBucket> list = new ArrayList<>();
                for (JsonNode b : e.getValue()) {
                    list.add(new FacetBucket(b.path("key").asText(), b.path("count").asLong(0)));
                }
                facets.put(e.getKey(), list);
            }
        }
        return new SearchResult(products, total, page, size, facets);
    }

    private static ProductView toProduct(JsonNode d) {
        return new ProductView(
                d.path("id").asText(null),
                d.path("name").asText(null),
                d.path("description").asText(null),
                d.hasNonNull("price") ? d.path("price").asDouble() : null,
                d.hasNonNull("stock") ? d.path("stock").asInt() : null,
                d.path("category").asText(null),
                d.path("brand").asText(null)
        );
    }
}
