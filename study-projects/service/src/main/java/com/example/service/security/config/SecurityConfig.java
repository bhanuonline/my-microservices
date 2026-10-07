package com.example.service.security.config;

import com.example.service.filter.ApiKeyFilter;
import com.example.service.filter.RequestLoggingFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@Slf4j
public class SecurityConfig {

    private final ApiKeyFilter apiKeyFilter;

    public SecurityConfig(ApiKeyFilter apiKeyFilter) {
        this.apiKeyFilter = apiKeyFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/public/**").permitAll()    // open for everyone
                        .requestMatchers("/order/**").permitAll()
                        .requestMatchers("/internal/**").permitAll() // for api key
                        .requestMatchers("/api/admin/**").hasRole("ADMIN") // require ADMIN role
                        .requestMatchers("/api/dev/**").hasRole("DEV")

                        .anyRequest().authenticated()                     // others need authentication

                );
              //  .formLogin(form -> form.disable()).httpBasic();  // allow basic auth (for APIs)
        // 🔥 THIS IS THE MISSING PART
//        http.oauth2ResourceServer(oauth2 ->
//                oauth2.jwt(Customizer.withDefaults())
//        );
        http.oauth2ResourceServer(oauth2 ->
                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
        );
        // Register your filter at the desired position
        http.addFilterBefore(new RequestLoggingFilter(),
                UsernamePasswordAuthenticationFilter.class);

        http.addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class);
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()));
        http
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                );


        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        Logger log = LoggerFactory.getLogger("JWT-ROLE-MAPPER");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {

            log.debug("---- JWT ROLE EXTRACTION START ----");
            log.debug("Token subject        : {}", jwt.getSubject());
            log.debug("Token issuer         : {}", jwt.getIssuer());
            log.debug("All claims           : {}", jwt.getClaims());

            Map<String, Object> resourceAccess = jwt.getClaim("resource_access");

            if (resourceAccess == null) {
                log.warn("No 'resource_access' claim found in token");
                return List.of();
            }

            if (!resourceAccess.containsKey("myservice")) {
                log.warn("'resource_access' does not contain client 'myservice'. Available keys: {}",
                        resourceAccess.keySet());
                return List.of();
            }

            Map<String, Object> myservice =
                    (Map<String, Object>) resourceAccess.get("myservice");

            log.debug("'myservice' object   : {}", myservice);

            List<String> roles = (List<String>) myservice.get("roles");

            if (roles == null || roles.isEmpty()) {
                log.warn("No roles found under resource_access.myservice.roles");
                return List.of();
            }

            log.debug("Extracted roles     : {}", roles);

            List<GrantedAuthority> authorities = roles.stream()
                    .map(role -> "ROLE_" + role)
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            log.debug("Mapped authorities  : {}", authorities);
            log.debug("---- JWT ROLE EXTRACTION END ----");

            return authorities;
        });

        return converter;
    }




    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:3000")); // your frontend origin
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true); // allow cookies / auth headers

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {

            log.error("Unauthorized request - URI: {}, IP: {}, Error: {}",
                    request.getRequestURI(),
                    request.getRemoteAddr(),
                    authException.getMessage());

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");

            response.getWriter().write("""
        {
          "status": 401,
          "error": "Unauthorized",
          "message": "Authentication token is missing or invalid",
          "path": "%s"
        }
        """.formatted(request.getRequestURI()));
        };
    }


    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {

            log.warn("Access denied - User tried to access: {}, Reason: {}",
                    request.getRequestURI(),
                    accessDeniedException.getMessage());

            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");

            response.getWriter().write("""
        {
          "status": 403,
          "error": "Forbidden",
          "message": "You don't have permission to access this resource",
          "path": "%s"
        }
        """.formatted(request.getRequestURI()));
        };
    }

}