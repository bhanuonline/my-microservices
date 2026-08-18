package com.angle.trading.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows the React dev server (localhost:5173) to call /api endpoints directly
 * — used when you hit the backend without going through the Vite proxy
 * (e.g. from a deployed frontend, or curl/Postman with an Origin header).
 *
 * Not needed for local dev with the Vite proxy because the browser sees all
 * calls as coming from localhost:5173 to itself. Left here so both paths work.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
