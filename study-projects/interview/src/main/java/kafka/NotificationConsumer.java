package kafka;

import org.apache.kafka.clients.consumer.*;
import java.time.Duration;
import java.util.*;

public class NotificationConsumer {
    public static void main(String[] args) {
        Properties props = new Properties();
        props.put("bootstrap.servers",  "localhost:9092");
        props.put("group.id",           "notifications-group"); // consumer group
        props.put("auto.offset.reset",  "earliest");            // start from beginning if no prior offset
        props.put("key.deserializer",   "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of("order-events"));

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                for (ConsumerRecord<String, String> record : records) {
                    System.out.printf("partition=%d offset=%d key=%s value=%s%n",
                        record.partition(), record.offset(), record.key(), record.value());
                }
            }
        }
    }
}