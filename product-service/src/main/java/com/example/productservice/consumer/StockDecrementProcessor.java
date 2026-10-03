package com.example.productservice.consumer;

import com.example.common.event.OrderCreatedEvent;
import com.example.common.event.ProductStockInsufficientEvent;
import com.example.common.event.ProductStockLowEvent;
import com.example.common.idempotency.IdempotencyGuard;
import com.example.productservice.model.Product;
import com.example.productservice.outbox.OutboxWriter;
import com.example.productservice.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Reacts to OrderCreatedEvent by decrementing stock. Emits ProductStockLowEvent
 * via the outbox when post-decrement stock dips below the configured threshold.
 *
 * Dedup via {@link IdempotencyGuard} — the claim runs in its own tx (REQUIRES_NEW)
 * so a replay cleanly skips without poisoning this handler's transaction.
 */
@Service
public class StockDecrementProcessor {

    private static final Logger log = LoggerFactory.getLogger(StockDecrementProcessor.class);
    private static final String CONSUMER_NAME = "product-service.orderCreated";
    private static final String TOPIC_STOCK_LOW = "product.stock.low";
    private static final String TOPIC_STOCK_INSUFFICIENT = "product.stock.insufficient";

    private final ProductRepository productRepo;
    private final IdempotencyGuard guard;
    private final OutboxWriter outboxWriter;
    private final int lowStockThreshold;

    public StockDecrementProcessor(ProductRepository productRepo,
                                   IdempotencyGuard guard,
                                   OutboxWriter outboxWriter,
                                   @Value("${product.stock.low-threshold:5}") int lowStockThreshold) {
        this.productRepo = productRepo;
        this.guard = guard;
        this.outboxWriter = outboxWriter;
        this.lowStockThreshold = lowStockThreshold;
    }

    @Transactional
    public void handle(OrderCreatedEvent event) {
        if (!guard.claim(event.eventId(), CONSUMER_NAME)) {
            log.info("Skipping duplicate OrderCreated eventId={} orderId={}", event.eventId(), event.orderId());
            return;
        }

        Product product = productRepo.findById(event.productId()).orElse(null);
        if (product == null) {
            log.warn("OrderCreated for unknown productId={} orderId={} — nothing to decrement",
                    event.productId(), event.orderId());
            return;
        }

        // Overflow guard — never let stock go negative. Emit a rejection event
        // (via outbox so it's atomic with the dedup commit) and bail. The dedup
        // row is already written, so a replay will cleanly skip without
        // double-emitting the rejection.
        if (product.getQuantityInStock() < event.quantity()) {
            ProductStockInsufficientEvent insufficient = new ProductStockInsufficientEvent(
                    UUID.randomUUID(),
                    event.orderId(),
                    product.getId(),
                    product.getName(),
                    event.quantity(),
                    product.getQuantityInStock(),
                    Instant.now());
            outboxWriter.write("product", TOPIC_STOCK_INSUFFICIENT, insufficient);
            log.warn("Rejected order {} — productId={} requested={} available={}",
                    event.orderId(), product.getId(), event.quantity(), product.getQuantityInStock());
            return;
        }

        int newStock = product.getQuantityInStock() - event.quantity();
        product.setQuantityInStock(newStock);
        productRepo.save(product);
        log.info("Decremented stock productId={} by {} → remaining={}",
                product.getId(), event.quantity(), newStock);

        if (newStock < lowStockThreshold) {
            ProductStockLowEvent low = new ProductStockLowEvent(
                    UUID.randomUUID(),
                    product.getId(),
                    product.getName(),
                    newStock,
                    lowStockThreshold,
                    Instant.now());
            outboxWriter.write("product", TOPIC_STOCK_LOW, low);
            log.warn("LOW STOCK productId={} remaining={} threshold={}",
                    product.getId(), newStock, lowStockThreshold);
        }
    }
}
