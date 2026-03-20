package interview.spring.core.config.routingdb;

/**
 * DBContextHolder manages the thread-local context for database routing.
 * It stores both the DBType and the actual DataSource for load balancing.
 */
public class DBContextHolder {
    private static final ThreadLocal<DBType> contextHolder = new ThreadLocal<>();
    
    // Store the actual DataSource when using load balancing
    private static final ThreadLocal<Object> dataSourceKeyHolder = new ThreadLocal<>();

    public static void set(DBType dbType) {
        contextHolder.set(dbType);
    }

    public static DBType get() {
        return contextHolder.get();
    }
    
    /**
     * Set the data source key (can be DBType or actual DataSource for load balancing)
     */
    public static void setDataSourceKey(Object key) {
        dataSourceKeyHolder.set(key);
    }
    
    /**
     * Get the data source key for routing
     */
    public static Object getDataSourceKey() {
        Object key = dataSourceKeyHolder.get();
        return key != null ? key : contextHolder.get();
    }

    public static void clear() {
        contextHolder.remove();
        dataSourceKeyHolder.remove();
    }
}
