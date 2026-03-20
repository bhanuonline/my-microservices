package interview.spring.core.event;

import lombok.Data;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

// 1. Create custom event extending ApplicationEvent
public class UserRegisteredEvent extends ApplicationEvent {

    private String username;
    private String email;
    private int age;


    public UserRegisteredEvent(Object source, String username, String email,int age) {
        super(source);
        this.username = username;
        this.email = email;
        this.age=age;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }
}
@Data
class SystemEvent{
    public String message;
}

// 2. Create listener implementing ApplicationListener
@Component
class EmailNotificationListener implements ApplicationListener<UserRegisteredEvent> {

    @Override
    public void onApplicationEvent(UserRegisteredEvent event) {
        System.out.println("Sending welcome email to: " + event.getEmail());
        System.out.println("Event source: " + event.getSource());
        System.out.println("Timestamp: " + event.getTimestamp());

        // Send email logic here
    }
}

// 3. Publish event
@Service
class UserServiceNew implements ApplicationEventPublisherAware {

    private ApplicationEventPublisher eventPublisher;

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void registerUser(String username, String email,int age) {
        // Save user to database
        System.out.println("Saving user: " + username);

        // Publish event
        UserRegisteredEvent event = new UserRegisteredEvent(this, username, email,age);
        eventPublisher.publishEvent(event);

        System.out.println("User registration completed");
    }
}

@Component
 class AuditLogListener implements ApplicationListener<UserRegisteredEvent> {

    @Override
    public void onApplicationEvent(UserRegisteredEvent event) {
        System.out.println("2. Logging user registration: " + event.getUsername());
    }
}

@Component
 class AnalyticsListener implements ApplicationListener<UserRegisteredEvent> {

    @Override
    public void onApplicationEvent(UserRegisteredEvent event) {
        System.out.println("3. Tracking analytics for: " + event.getUsername());
    }
}

@Component
class ConditionalEventListener {

    // Only handle events for premium users
    @EventListener(condition = "#event.userType == 'PREMIUM'")
    public void handlePremiumUserRegistered(UserRegisteredEvent event) {
        System.out.println("Premium user registered: " + event.getUsername());
        // Send premium welcome package
    }

    // Only handle large orders
    @EventListener(condition = "#event.age > 50")
    public void handleLargeOrder(UserRegisteredEvent event) {
        System.out.println("Large order placed: $" + event.getAge());
        // Alert sales team
    }

    // Only in production environment
    @EventListener(condition = "@environment.getProperty('spring.profiles.active') == 'prod'")
    public void handleProductionEvent(SystemEvent event) {
        System.out.println("Production event: " + event.getMessage());
    }
}