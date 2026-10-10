package com.example.common.tenant;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

import java.util.EnumSet;

/**
 * Registers {@link TenantContextFilter} on every servlet-based service that
 * depends on common-lib. Runs AFTER the correlation-id filter but BEFORE
 * Spring Security's dispatcher so tenant is visible everywhere downstream.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(jakarta.servlet.Filter.class)
public class TenantAutoConfiguration {

    @Bean
    public FilterRegistrationBean<TenantContextFilter> tenantContextFilterRegistration() {
        FilterRegistrationBean<TenantContextFilter> bean = new FilterRegistrationBean<>(new TenantContextFilter());
        bean.addUrlPatterns("/*");
        bean.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC));
        // Correlation filter is HIGHEST_PRECEDENCE + 10; sit just after it.
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        bean.setName("tenantContextFilter");
        return bean;
    }
}
