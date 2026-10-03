package com.example.resourceserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Spring Security configuration for the resource-server.
 *
 * <h2>Role in the OAuth2 flow</h2>
 * <pre>
 * ┌──────────┐      1. token request      ┌───────────────┐
 * │  Client  │ ─────────────────────────▶ │ auth-server   │
 * │          │ ◀───────────────────────── │  :8095        │
 * └──────────┘      2. access_token       └───────────────┘
 *       │
 *       │ 3. GET /api/hello
 *       │    Authorization: Bearer eyJhbG...
 *       ▼
 * ┌──────────────────────────────────────────────────────────┐
 * │ resource-server :8096                                    │
 * │                                                          │
 * │  SecurityFilterChain:                                    │
 * │    oauth2ResourceServer.jwt()                            │
 * │      ├─ JwtDecoder (auto-configured from issuer-uri)     │
 * │      │   fetches JWKS from http://localhost:8095/.../jwks│
 * │      │   validates signature, exp, iss claims            │
 * │      │                                                   │
 * │      └─ JwtAuthenticationConverter (this file)           │
 * │          extracts `scope` claim → SCOPE_* authorities    │
 * │          extracts `roles` claim → ROLE_* authorities     │
 * │                                                          │
 * │  @EnableMethodSecurity activates @PreAuthorize on        │
 * │  controller methods — see ApiController.                 │
 * └──────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Changes from the pre-Tier-0 version</h2>
 * <ul>
 *   <li><strong>Removed</strong> the hardcoded {@code JwtDecoder} bean. The
 *       JWKS URL was duplicated between here and {@code application.yml}.
 *       Now the {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}
 *       property is the single source of truth; Spring Security
 *       auto-configures the decoder from it.</li>
 *   <li><strong>Added</strong> {@link JwtAuthenticationConverter} that
 *       extracts both {@code scope} AND {@code roles} claims as authorities.
 *       The default converter only reads {@code scope}; our auth-server
 *       emits both.</li>
 *   <li><strong>Added</strong> {@link EnableMethodSecurity} so
 *       {@code @PreAuthorize} works on controller methods.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /**
     * The HTTP security pipeline. Every request must be authenticated with a
     * valid JWT; actuator health/info/prometheus endpoints are public so
     * monitoring probes can reach them without auth.
     *
     * @param http the {@link HttpSecurity} builder provided by Spring
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception propagated from the builder
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        log.info("Configuring SecurityFilterChain: JWT resource server mode");
        http
                .authorizeHttpRequests(auth -> auth
                        // Monitoring probes (K8s liveness/readiness, Prometheus scrape)
                        // need un-authenticated access.
                        .requestMatchers("/actuator/health/**",
                                         "/actuator/info",
                                         "/actuator/prometheus").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /**
     * Converts a validated {@link Jwt} into an {@link AbstractAuthenticationToken}
     * carrying both scope-based ({@code SCOPE_*}) and role-based ({@code ROLE_*})
     * {@link GrantedAuthority authorities}.
     *
     * <p>Spring Security's default converter only reads the {@code scope} claim.
     * Our auth-server emits both {@code scope} (space-separated string) and
     * {@code roles} (list of strings), so this converter extracts both. That
     * way {@code @PreAuthorize("hasAuthority('SCOPE_read')")} and
     * {@code @PreAuthorize("hasRole('ADMIN')")} both work on controller methods.
     *
     * @return the converter
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        // Spring's built-in: reads `scope` or `scp` claim → SCOPE_* authorities
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        scopes.setAuthorityPrefix("SCOPE_");
        scopes.setAuthoritiesClaimName("scope");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new CombinedAuthoritiesConverter(scopes));
        return converter;
    }

    /**
     * Merges Spring's built-in scope converter with a custom roles-claim
     * extractor. Keeps the default behaviour and adds ROLE_* authorities
     * when a {@code roles} claim is present.
     */
    static final class CombinedAuthoritiesConverter
            implements Converter<Jwt, Collection<GrantedAuthority>> {

        private final JwtGrantedAuthoritiesConverter scopesDelegate;

        CombinedAuthoritiesConverter(JwtGrantedAuthoritiesConverter scopesDelegate) {
            this.scopesDelegate = scopesDelegate;
        }

        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            Collection<GrantedAuthority> all = new ArrayList<>();

            // 1. scope claim via Spring's built-in
            Collection<GrantedAuthority> scopes = scopesDelegate.convert(jwt);
            if (scopes != null) {
                all.addAll(scopes);
            }

            // 2. roles claim (list or CSV) → ROLE_* authorities
            for (String role : extractRoles(jwt)) {
                String normalised = role.startsWith("ROLE_") ? role : "ROLE_" + role.toUpperCase();
                all.add(new SimpleGrantedAuthority(normalised));
            }

            log.debug("JWT authorities resolved: sub={} authorities={}", jwt.getSubject(), all);
            return all;
        }

        /**
         * Reads the {@code roles} JWT claim as either a {@link Collection} or
         * a delimited {@link String}. Returns an empty list when absent.
         */
        private List<String> extractRoles(Jwt jwt) {
            Object raw = jwt.getClaims().get("roles");
            if (raw == null) return Collections.emptyList();
            if (raw instanceof Collection<?> col) {
                List<String> out = new ArrayList<>(col.size());
                for (Object o : col) {
                    if (o != null) out.add(o.toString());
                }
                return out;
            }
            if (raw instanceof String s && !s.isBlank()) {
                String delim = s.contains(",") ? "," : "\\s+";
                List<String> out = new ArrayList<>();
                for (String part : s.split(delim)) {
                    String t = part.trim();
                    if (!t.isEmpty()) out.add(t);
                }
                return out;
            }
            return Collections.emptyList();
        }
    }

    /**
     * Marker type used only for interview conversation — illustrates the
     * two auth modes Spring Security's resource-server starter supports:
     *
     * <ol>
     *   <li><strong>JWKS (self-contained JWT)</strong> — current mode.
     *       Decoder fetches JWKS from auth-server and validates locally.
     *       Set via {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}
     *       or {@code .jwt.jwk-set-uri}. No per-request network cost.</li>
     *
     *   <li><strong>Opaque token + introspection</strong> — alternative.
     *       Token is just a random string; resource-server POSTs to
     *       {@code /oauth2/introspect} on auth-server to validate.
     *       One network call per request. Needed when tokens must be
     *       revocable instantly.
     *       Set via {@code spring.security.oauth2.resourceserver.opaquetoken.*}.</li>
     * </ol>
     *
     * <p>Not referenced from code — kept here as living documentation.
     */
    @SuppressWarnings("unused")
    private enum AuthMode { JWT_WITH_JWKS, OPAQUE_WITH_INTROSPECTION }
}
