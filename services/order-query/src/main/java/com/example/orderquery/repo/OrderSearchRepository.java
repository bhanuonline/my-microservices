package com.example.orderquery.repo;

import com.example.orderquery.model.OrderDoc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface OrderSearchRepository extends ElasticsearchRepository<OrderDoc, String> {
    Page<OrderDoc> findByStatus(String status, Pageable pageable);
    Page<OrderDoc> findByProductId(Long productId, Pageable pageable);
}
