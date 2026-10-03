package com.example.common.logging;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

import java.util.EnumSet;

/**
 * Registers the servlet CorrelationIdFilter with highest precedence so every
 * request gets an id in MDC before Spring Security / Web MVC dispatch runs.
 *
 * Auto-applied to any servlet-based Spring Boot service that depends on common-lib.
 * Reactive apps skip this entirely (guarded by @ConditionalOnWebApplication SERVLET).
 *
 * Listed in
 *   META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 * so Spring Boot 3 picks it up during auto-configuration.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(jakarta.servlet.Filter.class)
public class CorrelationIdAutoConfiguration {

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration() {
        FilterRegistrationBean<CorrelationIdFilter> bean = new FilterRegistrationBean<>(new CorrelationIdFilter());
        bean.addUrlPatterns("/*");
        bean.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC));
        // Runs before Spring Security so unauthenticated 401 responses still get a correlation id.
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        bean.setName("correlationIdFilter");
        return bean;
    }
}
