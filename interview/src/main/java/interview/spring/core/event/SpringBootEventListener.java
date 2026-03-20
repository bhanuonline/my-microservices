package interview.spring.core.event;

import org.springframework.boot.context.event.*;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class SpringBootEventListener {
    
    // Very early - before context is created
    @EventListener
    public void handleApplicationStarting(ApplicationStartingEvent event) {
        System.out.println("Application starting...");
    }
    
    // Environment prepared, before context created
    @EventListener
    public void handleEnvironmentPrepared(ApplicationEnvironmentPreparedEvent event) {
        System.out.println("Environment prepared");
        System.out.println("Active profiles: " + 
                          Arrays.toString(event.getEnvironment().getActiveProfiles()));
    }
    
    // Context prepared, before beans loaded
    @EventListener
    public void handleContextPrepared(ApplicationContextInitializedEvent event) {
        System.out.println("Context prepared");
    }
    
    // Context loaded, before refresh
    @EventListener
    public void handleContextLoaded(ApplicationPreparedEvent event) {
        System.out.println("Context loaded");
    }
    
    // Application started and ready
    @EventListener
    public void handleApplicationReady(ApplicationReadyEvent event) {
        System.out.println("Application ready to serve requests");
    }
    
    // Application failed to start
    @EventListener
    public void handleApplicationFailed(ApplicationFailedEvent event) {
        System.err.println("Application failed to start: " + 
                          event.getException().getMessage());
    }
}