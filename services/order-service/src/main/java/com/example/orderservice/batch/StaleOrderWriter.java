package com.example.orderservice.batch;

import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

public class StaleOrderWriter implements ItemWriter<Order> {

    private static final Logger log = LoggerFactory.getLogger(StaleOrderWriter.class);

    private final OrderRepository orderRepository;

    public StaleOrderWriter(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public void write(Chunk<? extends Order> chunk) {
        orderRepository.saveAll(chunk.getItems());
        log.info("StaleOrderWriter: persisted {} cancelled orders", chunk.size());
    }
}
