package com.example.productquery.repo;

import com.example.productquery.model.ProductDoc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * Derived queries get Spring to translate method names into ES queries.
 * For richer searches (bool + filter + aggs) Phase 3 moves to NativeQueryBuilder.
 */
public interface ProductSearchRepository extends ElasticsearchRepository<ProductDoc, String> {
    Page<ProductDoc> findByDeletedFalse(Pageable pageable);
    Page<ProductDoc> findByNameContainingIgnoreCaseAndDeletedFalse(String name, Pageable pageable);
}
