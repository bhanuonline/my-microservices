package interview.spring.core.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
public class JpaConfig {

    // ================= PRIMARY =================
    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.primary")
    @Profile("core")
    public LocalContainerEntityManagerFactoryBean primaryEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("primaryDataSource") DataSource dataSource) {
        return builder
                .dataSource(dataSource)
                .packages("interview.spring.core.model.primary") // Primary entity package
                .persistenceUnit("primary")
                .build();
    }

    @Bean
    @Primary
    @Profile("core")
    public PlatformTransactionManager primaryTransactionManager(
            @Qualifier("primaryEntityManagerFactory") EntityManagerFactory factory) {
        return new JpaTransactionManager(factory);
    }

    // ================= SECONDARY =================
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.secondary")
    @Profile("core-one")
    public LocalContainerEntityManagerFactoryBean secondaryEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("secondaryDataSource") DataSource dataSource) {
        return builder
                .dataSource(dataSource)
                .packages("interview.spring.core.model.secondary") // Secondary entity package
                .persistenceUnit("secondary")
                .build();
    }

    @Bean
    @Profile("core-one")
    public PlatformTransactionManager secondaryTransactionManager(
            @Qualifier("secondaryEntityManagerFactory") EntityManagerFactory factory) {
        return new JpaTransactionManager(factory);
    }

    // ================= TERTIARY =================
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.tertiary")
    @Profile("core-two")
    public LocalContainerEntityManagerFactoryBean tertiaryEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("tertiaryDataSource") DataSource dataSource) {
        return builder
                .dataSource(dataSource)
                .packages("interview.spring.core.model.tertiary") // Tertiary entity package
                .persistenceUnit("tertiary")
                .build();
    }

    @Bean
    @Profile("core-two")
    public PlatformTransactionManager tertiaryTransactionManager(
            @Qualifier("tertiaryEntityManagerFactory") EntityManagerFactory factory) {
        return new JpaTransactionManager(factory);
    }
}

@Configuration
@EnableJpaRepositories(
        basePackages = "interview.spring.core.repository.primary",
        entityManagerFactoryRef = "primaryEntityManagerFactory",
        transactionManagerRef = "primaryTransactionManager"
)
class PrimaryJpaConfig {}

@Configuration
@EnableJpaRepositories(
        basePackages = "interview.spring.core.repository.secondary",
        entityManagerFactoryRef = "secondaryEntityManagerFactory",
        transactionManagerRef = "secondaryTransactionManager"
)
class SecondaryJpaConfig {}

@Configuration
@EnableJpaRepositories(
        basePackages = "interview.spring.core.repository.tertiary",
        entityManagerFactoryRef = "tertiaryEntityManagerFactory",
        transactionManagerRef = "tertiaryTransactionManager"
)
class TertiaryJpaConfig {}

