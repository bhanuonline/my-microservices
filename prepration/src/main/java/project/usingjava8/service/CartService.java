package project.usingjava8.service;

import project.usingjava8.model.Cart;
import project.usingjava8.model.CartItem;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;

public class CartService {
    Cart cart;
    private final Consumer<CartItem> addToCartLog =
            item -> System.out.println(" Product id : "+ item.getProduct().getSku() + " added successfully.");
    public void addToCart(CartItem item) {
        if (cart==null) {
            createCart();
        }
        cart.getItems().add(item);
        addToCartLog.accept(item);
    }

    public void createCart() {
        Cart cart = Cart.builder()
                .cartId("Cart-" + (int) (Math.random() * 100 + 2))
                .items(new ArrayList<>())
                .createdDate(LocalDateTime.now())
                .build();
        this.cart=cart;
        System.out.println("Cart Created Successfully.");
    }

    public Optional<Cart> getCart() {
        Optional<Cart> optional = Optional.ofNullable(cart);
        optional.ifPresentOrElse(
                c -> System.out.println("Cart found : " + c.getCartId()),
                () -> System.out.println("Cart is not present")
        );
        return optional;
    }

    public void printCart() {
        getCart().ifPresentOrElse(
                c -> {
                    System.out.println("\n====== Cart Details ========");
                    System.out.println("Cart Id : " + c.getCartId());
                    System.out.println("Created : " + c.getCreatedDate());

                    c.getItems().forEach(item ->
                            System.out.println("Product code :"+item.getProduct().getSku() +" , "+item.getProduct().getName()
                                    + " ,Qty : " + item.getQnty()));
                },
                () -> System.out.println("Cart is Empty.")
        );
    }

    public void clearCart() {
        getCart().ifPresentOrElse(
                c -> {
                    cart = null;
                    System.out.println("Cart cleared successfully.");
                },
                () -> System.out.println("Cart not found.")
        );
    }

    public void removeItem(int productId) {
        getCart().ifPresent(c-> cart.getItems().removeIf(item->item.getProduct().getSku()==productId));
    }

    public void filter(CartItem item) {

    }

    public double cartTotal() {
        return getCart().map(cart -> cart.getItems()
                .stream()
                .mapToDouble(item->item.getQnty()*item.getProduct().getPrice())
                .sum()).orElse(0.0);

    }

    public void applyDiscount(CartItem item) {

    }

    public void sortByName(CartItem item) {

    }

    public void getItem(CartItem item) {
    }

}
