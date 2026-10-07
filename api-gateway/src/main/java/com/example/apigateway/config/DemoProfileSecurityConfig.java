package com.example.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Permit-all security chain for the <code>demo</code> Spring profile.
 *
 * Activated when the gateway is started with {@code -Dspring.profiles.active=demo},
 * which is what {@code start-stack.sh --demo} does. Lets the shop/backoffice run
 * without auth-server + mysql-auth, saving ~700 MB RAM for small-machine demos.
 *
 * This bean takes precedence over {@link GatewaySecurityConfig} because
 * Spring instantiates @Profile-matching beans ahead of the unqualified ones,
 * AND GatewaySecurityConfig has no profile => it's not loaded under 'demo'
 * (Spring's @Profile matching rule: a bean with NO @Profile is active only
 * when the profile isn't a @Profile("!demo") exclusion — in our case it
 * stays active, which would cause a bean conflict. Solution: mark
 * GatewaySecurityConfig as @Profile("!demo"). We do that too.)
 */
@Configuration
@Profile("demo")
public class DemoProfileSecurityConfig {

    @Bean
    public SecurityWebFilterChain permitAllSecurity(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> {})
                .authorizeExchange(auth -> auth.anyExchange().permitAll())
                .build();
    }
}
