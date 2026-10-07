package project.usingjava8.util;

import project.usingjava8.model.Cart;
import project.usingjava8.model.CartItem;
import project.usingjava8.model.Product;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.*;
import java.util.stream.Collectors;

public class CartUtils {

    Function<Double, Double> flatDiscount = total -> Math.max(0, total - 500);

    BiFunction<Double, Double, Double> percentageDiscount = (total, percentage) -> total - (total * percentage / 100);

    BiFunction<CartItem, Double, Double> bulkDiscount = (item, percentage) -> {

        double total = item.getProduct().getPrice() * item.getQnty();

        if (item.getQnty() >= 5) {
            return total - (total * percentage / 100);
        }

        return total;
    };

    Function<Double, Double> addGST = total -> total + (total * 18 / 100);

    Function<Double, Double> doublePrice = total -> total * 2;

    BiFunction<Double, Double, Double> flatDiscountNew = (total, discount) -> total - discount;

    BiFunction<Double, Integer, Double> totalPrice = (price, qnty) -> price * qnty;

    Predicate<Double> eligible = price -> price > 5000;

    Predicate<CartItem> bulkEligible = item -> item.getQnty() >= 4;

    Consumer<Product> printer =
            product -> System.out.println(product.getName());

    Consumer<Double> printTotal = aDouble -> System.out.println(aDouble);

    Supplier<LocalDateTime> now = LocalDateTime::now;

    Function<Double, Double> shipping = total -> total + 100;

    Function<Double, Double> freeShipping =
            total -> total > 1000 ? total : total + 100;

    Function<List<CartItem>, Double> cartTotal =
            cartItems -> cartItems.stream()
                    .mapToDouble(item -> item.getQnty() * item.getProduct().getPrice())
                    .sum();
    Consumer<Cart> printCart = cart ->
            System.out.println(
                    "Cart Id : " + cart.getCartId()
                            + ", Total Items : " + cart.getItems().size()
                            + ", Cart Total : " + cartTotal.apply(cart.getItems())
            );
    Function<List<CartItem>, Optional<CartItem>> maxPriceItem =
            cartItems -> cartItems.stream()
                    .max(Comparator.comparingDouble(item -> item.getProduct().getPrice()));

    Function<List<CartItem>, Integer> totalQuantity = cartItems -> cartItems.stream().mapToInt(CartItem::getQnty).sum();

    Function<List<CartItem>, Map<String, List<CartItem>>> groupByCategory = cartItems -> cartItems.stream()
            .collect(Collectors.groupingBy(item -> item.getProduct().getCategory()));

    public static Predicate<CartItem> isInStock() {
        return item -> item.getProduct().getStock() > 0;
    }

}