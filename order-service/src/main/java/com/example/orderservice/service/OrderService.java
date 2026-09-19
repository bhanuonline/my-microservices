package com.example.orderservice.service;

import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.saga.OrderSagaOrchestrator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * NOTE ON PATTERN CHANGE:
 * Previously this class published an OrderCreatedEvent (choreography style).
 * Now it delegates to the saga orchestrator (orchestration style).
 *
 * The old choreography wiring (OrderEventPublisher, PaymentEventHandler,
 * PaymentEventProcessor) is left in place — it still runs in parallel.
 * In a real refactor you'd pick one and delete the other.
 */
@Service
public class OrderService {

    private static final BigDecimal UNIT_PRICE = new BigDecimal("9.99");

    private final OrderRepository orderRepo;
    private final OrderSagaOrchestrator saga;

    public OrderService(OrderRepository orderRepo, OrderSagaOrchestrator saga) {
        this.orderRepo = orderRepo;
        this.saga = saga;
    }

    @Transactional
    public Order create(Long productId, Integer quantity) {
        String orderId = UUID.randomUUID().toString();
        BigDecimal amount = UNIT_PRICE.multiply(BigDecimal.valueOf(quantity));

        Order order = orderRepo.save(new Order(orderId, productId, quantity, amount));

        // Kick off the saga. It will drive payment → notify → completion.
        saga.start(orderId, amount);

        return order;
    }
}
