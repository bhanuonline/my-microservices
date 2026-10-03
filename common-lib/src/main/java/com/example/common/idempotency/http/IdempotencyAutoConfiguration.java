package com.example.common.idempotency.http;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Opt-in via `idempotency.enabled=true` + at least one entry in `idempotency.endpoints`.
 *
 * Consumer services must also:
 *   - Ensure JPA is on the classpath (spring-boot-starter-data-jpa).
 *   - Ensure `com.example.common.idempotency.http` is scanned for entities + repos.
 *     This auto-config adds the needed @EntityScan + @EnableJpaRepositories so most
 *     services get it for free; services with custom scan setups should merge the
 *     base package explicitly.
 */
@AutoConfiguration
@ConditionalOnClass(jakarta.servlet.Filter.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "idempotency", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(IdempotencyProperties.class)
@EntityScan(basePackages = "com.example.common.idempotency.http")
@EnableJpaRepositories(basePackages = "com.example.common.idempotency.http")
public class IdempotencyAutoConfiguration {

    @Bean
    public FilterRegistrationBean<IdempotencyFilter> idempotencyFilterRegistration(
            IdempotencyRecordRepository repo, IdempotencyProperties props) {

        FilterRegistrationBean<IdempotencyFilter> bean =
                new FilterRegistrationBean<>(new IdempotencyFilter(repo, props));
        bean.addUrlPatterns("/*");
        // Run after Spring Security so principal is resolved for scoping.
        bean.setOrder(Ordered.LOWEST_PRECEDENCE - 100);
        bean.setName("idempotencyFilter");
        return bean;
    }
}
