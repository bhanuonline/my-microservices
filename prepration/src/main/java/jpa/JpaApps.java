package jpa;

import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import lombok.Data;
import lombok.ToString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "users")
@Data
@ToString
class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String email;
    private int age;

    // @Embedded = "take all fields from Address
    //              and put them in THIS table"
    @Embedded
    private Address address;

    // ONE user has many orders
    // mappedBy = "user" → points to the field name in Order.java
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Order> orders = new ArrayList<>();

    // constructors
    public User() {}

    public User(String name, String email, int age) {
        this.name = name;
        this.email = email;
        this.age = age;
    }

    // helper method to link both sides
    public void addOrder(Order order) {
        orders.add(order);
        order.setUser(this);   // keeps both sides in sync
    }
}

@Entity
@Table(name = "orders")
@Data
@ToString
class Order {
    public Order(User user) {
        this.user=user;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String item;

    private double price;

    // Many orders belong to ONE user
    // This side OWNS the relationship (has the foreign key column)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")   // creates user_id column in orders table
    private User user;

    @ManyToMany
    @JoinTable(
            name = "order_product",
            joinColumns = @JoinColumn(name = "order_id"),
            inverseJoinColumns = @JoinColumn(name = "product_id")
    )
    private List<Product> products = new ArrayList<>();

    public Order() {}

    public Order(String item, double price, User user) {
        this.item = item;
        this.price = price;
        this.user = user;
    }

    // helper method
    public void addProduct(Product product) {
        products.add(product);
        product.getOrders().add(this); // keep both sides in sync
    }
}

@Entity
@Table(name = "products")
@Data
@ToString
class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private double price;

    // Inverse side
    @ManyToMany(mappedBy = "products")
    private List<Order> orders = new ArrayList<>();

    public Product() {}
    public Product(String name, double price) {
        this.name = name;
        this.price = price;
    }
}

// @Embeddable = "I am not an entity, I don't have my own table
//                I live inside another entity's table"
@Embeddable
@Data
@ToString
class Address {

    private String street;
    private String city;
    private String state;
    private String zipcode;

    // NO @Id needed — this is not an entity!

    public Address() {}

    public Address(String street, String city, String state, String zipcode) {
        this.street = street;
        this.city = city;
        this.state = state;
        this.zipcode = zipcode;
    }
}

// Just declare the interface — Spring generates the implementation!
interface UserRepository extends JpaRepository<User, Long> {

    // Spring generates this query automatically from method name
    List<User> findByName(String name);
    Optional<User> findByEmail(String email);
    List<User> findByAgeGreaterThan(int age);

}

interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("""
       SELECT o
       FROM Order o
       JOIN FETCH o.user
       WHERE o.user.id = :userId
       """)
    // find all orders for a specific user
    List<Order> findByUserId(Long userId);

    // find orders above a certain price
    List<Order> findByPriceGreaterThan(double price);

    // find orders by item name and user
    List<Order> findByItemAndUserId(String item, Long userId);
}

interface ProductRepository extends  JpaRepository<Product, Long>{

}

@Service
class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;


    // CREATE
    public User createUser(String name, String email, int age) {
        User user = new User(name, email, age);
        return userRepository.save(user);  // INSERT INTO users...
    }

    // READ - find one
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // READ - find all
    public List<User> getAllUsers() {
        return userRepository.findAll();  // SELECT * FROM users
    }

    // UPDATE
    public User updateUser(Long id, String newName) {
        User user = getUserById(id);
        user.setName(newName);
        return userRepository.save(user);  // UPDATE users SET name=...
    }

    // DELETE
    public void deleteUser(Long id) {
        userRepository.deleteById(id);  // DELETE FROM users WHERE id=...
    }

    // CUSTOM QUERY
    public List<User> getUsersOlderThan(int age) {
        return userRepository.findByAgeGreaterThan(age);
    }
}

