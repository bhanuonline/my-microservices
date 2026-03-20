package interview.spring.core.event;

import org.springframework.context.event.*;
import org.springframework.stereotype.Component;
import org.springframework.web.context.support.RequestHandledEvent;

@Component
public class ApplicationLifecycleListener {
    
    // Application context has been initialized
    @EventListener
    public void handleContextRefreshed(ContextRefreshedEvent event) {
        System.out.println("Context refreshed: " + event.getApplicationContext());
    }
    
    // Application context is about to be closed
    @EventListener
    public void handleContextClosed(ContextClosedEvent event) {
        System.out.println("Context closing - cleanup resources");
    }
    
    // Application context has started
    @EventListener
    public void handleContextStarted(ContextStartedEvent event) {
        System.out.println("Context started");
    }
    
    // Application context has stopped
    @EventListener
    public void handleContextStopped(ContextStoppedEvent event) {
        System.out.println("Context stopped");
    }
    
    // Request handled (web applications)
    @EventListener
    public void handleRequestHandled(RequestHandledEvent event) {
        System.out.println("Request handled: " + event.getDescription());
    }
}