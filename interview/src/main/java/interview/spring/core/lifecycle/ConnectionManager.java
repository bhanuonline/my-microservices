package interview.spring.core.lifecycle;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Component
public class ConnectionManager implements DisposableBean {
    
    @Autowired
    private ApplicationContext context;
    
    private List<DatabaseConnection> connections = new ArrayList<>();
    
    public DatabaseConnection getConnection() {
        DatabaseConnection conn = context.getBean(DatabaseConnection.class);
        connections.add(conn);
        return conn;
    }
    
    @Override
    public void destroy() throws SQLException {
        // Clean up all connections when manager is destroyed
        for (DatabaseConnection conn : connections) {
            conn.cleanup();
        }
        connections.clear();
    }
}