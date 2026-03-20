package interview.spring.core.config.routingdb;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class DynamicDataSource extends AbstractRoutingDataSource {
    @Override
    protected Object determineCurrentLookupKey() {
        return DBContextHolder.getDataSourceKey(); // Get current data source key with load balancing support
    }
}
