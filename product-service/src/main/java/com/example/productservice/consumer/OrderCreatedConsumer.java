package com.example.productservice.consumer;

import com.example.common.event.OrderCreatedEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * Cloud Stream binding: order.created → StockDecrementProcessor.
 */
@Configuration
public class OrderCreatedConsumer {

    @Bean
    public Consumer<OrderCreatedEvent> orderCreated(StockDecrementProcessor processor) {
        return processor::handle;
    }
}
