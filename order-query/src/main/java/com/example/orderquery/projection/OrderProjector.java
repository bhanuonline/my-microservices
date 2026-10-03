package com.example.orderquery.projection;

import com.example.common.event.OrderCreatedEvent;
import com.example.orderquery.model.OrderDoc;
import com.example.orderquery.repo.OrderSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Builds the Elasticsearch read model from the order.created event stream.
 *
 * Idempotency: ElasticsearchRepository.save() upserts by @Id; a replay of the
 * same event rewrites the same doc. For richer multi-event projection (status
 * transitions, payment outcomes, refunds), use IdempotencyGuard — but for
 * create-only upserts the save-is-upsert property suffices.
 *
 * Consumer group `order-query-projector` is private to this service — the
 * projector gets its own offset separate from analytics / notification / etc.
 */
@Component
public class OrderProjector {

    private static final Logger log = LoggerFactory.getLogger(OrderProjector.class);

    private final OrderSearchRepository repo;

    public OrderProjector(OrderSearchRepository repo) {
        this.repo = repo;
    }

    @KafkaListener(
            topics = "order.created",
            groupId = "order-query-projector",
            containerFactory = "orderCreatedListenerFactory"
    )
    public void onOrderCreated(OrderCreatedEvent event) {
        OrderDoc doc = new OrderDoc(
                event.orderId(),
                event.productId(),
                event.quantity(),
                event.amount(),
                "CREATED",
                event.createdAt(),
                Instant.now()
        );
        repo.save(doc);
        log.debug("projected order {} into read model", event.orderId());
    }
}