@Service
class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired private ProductRepository productRepository;

    // Create order for a user
    @Transactional
    public Order createOrder(Long userId, String item, double price) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Order order = new Order(item, price, user);
        user.addOrder(order);

        return orderRepository.save(order);
        // INSERT INTO orders (item, price, user_id) VALUES (...)
    }

    // Get all orders for a user
    public List<Order> getOrdersByUser(Long userId) {
        return orderRepository.findByUserId(userId);
        // SELECT * FROM orders WHERE user_id = ?
    }

    // Get user WITH all their orders (solves N+1)
    @Transactional
    public User getUserWithOrders(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.getOrders().size(); // triggers LAZY load inside transaction
        return user;
    }

    // Delete an order
    public void deleteOrder(Long orderId) {
        orderRepository.deleteById(orderId);
        // DELETE FROM orders WHERE id = ?
    }

    // Create empty order for a user
    @Transactional
    public Order createOrder(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Order order = new Order(user);
        return orderRepository.save(order);
        // INSERT INTO orders (user_id) VALUES (1)
    }

    // Add product to order
    @Transactional
    public void addProductToOrder(Long orderId, Long productId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        order.addProduct(product);
        orderRepository.save(order);
        // INSERT INTO order_product (order_id, product_id) VALUES (?, ?)
    }

    // Get all products in an order
    @Transactional
    public List<Product> getProductsInOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        return order.getProducts();
        // SELECT p.* FROM products p
        // INNER JOIN order_product op ON p.id = op.product_id
        // WHERE op.order_id = ?
    }

    // Get all orders containing a product
    @Transactional
    public List<Order> getOrdersWithProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        return product.getOrders();
    }

    // Remove product from order
    @Transactional
    public void removeProductFromOrder(Long orderId, Long productId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        order.getProducts().remove(product);
        product.getOrders().remove(order);
        orderRepository.save(order);
        // DELETE FROM order_product WHERE order_id=? AND product_id=?
    }
}

@SpringBootApplication
public class JpaApps implements CommandLineRunner {

    @Autowired
    private UserService userService;

    @Autowired
    private OrderService orderService;

    @Autowired
    ProductRepository productRepository;

    public static void main(String[] args) {
        SpringApplication.run(JpaApps.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // CREATE users
        User alice = userService.createUser("Alice", "alice@gmail.com", 25);
        Address address = new Address("123 Main St", "Delhi", "DL", "110001");
        alice.setAddress(address);
        User bob   = userService.createUser("Bob",   "bob@gmail.com",   30);
        Address address1 = new Address("Behati Amauli Fatehpur", "KANPUR", "UP", "212631");
        bob.setAddress(address1);

        // CREATE products
        Product laptop   = productRepository.save(new Product("Laptop",   999.99));
        Product mouse    = productRepository.save(new Product("Mouse",      29.99));
        Product keyboard = productRepository.save(new Product("Keyboard",   79.99));
        Product phone    = productRepository.save(new Product("Phone",     499.99));

        // CREATE orders
        Order order1 = orderService.createOrder(alice.getId()); // Alice's order
        Order order2 = orderService.createOrder(alice.getId()); // Alice's 2nd order
        Order order3 = orderService.createOrder(bob.getId());   // Bob's order


        // ADD products to orders
        orderService.addProductToOrder(order1.getId(), laptop.getId());
        orderService.addProductToOrder(order1.getId(), mouse.getId());
        orderService.addProductToOrder(order1.getId(), keyboard.getId());

        orderService.addProductToOrder(order2.getId(), laptop.getId()); // laptop in 2 orders!
        orderService.addProductToOrder(order2.getId(), phone.getId());

        orderService.addProductToOrder(order3.getId(), mouse.getId());  // mouse in 2 orders!

        // GET products in order1
        System.out.println("\n--- Products in Order 1 ---");
        orderService.getProductsInOrder(order1.getId())
                .forEach(System.out::println);
        // Laptop, Mouse, Keyboard

        // GET all orders containing Laptop
        System.out.println("\n--- Orders containing Laptop ---");
        orderService.getOrdersWithProduct(laptop.getId())
                .forEach(System.out::println);
        // Order1, Order2

        // REMOVE mouse from order1
        orderService.removeProductFromOrder(order1.getId(), mouse.getId());
        System.out.println("\n--- Order 1 after removing Mouse ---");
        orderService.getProductsInOrder(order1.getId())
                .forEach(System.out::println);
        // GET all orders for Alice
        System.out.println("\n--- Alice's Orders ---");
        orderService.getOrdersByUser(alice.getId())
                .forEach(System.out::println);

        // GET user WITH orders
        System.out.println("\n--- Alice with Orders ---");
        User aliceWithOrders = orderService.getUserWithOrders(alice.getId());
        System.out.println("User: " + aliceWithOrders.getName());
        System.out.println("Orders: " + aliceWithOrders.getOrders());

        // DELETE one order
        orderService.deleteOrder(1L);
        System.out.println("\n--- Alice's Orders after delete ---");
        orderService.getOrdersByUser(alice.getId())
                .forEach(System.out::println);
    }
}
