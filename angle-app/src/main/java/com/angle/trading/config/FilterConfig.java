package com.angle.trading.config;

import com.angle.trading.filter.TraditionalFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Servlet filter registrations that must run regardless of security profile.
 * Kept separate from SecurityConfig so filters still load when the
 * "nosec" profile disables the security chain.
 */
@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<TraditionalFilter> traditionalFilterRegistration(
            TraditionalFilter traditionalFilter) {
        FilterRegistrationBean<TraditionalFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(traditionalFilter);
        bean.addUrlPatterns("/*");
        return bean;
    }
}
