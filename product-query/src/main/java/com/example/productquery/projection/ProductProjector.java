package com.example.productquery.projection;

import com.example.common.event.ProductUpdatedEvent;
import com.example.productquery.model.ProductDoc;
import com.example.productquery.repo.ProductSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Builds the Elasticsearch read model from product.updated events.
 *
 * Idempotency: ElasticsearchRepository.save() upserts by @Id, so replays of
 * the same event rewrite the same doc. For stricter dedup (counts, side
 * effects), wrap in IdempotencyGuard — not needed here.
 *
 * Delete handling: event.deleted() == true  → remove the doc from ES.
 */
@Component
public class ProductProjector {

    private static final Logger log = LoggerFactory.getLogger(ProductProjector.class);

    private final ProductSearchRepository repo;

    public ProductProjector(ProductSearchRepository repo) {
        this.repo = repo;
    }

    @KafkaListener(
            topics = "product.updated",
            groupId = "product-query-projector",
            containerFactory = "productUpdatedListenerFactory"
    )
    public void on(ProductUpdatedEvent event) {
        if (event.deleted()) {
            repo.deleteById(String.valueOf(event.productId()));
            log.info("projected delete productId={}", event.productId());
            return;
        }
        ProductDoc doc = ProductDoc.fromEvent(
                event.productId(), event.name(), event.description(),
                event.price(), event.stock(),
                event.category(), event.brand(),
                false, event.updatedAt()
        );
        repo.save(doc);
        log.debug("projected upsert productId={}", event.productId());
    }
}
