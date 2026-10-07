package project.usingjava8.service;

import project.usingjava8.model.Cart;
import project.usingjava8.model.Order;
import project.usingjava8.model.OrderStatus;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class OrderService {

    Supplier<String> idGenSupplier = () -> "ORD-" + System.currentTimeMillis();

    public Order placeOrder() {
        //
        CartService cartService = new CartService();
        cartService.cartTotal();

        Order order = Order.builder().cart(cartService.getCart().get()).orderStatus(OrderStatus.PENDING)
                .orderID(idGenSupplier.get())
                .build();
        // Consumer — print receipt
        Consumer<Order> receipt = o -> {
            System.out.println("\n===== RECEIPT =====");
            System.out.println("Order ID : " + o.getOrderID());
            o.getCart().getItems().forEach(i ->
                    System.out.printf("  %-15s x%d  ₹%.2f%n",
                            i.getProduct().getName(), i.getQnty(), o.getTotal()));
            System.out.printf("Total    : ₹%.2f%n", o.getTotal());
            System.out.println("===================");
        };
        receipt.accept(order);
        return order;

    }

    // groupingBy — group orders by status
    Map<OrderStatus, List<Order>> getOrderByStatus(List<Order> orders) {
        return orders.stream().collect(Collectors.groupingBy(Order::getOrderStatus));
    }
    // flatMap — all items across all orders

    // merge — most ordered products (frequency map)

    // Optional — safe order lookup
}
