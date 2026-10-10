package com.example.paymentservice.consumer;

import com.example.common.event.OrderCreatedEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * Cloud Stream binding. Real work is delegated to {@link OrderCreatedProcessor}
 * so we get @Transactional + consumer-side dedup.
 */
@Configuration
public class OrderCreatedHandler {

    @Bean
    public Consumer<OrderCreatedEvent> orderCreated(OrderCreatedProcessor processor) {
        return processor::handle;
    }
}
