package com.example.orderquery.web;

import com.example.orderquery.model.OrderDoc;
import com.example.orderquery.repo.OrderSearchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CQRS read side. All reads hit Elasticsearch, not the Postgres write store.
 * Eventually consistent — gap is Kafka latency + projection time, usually &lt; 1 s.
 */
@RestController
@RequestMapping("/orders/search")
public class OrderQueryController {

    private final OrderSearchRepository repo;

    public OrderQueryController(OrderSearchRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public Page<OrderDoc> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pr = PageRequest.of(page, size);
        if (status != null)    return repo.findByStatus(status, pr);
        if (productId != null) return repo.findByProductId(productId, pr);
        return repo.findAll(pr);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDoc> byId(@PathVariable String orderId) {
        return repo.findById(orderId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
