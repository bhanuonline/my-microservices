package interview.spring.core.config.routingdb;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LoadBalancingDynamicDataSource extends DynamicDataSource to provide
 * intelligent load balancing across multiple replica databases.
 * 
 * This implementation supports:
 * - Round-robin load balancing
 * - Random load balancing  
 * - Sticky sessions (same replica for consecutive requests)
 */
public class LoadBalancingDynamicDataSource extends AbstractRoutingDataSource {

    // List of replica data sources for load balancing
    private final List<DataSource> replicaDataSources = new ArrayList<>();
    
    // Round-robin counter
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);
    
    // Load balancing strategy
    private LoadBalancingStrategy strategy = LoadBalancingStrategy.ROUND_ROBIN;
    
    /**
     * Add a replica data source to the pool
     */
    public void addReplicaDataSource(DataSource dataSource) {
        replicaDataSources.add(dataSource);
    }
    
    /**
     * Set multiple replica data sources at once
     */
    public void setReplicaDataSources(List<DataSource> dataSources) {
        replicaDataSources.clear();
        replicaDataSources.addAll(dataSources);
    }
    
    /**
     * Set the load balancing strategy
     */
    public void setStrategy(LoadBalancingStrategy strategy) {
        this.strategy = strategy;
    }
    
    @Override
    protected Object determineCurrentLookupKey() {
        DBType dbType = DBContextHolder.get();
        
        // If it's a replica operation, apply load balancing
        if (isReplicaOperation(dbType)) {
            return getLoadBalancedReplica();
        }
        
        // For master operations, return the DBType directly
        return dbType;
    }
    
    /**
     * Check if the current operation is a replica/read operation
     */
    private boolean isReplicaOperation(DBType dbType) {
        return dbType == DBType.REPLICA || 
               dbType == DBType.REPLICA_2 || 
               dbType == DBType.REPLICA_3;
    }
    
    /**
     * Get the next replica based on the load balancing strategy
     */
    private Object getLoadBalancedReplica() {
        if (replicaDataSources.isEmpty()) {
            // No replicas available, fallback to master
            return DBType.MASTER;
        }
        
        if (replicaDataSources.size() == 1) {
            // Only one replica, use it directly
            return DBType.REPLICA;
        }
        
        // Apply load balancing strategy
        switch (strategy) {
            case ROUND_ROBIN:
                return getRoundRobinReplica();
            case RANDOM:
                return getRandomReplica();
            case STICKY:
                return getStickyReplica();
            default:
                return getRoundRobinReplica();
        }
    }
    
    /**
     * Round-robin load balancing - distributes requests evenly across replicas
     */
    private Object getRoundRobinReplica() {
        int index = Math.abs(roundRobinCounter.getAndIncrement()) % replicaDataSources.size();
        return getDBTypeForIndex(index);
    }
    
    /**
     * Random load balancing - randomly selects a replica
     */
    private Object getRandomReplica() {
        int index = (int) (Math.random() * replicaDataSources.size());
        return getDBTypeForIndex(index);
    }
    
    /**
     * Sticky session - keeps using the same replica for consecutive requests
     * Uses thread-local to maintain sticky session
     */
    private Object getStickyReplica() {
        Integer stickyIndex = StickySessionHolder.get();
        if (stickyIndex == null || stickyIndex >= replicaDataSources.size()) {
            stickyIndex = roundRobinCounter.getAndIncrement() % replicaDataSources.size();
            StickySessionHolder.set(stickyIndex);
        }
        return getDBTypeForIndex(stickyIndex);
    }
    
    /**
     * Get DBType for a given replica index
     */
    private Object getDBTypeForIndex(int index) {
        switch (index) {
            case 0:
                return DBType.REPLICA;
            case 1:
                return DBType.REPLICA_2;
            case 2:
                return DBType.REPLICA_3;
            default:
                return DBType.REPLICA;
        }
    }
    
    /**
     * Get the number of available replicas
     */
    public int getReplicaCount() {
        return replicaDataSources.size();
    }
    
    /**
     * Get the current strategy being used
     */
    public LoadBalancingStrategy getStrategy() {
        return strategy;
    }
    
    /**
     * Enum for load balancing strategies
     */
    public enum LoadBalancingStrategy {
        ROUND_ROBIN,  // Distribute requests evenly
        RANDOM,       // Randomly select replica
        STICKY        // Keep same replica for session
    }
    
    /**
     * ThreadLocal holder for sticky session
     */
    private static class StickySessionHolder {
        private static final ThreadLocal<Integer> holder = new ThreadLocal<>();
        
        public static Integer get() {
            return holder.get();
        }
        
        public static void set(Integer index) {
            holder.set(index);
        }
        
        public static void clear() {
            holder.remove();
        }
    }
}
