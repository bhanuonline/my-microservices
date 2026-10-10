package com.example.auth.security;

import com.example.auth.config.FeatureFlags;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;

/**
 * Registers {@link RateLimitFilter} in the servlet container by hand so we
 * control the order and the URL patterns.
 *
 * <p>Why not just {@code @Component} on the filter class:
 * Spring Boot auto-detects any {@code Filter} bean and adds it to the chain,
 * but the ordering is Boot's default (very late — AFTER Spring Security). We
 * need our filter BEFORE Spring Security so we can reject early and save the
 * server the work of parsing Basic Auth headers, hashing passwords, etc.
 *
 * <p>{@link FilterRegistrationBean#setOrder(int) Ordered.HIGHEST_PRECEDENCE}
 * puts it at the very front of the servlet chain — before FilterChainProxy
 * (Spring Security's entry point).
 *
 * <p>{@link FilterRegistrationBean#addUrlPatterns} limits it to just the two
 * endpoints we care about. Static resources and API paths pass through
 * untouched.
 */
@Configuration
@Profile("jdbc")
public class RateLimitFilterRegistration {

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterBean(
            RateLimitService service, FeatureFlags flags,
            com.example.auth.metrics.AuthMetrics metrics) {
        FilterRegistrationBean<RateLimitFilter> reg =
                new FilterRegistrationBean<>(new RateLimitFilter(service, flags, metrics));
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        reg.addUrlPatterns("/login", "/oauth2/token");
        reg.setName("rateLimitFilter");
        return reg;
    }
}
