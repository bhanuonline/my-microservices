package designpattern.Observerpattern;


import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.event.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static java.lang.Math.random;

@SpringBootApplication
public class SpringEquivalentObserverPattern {
    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(SpringEquivalentObserverPattern.class, args);
        OrderService orderService = (OrderService) context.getBean("orderService");

        orderService.orderPlace();

    }
}

class OrderPlaceEvent extends ApplicationEvent {
    OrderDate orderDate;

    public OrderPlaceEvent(Object source, OrderDate orderDate) {
        super(source);
        this.orderDate = orderDate;
    }

    public OrderDate getOrderDate() {
        return orderDate;
    }
}

@Data
class OrderDate {
    String code;
    String email;
    int qnt;
}

@Service
class OrderService {
    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    OrderRpo orderRpo;

    @Transactional
    public void orderPlace() {
        int code = (int) (Math.random() * 10 + 100);
        OrderDate orderDate = new OrderDate();
        orderDate.setCode("ORD" + code);
        orderDate.setEmail(code + "@gmail.com");
        orderDate.setQnt((int) Math.random() * 10 + 1);

        Order order = new Order();
        order.setCode(orderDate.getCode());
        order.setEmail(orderDate.getEmail());
        orderRpo.save(order);
        eventPublisher.publishEvent(new OrderPlaceEvent(this, orderDate));

        // uncomment to test @TransactionalEventListener when something error happen
        // throw new RuntimeException("DB crashed!");
    }
}


// STEP 4: Observer 2 — Inventory Service reacts
@Component
class InventoryListener {

    @Async
    @EventListener
    // Only fires if transaction SUCCEEDED
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderPlaced(OrderPlaceEvent event) {
        System.out.println("📦 Reducing inventory for order: " + event.orderDate.getCode());
    }
}

// STEP 3: Observer 1 — Email Service reacts
@Component
class EmailNotificationListener {

    // @EventListener = update() method in Observer pattern
    @Async
    @EventListener
    // Only fires if transaction SUCCEEDED
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderPlaced(OrderPlaceEvent event) {
        OrderDate orderDate = event.getOrderDate();
        System.out.println("📧 Sending email to: " + orderDate.getEmail() + " for order: " + orderDate.getCode());
    }
}

@Entity(name = "orders")
@Data
class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(nullable = false)
    private Long id;

    private String email;
    private String code;

}

@Repository
interface OrderRpo extends JpaRepository<Order, Integer> {

}

// STEP 4: Observer — fires only AFTER_ROLLBACK
@Component
class FailureAlertListener {

    // Only fires if transaction FAILED
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void handleAfterRollback(OrderPlaceEvent event) {
        System.out.println("🚨 Order FAILED, alert team! Order: " + event.getOrderDate().getCode());
    }
}

// STEP 1: Security Config — enable authentication events
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated()).formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/home", true)  // ← go to home after login
                .failureUrl("/login?error=true")   // ← go back with error flag
                .permitAll());
        return http.build();
    }

    // Dummy in-memory user for testing
    // username: user | password: 1234
    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails user = User.builder().username("user").password("{noop}1234")   // {noop} = no password encoding
                .roles("USER").build();
        return new InMemoryUserDetailsManager(user);
    }

    // ✅ This bean enables Spring to publish auth events
    @Bean
    public AuthenticationEventPublisher authenticationEventPublisher(ApplicationEventPublisher publisher) {
        return new DefaultAuthenticationEventPublisher(publisher);
    }
}

// STEP 2: Observer 1 — listens for SUCCESS
@Component
class AuthenticationSuccessListener {

    @EventListener                              // ← Observer
    public void onSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        System.out.println("✅ Login SUCCESS for user: " + username);

        // Real use cases:
        // 1. log to DB
        // 2. reset failed attempts
        // 3. record last login time
    }
}

// STEP 3: Observer 2 — listens for FAILURE
@Component
class AuthenticationFailureListener {

    private Map<String, Integer> failedAttempts = new HashMap<>();

    @EventListener                              // ← Observer
    public void onFailure(AbstractAuthenticationFailureEvent event) {

        String username = event.getAuthentication().getName();
        String reason = event.getException().getMessage();

        System.out.println("❌ Login FAILED for user: " + username + " | Reason: " + reason);

        // Count failed attempts
        failedAttempts.merge(username, 1, Integer::sum);
        int attempts = failedAttempts.get(username);

        System.out.println("⚠️ Failed attempts for " + username + ": " + attempts);

        if (attempts >= 5) {
            System.out.println("🔒 User BLOCKED: " + username);
            // lock user account in DB
        }
    }
}

// STEP 4: Observer 3 — send security alert email
@Component
class SecurityAlertListener {

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        String username = event.getAuthentication().getName();
        System.out.println("📧 Security alert email sent for: " + username);
        // emailService.sendSecurityAlert(username);
    }
}

@Component
class OtherSecurityAlert {
    // Spring fires SPECIFIC events per failure reason
// You can listen to specific ones:

    @EventListener
    public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        System.out.println("Wrong password for: " + event.getAuthentication().getName());
    }

    @EventListener
    public void onAccountLocked(AuthenticationFailureLockedEvent event) {
        System.out.println("Account locked: " + event.getAuthentication().getName());
    }

    @EventListener
    public void onAccountDisabled(AuthenticationFailureDisabledEvent event) {
        System.out.println("Account disabled: " + event.getAuthentication().getName());
    }
}

@Controller
class LoginController {

    // Show login page
    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error, Model model) {

        if (error != null) {
            model.addAttribute("errorMsg", "Invalid username or password!");
        }

        return "login";   // → templates/login.html
    }

    // Show home page after successful login
    @GetMapping("/home")
    public String homePage() {
        return "home";    // → templates/home.html
    }
}