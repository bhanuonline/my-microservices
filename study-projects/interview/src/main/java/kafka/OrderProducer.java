package kafka;

import org.apache.kafka.clients.producer.*;
import java.util.Properties;

public class OrderProducer {
    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("key.serializer",   "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        try (Producer<String, String> producer = new KafkaProducer<>(props)) {
            ProducerRecord<String, String> record = new ProducerRecord<>(
                "order-events",   // topic
                "ord-11",         // key  → determines partition
                "{\"qty\": 2}"    // value
            );

            producer.send(record, (metadata, exception) -> {
                if (exception == null) {
                    System.out.printf("Delivered → partition=%d offset=%d%n",
                        metadata.partition(), metadata.offset());
                } else {
                    exception.printStackTrace();
                }
            });
        } // flush + close happens automatically
    }
}