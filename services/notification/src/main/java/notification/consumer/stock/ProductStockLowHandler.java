package notification.consumer.stock;

import com.example.common.event.ProductStockLowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * Cloud Stream consumer for product.stock.low → send admin alert.
 * Demo-grade: logs loudly. Real impl would call EmailGateway.
 */
@Configuration
public class ProductStockLowHandler {

    private static final Logger log = LoggerFactory.getLogger(ProductStockLowHandler.class);

    @Bean
    public Consumer<ProductStockLowEvent> productStockLow() {
        return event -> {
            log.warn("ADMIN ALERT | low stock | productId={} name='{}' remaining={} threshold={} eventId={}",
                    event.productId(), event.productName(), event.remainingStock(),
                    event.threshold(), event.eventId());
        };
    }
}
