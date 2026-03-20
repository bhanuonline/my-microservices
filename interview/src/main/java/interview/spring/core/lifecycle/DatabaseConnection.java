package interview.spring.core.lifecycle;

import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.SQLException;

@Component
public class DatabaseConnection {
    
    private Connection connection;
    
    // 1. Constructor called
    public DatabaseConnection() {
        System.out.println("Constructor called");
    }
    
    // 2. Dependencies injected here via setter or constructor
    
    // 3. Initialization
    @PostConstruct
    public void init() {
        System.out.println("PostConstruct - opening connection");
    }
    
    // 4. Bean ready for use
    
    // 5. Cleanup before destruction
    @PreDestroy
    public void cleanup() throws SQLException {
        System.out.println("PreDestroy - closing connection");
        if (connection != null) {
            connection.close();
        }
    }
}