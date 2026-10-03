package com.example.common.transaction;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.jpa.JpaTransactionManager;

/**
 * Enabling spring.kafka.producer.transaction-id-prefix adds a kafkaTransactionManager
 * bean. Spring Boot's built-in JpaTransactionManager (bean name "transactionManager")
 * is NOT @Primary, so every @Transactional / @Autowired PlatformTransactionManager
 * becomes ambiguous.
 *
 * Fix: mark the existing "transactionManager" bean definition as primary via a
 * BeanFactoryPostProcessor. This runs before any @Transactional AOP wiring, so
 * all unqualified resolutions pick the JPA tx manager. Code that genuinely wants
 * the Kafka one must inject by qualified name: @Qualifier("kafkaTransactionManager").
 */
@AutoConfiguration
@ConditionalOnClass(JpaTransactionManager.class)
public class PrimaryJpaTransactionManagerConfig {

    @Bean
    public static BeanFactoryPostProcessor markJpaTxManagerPrimary() {
        return new BeanFactoryPostProcessor() {
            @Override
            public void postProcessBeanFactory(ConfigurableListableBeanFactory bf) throws BeansException {
                if (bf.containsBeanDefinition("transactionManager")) {
                    bf.getBeanDefinition("transactionManager").setPrimary(true);
                }
            }
        };
    }
}
