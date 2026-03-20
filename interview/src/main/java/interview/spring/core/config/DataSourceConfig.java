package interview.spring.core.config;

import javax.sql.DataSource;

import interview.spring.core.config.routingdb.DBType;
import interview.spring.core.config.routingdb.DynamicDataSource;
import interview.spring.core.config.routingdb.LoadBalancingDynamicDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class DataSourceConfig {

    // ================= PRIMARY =================
    @Bean(name = "primaryDataSource")
    @Primary
    @Profile("core")
    @ConfigurationProperties(prefix = "spring.datasource.primary")
    public DataSource primaryDataSource() {
        return DataSourceBuilder.create().build();
    }

    // ================= SECONDARY =================
    @Bean(name = "secondaryDataSource")
    @Profile("core-one")
    @ConfigurationProperties(prefix = "spring.datasource.secondary")
    public DataSource secondaryDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "tertiaryDataSource")
    @Profile("core-two")
    @ConfigurationProperties(prefix = "spring.datasource.tertiary")
    public DataSource tertiaryDataSource() {
        return DataSourceBuilder.create().build();
    }


    @Bean
    public DataSource dynamicDataSource(
            @Qualifier("primaryDataSource") DataSource primaryDataSource,
            @Qualifier("secondaryDataSource") DataSource secondaryDataSource,
            @Qualifier("tertiaryDataSource") DataSource tertiaryDataSource) {

        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DBType.MASTER, primaryDataSource);
        targetDataSources.put(DBType.REPLICA, secondaryDataSource);
        targetDataSources.put(DBType.REPLICA_2, tertiaryDataSource);
       // targetDataSources.put(DBType.REPLICA_3, replica2DataSource);

        // Create load balancing data source
        LoadBalancingDynamicDataSource dynamicDataSource = new LoadBalancingDynamicDataSource();
        dynamicDataSource.setTargetDataSources(targetDataSources);
        dynamicDataSource.setDefaultTargetDataSource(primaryDataSource);
        
        // Add replica data sources for load balancing
        List<DataSource> replicas = new ArrayList<>();
        replicas.add(secondaryDataSource);
        replicas.add(tertiaryDataSource);
        //replicas.add(replica2DataSource);
        dynamicDataSource.setReplicaDataSources(replicas);
        
        // Set load balancing strategy
        dynamicDataSource.setStrategy(LoadBalancingDynamicDataSource.LoadBalancingStrategy.ROUND_ROBIN);
        
        return dynamicDataSource;
    }

}




