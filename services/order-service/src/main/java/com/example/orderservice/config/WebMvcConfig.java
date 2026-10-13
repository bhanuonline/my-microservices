package com.example.orderservice.config;

import com.example.orderservice.web.ApiVersionArgumentResolver;
import com.example.orderservice.web.CorrelationIdInterceptor;
import com.example.orderservice.web.CurrentUserArgumentResolver;
import com.example.orderservice.web.MaintenanceModeInterceptor;
import com.example.orderservice.web.RateLimitInterceptor;
import com.example.orderservice.web.RequestTimingInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RequestTimingInterceptor requestTimingInterceptor;
    private final CorrelationIdInterceptor correlationIdInterceptor;
    private final MaintenanceModeInterceptor maintenanceModeInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;
    private final ApiVersionArgumentResolver apiVersionArgumentResolver;

    public WebMvcConfig(RequestTimingInterceptor requestTimingInterceptor,
                        CorrelationIdInterceptor correlationIdInterceptor,
                        MaintenanceModeInterceptor maintenanceModeInterceptor,
                        RateLimitInterceptor rateLimitInterceptor,
                        CurrentUserArgumentResolver currentUserArgumentResolver,
                        ApiVersionArgumentResolver apiVersionArgumentResolver) {
        this.requestTimingInterceptor = requestTimingInterceptor;
        this.correlationIdInterceptor = correlationIdInterceptor;
        this.maintenanceModeInterceptor = maintenanceModeInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.currentUserArgumentResolver = currentUserArgumentResolver;
        this.apiVersionArgumentResolver = apiVersionArgumentResolver;
    }

    // Order matters: CorrelationId first (sets MDC for all subsequent logs),
    // then Maintenance (bail early if down), then RateLimit, then Timing.
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(correlationIdInterceptor).addPathPatterns("/api/**").order(1);
        registry.addInterceptor(maintenanceModeInterceptor).addPathPatterns("/api/**").order(2);
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/**").order(3);
        registry.addInterceptor(requestTimingInterceptor).addPathPatterns("/api/**").order(4);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
        resolvers.add(apiVersionArgumentResolver);
    }
}
