package com.example.productquery.projection;

import com.example.common.event.ProductUpdatedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * JSON consumer dedicated to ProductUpdatedEvent. isolation.level=read_committed
 * pairs with product-service's transactional outbox producer — we only see events
 * after their outbox-publishing Kafka transaction commits.
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, ProductUpdatedEvent> productUpdatedConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        JsonDeserializer<ProductUpdatedEvent> value = new JsonDeserializer<>(ProductUpdatedEvent.class);
        value.addTrustedPackages("com.example.common.event");
        value.setUseTypeHeaders(false);        // outbox stores raw JSON; no __TypeId__ header to honour

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), value);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ProductUpdatedEvent> productUpdatedListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ProductUpdatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(productUpdatedConsumerFactory());
        return factory;
    }
}
