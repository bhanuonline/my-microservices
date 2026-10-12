package com.example.orderservice.batch;

import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.List;

// @StepScope: a fresh instance is created per step execution.
// This ensures Instant.now() is evaluated at job run time, not at startup.
@Component
@StepScope
public class StaleOrderReader implements ItemReader<Order> {

    private static final Logger log = LoggerFactory.getLogger(StaleOrderReader.class);

    private final OrderRepository orderRepository;
    private Iterator<Order> iterator;

    public StaleOrderReader(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public Order read() {
        if (iterator == null) {
            Instant cutoff = Instant.now().minus(30, ChronoUnit.MINUTES);
            List<Order> stale = orderRepository.findByStatusAndCreatedAtBefore(Order.Status.CREATED, cutoff);
            log.info("StaleOrderReader: found {} orders stuck in CREATED for >30min", stale.size());
            iterator = stale.iterator();
        }
        // Returning null signals to Spring Batch that the input is exhausted
        return iterator.hasNext() ? iterator.next() : null;
    }
}
