package com.example.shop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Permit-all for the storefront demo. In prod you'd:
 *   - require OAuth2 login for /cart /checkout /orders
 *   - keep /, /products, static assets public
 *   - re-enable CSRF (and set up thymeleaf-extras-springsecurity6 so the
 *     _csrf token lands in every form)
 *
 * CSRF is disabled here so HTMX POSTs (add-to-cart, update-qty) work
 * without template changes.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());
        return http.build();
    }
}
