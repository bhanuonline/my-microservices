package com.example.apigateway.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Centralized CORS at the gateway. All downstream services trust the gateway
 * to enforce origin/method/header rules — they don't need their own CORS config.
 *
 * Wiring:
 *   - Config-driven origin/method/header lists via CorsProperties
 *   - CorsWebFilter bean applied to all routes (registered path pattern /**)
 *   - GatewaySecurityConfig calls .cors(withDefaults()) so Spring Security
 *     consults this filter and permits preflight OPTIONS
 *
 * Notes:
 *   - allowedOrigins CANNOT contain "*" when allowCredentials=true — browsers
 *     reject the response
 *   - exposedHeaders is critical: without it, custom X-* response headers are
 *     stripped by the browser before JS can read them
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
@ConditionalOnProperty(prefix = "gateway.cors", name = "enabled", havingValue = "true")
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter(CorsProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.getAllowedOrigins());
        config.setAllowedMethods(props.getAllowedMethods());
        config.setAllowedHeaders(props.getAllowedHeaders());
        config.setExposedHeaders(props.getExposedHeaders());
        config.setAllowCredentials(props.isAllowCredentials());
        config.setMaxAge(props.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
