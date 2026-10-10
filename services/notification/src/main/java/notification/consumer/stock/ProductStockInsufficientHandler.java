package notification.consumer.stock;

import com.example.common.event.ProductStockInsufficientEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * Cloud Stream consumer for product.stock.insufficient → admin alert that an
 * order could not be fulfilled. Real impl would also notify the customer.
 */
@Configuration
public class ProductStockInsufficientHandler {

    private static final Logger log = LoggerFactory.getLogger(ProductStockInsufficientHandler.class);

    @Bean
    public Consumer<ProductStockInsufficientEvent> productStockInsufficient() {
        return event -> log.warn(
                "ADMIN ALERT | order rejected | orderId={} productId={} name='{}' requested={} available={} eventId={}",
                event.orderId(), event.productId(), event.productName(),
                event.requested(), event.available(), event.eventId());
    }
}
