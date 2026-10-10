package com.example.bff.client;

import com.example.bff.model.Order;
import com.example.bff.model.Product;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Thin WebClient-based wrappers around downstream REST backends. BFF resolvers
 * call these, never a repository / DB directly.
 *
 * In a real deployment these go through api-gateway and reuse the same JWT
 * (propagated via WebClient filter) — kept simple here so the module compiles
 * and runs against the services directly.
 */
@Configuration
public class BackendClients {

    @Bean
    public WebClient orderWebClient(@Value("${bff.backends.order.url:http://order-service:8083}") String url) {
        return WebClient.builder().baseUrl(url).build();
    }

    @Bean
    public WebClient productWebClient(@Value("${bff.backends.product.url:http://product-service:8082}") String url) {
        return WebClient.builder().baseUrl(url).build();
    }

    @Bean
    public OrderBackend orderBackend(WebClient orderWebClient) {
        return new OrderBackend(orderWebClient);
    }

    @Bean
    public ProductBackend productBackend(WebClient productWebClient) {
        return new ProductBackend(productWebClient);
    }

    public static class OrderBackend {
        private final WebClient web;
        OrderBackend(WebClient web) { this.web = web; }

        public Mono<Order> getById(String id) {
            return web.get().uri("/api/v1/orders/{id}", id)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .map(m -> new Order(
                            String.valueOf(m.get("id")),
                            String.valueOf(m.get("status")),
                            String.valueOf(m.get("amount")),
                            toLong(m.get("productId"))
                    ));
        }

        public Mono<List<Order>> list(String status, Integer limit) {
            // order-service may not expose a list endpoint yet — stub returns empty.
            // Replace with a real GET once order-service grows one.
            return Mono.just(List.of());
        }
    }

    public static class ProductBackend {
        private final WebClient web;
        ProductBackend(WebClient web) { this.web = web; }

        public Mono<Product> getById(Long id) {
            return web.get().uri("/products/{id}", id)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .map(m -> new Product(
                            toLong(m.get("id")),
                            String.valueOf(m.get("name")),
                            String.valueOf(m.get("description")),
                            String.valueOf(m.get("price"))
                    ));
        }
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(o.toString()); } catch (NumberFormatException e) { return null; }
    }
}
