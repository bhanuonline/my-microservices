package com.example.apigateway.config;

import com.example.apigateway.apikey.ApiKeyAuthenticationConverter;
import com.example.apigateway.apikey.ApiKeyProperties;
import com.example.apigateway.apikey.ApiKeyReactiveAuthenticationManager;
import com.example.apigateway.security.AdminAuthProperties;
import com.example.apigateway.security.AdminJwtAuthenticationConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;

import java.util.List;

@EnableWebFluxSecurity
@Configuration
@EnableConfigurationProperties(AdminAuthProperties.class)
public class GatewaySecurityConfig {

    @Bean
    public SecurityWebFilterChain security(
            ServerHttpSecurity http,
            ObjectProvider<ApiKeyReactiveAuthenticationManager> apiKeyManager,
            ObjectProvider<ApiKeyProperties> apiKeyProps,
            AdminAuthProperties adminProps) {

        String[] adminAuthorities = adminProps.getRequiredAuthorities().toArray(new String[0]);

        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // Consult the CorsWebFilter bean (when gateway.cors.enabled=true).
                // Security recognizes preflight OPTIONS and lets them through.
                .cors(Customizer.withDefaults())
                .authorizeExchange(auth -> auth
                        .pathMatchers("/actuator/**", "/fallback/**").permitAll()
                        // /admin/** requires ANY of the configured admin authorities
                        // (default: SCOPE_admin OR ROLE_ADMIN)
                        .pathMatchers("/admin/**").hasAnyAuthority(adminAuthorities)
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                new AdminJwtAuthenticationConverter(adminProps))));

        // When Thymeleaf UI is active, browsers hit /admin/ui/** and don't naturally
        // send Bearer headers. Enable HTTP Basic so the browser prompts for creds
        // (defined via MapReactiveUserDetailsService bean below).
        // Bearer JWT still works for REST /admin/routes, /admin/apikeys, etc.
        if (adminProps.getUi() == AdminAuthProperties.Ui.THYMELEAF) {
            http.httpBasic(Customizer.withDefaults());
        }

        // Register API-key filter only if the beans exist (i.e. gateway.apikey.enabled=true).
        // Placed BEFORE the JWT auth so API keys are cheaper to check first;
        // absence of an API key falls through to JWT.
        ApiKeyReactiveAuthenticationManager mgr = apiKeyManager.getIfAvailable();
        ApiKeyProperties props = apiKeyProps.getIfAvailable();
        if (mgr != null && props != null) {
            AuthenticationWebFilter apiKeyFilter = new AuthenticationWebFilter(mgr);
            apiKeyFilter.setServerAuthenticationConverter(new ApiKeyAuthenticationConverter(props));
            http.addFilterAt(apiKeyFilter, SecurityWebFiltersOrder.AUTHENTICATION);
        }

        return http.build();
    }

    /**
     * In-memory admin user for HTTP Basic auth on the Thymeleaf UI paths.
     * Uses the same credentials as the demo-admin-subs escape hatch so anyone
     * used to `admin:admin123` for /oauth2/token can also log in to /admin/ui.
     *
     * Only relevant when gateway.admin.ui=THYMELEAF (otherwise unused — bean
     * exists but no filter references it).
     */
    @Bean
    public MapReactiveUserDetailsService adminUserDetailsService(AdminAuthProperties adminProps) {
        String[] rolesArr = adminProps.getRequiredAuthorities().stream()
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .toArray(String[]::new);
        String[] roles = rolesArr.length == 0 ? new String[]{"ADMIN"} : rolesArr;

        UserDetails admin = User.builder()
                .username("admin")
                .password("{noop}admin123")
                .roles(roles)
                .authorities(adminProps.getRequiredAuthorities().toArray(new String[0]))
                .build();
        return new MapReactiveUserDetailsService(List.of(admin));
    }
}
