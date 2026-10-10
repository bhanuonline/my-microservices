package com.example.bff.resolver;

import com.example.bff.client.BackendClients.OrderBackend;
import com.example.bff.client.BackendClients.ProductBackend;
import com.example.bff.model.Order;
import com.example.bff.model.Product;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Query entry points + the Order.product nested resolver.
 *
 * The nested `product` resolver uses a DataLoader keyed by Long productId.
 * For a query like `{ orders { product { name } } }` returning 50 orders,
 * all 50 productIds collect into ONE batched call, not 50 serial fetches.
 * That's the GraphQL N+1 fix every interviewer asks about.
 */
@Controller
public class OrderResolver {

    private final OrderBackend orderBackend;
    private final ProductBackend productBackend;

    public OrderResolver(OrderBackend orderBackend, ProductBackend productBackend,
                        BatchLoaderRegistry registry) {
        this.orderBackend = orderBackend;
        this.productBackend = productBackend;

        // Register the product batch loader at startup. Spring GraphQL hands the
        // DataLoader to resolvers via DataFetchingEnvironment at query time.
        // Spring Graphql's BatchLoaderRegistry.registerBatchLoader expects a
        // Flux<V> (one emission per key). Preserve order with concatMap.
        registry.<Long, Product>forName("productLoader")
                .registerBatchLoader((ids, env) ->
                        Flux.fromIterable(ids).concatMap(productBackend::getById)
                );
    }

    @QueryMapping
    public Mono<Order> order(@Argument String id) {
        return orderBackend.getById(id);
    }

    @QueryMapping
    public Mono<List<Order>> orders(@Argument String status, @Argument Integer limit) {
        return orderBackend.list(status, limit);
    }

    @QueryMapping
    public Mono<Product> product(@Argument Long id) {
        return productBackend.getById(id);
    }

    @SchemaMapping(typeName = "Order", field = "product")
    public CompletableFuture<Product> resolveProduct(Order order,
                                                     org.dataloader.DataLoader<Long, Product> productLoader) {
        return productLoader.load(order.productId());
    }
}
