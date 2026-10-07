package com.fastapi.service;

import com.fastapi.exception.NotFoundException;
import com.fastapi.model.Product;
import com.fastapi.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * HOT PATH — called thousands of times per minute.
     *
     * Flow:
     *   1st call  → misses cache → hits DB → stores in Redis (~15ms)
     *   2nd+ call → hits Redis cache → returns immediately (~1ms)
     *
     * @Cacheable checks Redis before executing the method body.
     */
    @Cacheable(value = "products", key = "#id")
    @Transactional(readOnly = true)   // readOnly skips dirty-checking — faster
    public Product getById(Long id) {
        log.debug("Cache miss — fetching product {} from DB", id);
        return productRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Product not found: " + id));
    }

    /**
     * HOT PATH — list by category (also cached)
     * Key includes category so each category has its own cache entry.
     */
    @Cacheable(value = "products", key = "'category:' + #category")
    @Transactional(readOnly = true)
    public List<Product> getByCategory(String category) {
        log.debug("Cache miss — fetching category {} from DB", category);
        return productRepository.findActiveByCategory(category);
    }

    /**
     * On create — don't cache yet, just save.
     * The cache will be populated on the first GET call.
     */
    @Transactional
    public Product create(Product product) {
        return productRepository.save(product);
    }

    /**
     * On update — @CachePut updates the cache with the new value.
     * Other requests won't see stale data.
     */
    @CachePut(value = "products", key = "#product.id")
    @CacheEvict(value = "products", key = "'category:' + #product.category")
    @Transactional
    public Product update(Product product) {
        if (!productRepository.existsById(product.getId())) {
            throw new NotFoundException("Product not found: " + product.getId());
        }
        return productRepository.save(product);
    }

    /**
     * On delete — @CacheEvict removes the entry from Redis immediately.
     * Subsequent GETs will go to DB and return 404.
     */
    @CacheEvict(value = "products", key = "#id")
    @Transactional
    public void delete(Long id) {
        productRepository.deleteById(id);
        log.info("Product {} deleted and cache evicted", id);
    }

}
