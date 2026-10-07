package project.usingjava8.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Order {
    Cart cart;
    OrderStatus orderStatus;
    int total;
    String orderID;
}
