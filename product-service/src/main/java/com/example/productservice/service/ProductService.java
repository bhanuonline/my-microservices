package com.example.productservice.service;

import com.example.common.event.ProductUpdatedEvent;
import com.example.productservice.exception.ProductNotFoundException;
import com.example.productservice.model.Product;
import com.example.productservice.outbox.OutboxWriter;
import com.example.productservice.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Cache-aside read path: @Cacheable turns getProductById into a Redis-first
 * lookup. On miss, hits the DB and populates the cache. @CacheEvict on
 * mutations keeps stale entries out.
 *
 * The getAllProducts call is deliberately NOT cached — list queries explode
 * cache entries (one per combination of filters you'll ever add) and are
 * easy to make stale.
 */
@Service
@Slf4j
public class ProductService {

    public static final String PRODUCTS_CACHE = "products";

    /** Kafka topic consumed by product-query (Elasticsearch projector) + any other read models. */
    public static final String TOPIC_PRODUCT_UPDATED = "product.updated";

    /** Aggregate tag used by the OutboxEventRouter SMT if we ever switch to Debezium. */
    private static final String AGGREGATE_TYPE = "product";

    private final ProductRepository productRepository;
    private final OutboxWriter outboxWriter;

    public ProductService(ProductRepository productRepository, OutboxWriter outboxWriter) {
        this.productRepository = productRepository;
        this.outboxWriter = outboxWriter;
    }

    public List<Product> getAllProducts() {
        log.info("Fetching all products");
        return productRepository.findAll();
    }

    /**
     * Create or update one product. Write + outbox row commit in the same tx —
     * no dual-write risk. Relay (polling OR Debezium) publishes to Kafka later.
     */
    @Transactional
    @CacheEvict(value = PRODUCTS_CACHE, allEntries = true)
    public Product saveProduct(Product product) {
        log.info("Saving product: {}", product.getName());
        Product saved = productRepository.save(product);
        outboxWriter.write(AGGREGATE_TYPE, TOPIC_PRODUCT_UPDATED, toEvent(saved, false));
        return saved;
    }

    @Transactional
    @CacheEvict(value = PRODUCTS_CACHE, allEntries = true)
    public List<Product> saveAllProducts(List<Product> products) {
        log.info("Saving {} products in bulk", products.size());
        List<Product> saved = productRepository.saveAll(products);
        // One outbox row per product — downstream projectors upsert individually.
        saved.forEach(p -> outboxWriter.write(AGGREGATE_TYPE, TOPIC_PRODUCT_UPDATED, toEvent(p, false)));
        return saved;
    }

    /**
     * Soft-delete-aware removal: emits an event with deleted=true so downstream
     * read models (ES product-query) can remove the doc. If you add a hard
     * delete endpoint, keep this same pattern — the event is what matters, not
     * whether the row survives in MySQL.
     */
    @Transactional
    @CacheEvict(value = PRODUCTS_CACHE, allEntries = true)
    public void deleteProduct(Long id) {
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        productRepository.deleteById(id);
        outboxWriter.write(AGGREGATE_TYPE, TOPIC_PRODUCT_UPDATED, toEvent(existing, true));
    }

    @Cacheable(value = PRODUCTS_CACHE, key = "#id")
    public Product getProductById(Long id) {
        log.info("Cache MISS — fetching product with ID {} from DB", id);
        return productRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Product with ID {} not found", id);
                    return new ProductNotFoundException(id);
                });
    }

    private static ProductUpdatedEvent toEvent(Product p, boolean deleted) {
        LocalDateTime ts = p.getUpdatedAt() != null ? p.getUpdatedAt() : LocalDateTime.now();
        return new ProductUpdatedEvent(
                UUID.randomUUID(),
                p.getId(),
                p.getName(),
                p.getDescription(),
                BigDecimal.valueOf(p.getPrice()),
                p.getQuantityInStock(),
                p.getCategory(),
                p.getBrand(),
                deleted,
                ts.toInstant(ZoneOffset.UTC)
        );
    }
}