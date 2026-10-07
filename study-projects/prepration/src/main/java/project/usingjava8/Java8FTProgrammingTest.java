package project.usingjava8;

import project.usingjava8.model.Cart;
import project.usingjava8.model.CartItem;
import project.usingjava8.model.Product;
import project.usingjava8.service.CartService;

import java.time.LocalDateTime;
import java.util.List;

public class Java8FTProgrammingTest {
    public static void main(String[] args) {
        // products
        Product p1 = new Product("Apple iPhone",   "Electronics", 79999, 10,100, LocalDateTime.now());
        Product p2 = new Product("Nike Shoes",      "Footwear",    4999,  5,101,LocalDateTime.now());
        Product p3 = new Product("Samsung TV",      "Electronics", 54999, 3,200,LocalDateTime.now());
        Product p4 = new Product("Levi's Jeans",    "Clothing",    2999,  20,300,LocalDateTime.now());
        Product p5 = new Product("Apple MacBook",   "Electronics", 129999, 7,400,LocalDateTime.now());

        CartService cartService=new CartService();
        CartItem item1=CartItem.builder().product(p1).qnty(1).build();
        cartService.addToCart(item1);
        CartItem item2=CartItem.builder().product(p2).qnty(2).build();
        cartService.addToCart(item2);
        cartService.getCart();

        //cartService.clearCart();
        cartService.getCart();
        cartService.removeItem(100);


        CartItem item3=CartItem.builder().product(p3).qnty(2).build();
        cartService.addToCart(item3);
        cartService.printCart();
    }
}
