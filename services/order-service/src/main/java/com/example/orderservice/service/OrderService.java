package com.example.orderservice.service;

import com.example.common.event.OrderCreatedEvent;
import com.example.orderservice.model.Order;
import com.example.orderservice.outbox.OutboxWriter;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.saga.OrderSagaOrchestrator;
import io.micrometer.tracing.annotation.NewSpan;
import io.micrometer.tracing.annotation.SpanTag;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Creates orders and triggers two things atomically with the DB write:
 *   1) outbox row for `order.created` → relayed to Kafka → consumed by
 *      product-service (stock), analytics, etc.
 *   2) saga orchestrator kickoff → drives payment → notify → completion.
 *
 * The outbox write and the Order insert share one @Transactional, so there is
 * no dual-write risk: if the tx rolls back, no event is leaked.
 */
@Service
public class OrderService {

    private static final BigDecimal UNIT_PRICE = new BigDecimal("9.99");
    private static final String TOPIC_ORDER_CREATED = "order.created";

    private final OrderRepository orderRepo;
    private final OrderSagaOrchestrator saga;
    private final OutboxWriter outboxWriter;

    public OrderService(OrderRepository orderRepo, OrderSagaOrchestrator saga, OutboxWriter outboxWriter) {
        this.orderRepo = orderRepo;
        this.saga = saga;
        this.outboxWriter = outboxWriter;
    }

    @NewSpan("order.create")
    @Transactional
    public Order create(@SpanTag("order.productId") Long productId,
                        @SpanTag("order.quantity") Integer quantity) {
        return create(productId, quantity, null);
    }

    /**
     * Creates an order and starts the saga, routing payment to the named
     * provider. {@code provider} may be null to let payment-service pick
     * the configured default ({@code payment.default-provider}).
     *
     * <p>Used by {@code CheckoutController} when a client wants to pay with
     * a specific provider (Stripe / Razorpay / PayPal). The existing
     * {@code POST /api/v1/orders} path (no provider) continues to work.
     */
    @NewSpan("order.create")
    @Transactional
    public Order create(@SpanTag("order.productId") Long productId,
                        @SpanTag("order.quantity") Integer quantity,
                        @SpanTag("order.provider") String provider) {
        String orderId = UUID.randomUUID().toString();
        BigDecimal amount = UNIT_PRICE.multiply(BigDecimal.valueOf(quantity));

        Order order = orderRepo.save(new Order(orderId, productId, quantity, amount));

        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID(), orderId, productId, quantity, amount, Instant.now());
        outboxWriter.write("order", TOPIC_ORDER_CREATED, event);

        saga.start(orderId, amount, provider);

        return order;
    }
}
