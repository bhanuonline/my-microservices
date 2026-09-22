package com.example.auth.config;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /**
     * Chain 1 (ORDER 1 — runs FIRST) — OAuth2 authorization server endpoints.
     *
     * Matches ONLY the OAuth2 protocol paths:
     *   /oauth2/**              (token, authorize, jwks, ...)
     *   /.well-known/**         (OIDC discovery — critical: resource-servers fetch this)
     *   /connect/**             (OIDC session management)
     *
     * Redirects HTML browsers to /login for interactive flows.
     * Programmatic clients (Postman with Basic Auth) get straight-through access.
     */
    @Bean
    @Order(1)
    SecurityFilterChain authServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);

        http.exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                )
        );
        return http.build();
    }

    /**
     * Chain 2 (ORDER 2 — fallback) — everything else on this app.
     *
     * Login page, actuator, custom controllers, static resources.
     * Uses form-login so users can sign in for the auth_code flow.
     *
     * Public endpoints (no auth needed):
     *   /login                — the login form itself
     *   /error                — Spring's error page
     *   /actuator/**          — health checks, monitoring
     */
    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/error", "/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(Customizer.withDefaults())
            .csrf(csrf -> csrf.ignoringRequestMatchers("/actuator/**"));
        return http.build();
    }

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer() {
        return context -> {
            log.info("🪙  Issuing token for client={} principal={} scopes={}",
                    context.getRegisteredClient().getClientId(),
                    context.getPrincipal().getName(),
                    context.getAuthorizedScopes());

            // Enrich JWT with a custom claim — useful for downstream services
            context.getClaims().claim("custom-issuer", "ExampleAuthServer");
            // NOTE: removed 'user_ip' claim — it was serializing WebAuthenticationDetails
            //   which contains non-JSON-safe objects. Add safe scalar claims only.
        };
    }

    // Register OAuth clients
    @Bean
    public RegisteredClientRepository registeredClientRepository() {

        // Client 1: for real user login flows (browser redirect).
        // Used by web/mobile apps. Users authenticate; JWT carries user identity.
        RegisteredClient demoClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("demo-client")
                .clientSecret("{noop}secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .redirectUri("http://127.0.0.1:8097/login/oauth2/code/demo-client")
                .postLogoutRedirectUri("http://127.0.0.1:8097/")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope("read")
                .build();

        // Client 2: for machine-to-machine + Postman testing.
        // No user involved — JWT carries client identity only (sub=m2m-client).
        // Use this in Postman, curl, scheduled jobs, backend service-to-service.
        RegisteredClient m2mClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("m2m-client")
                .clientSecret("{noop}m2m-secret")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .scope("read")
                .scope("write")
                .build();

        logRegisteredClient("demo-client (user login flow)", demoClient);
        logRegisteredClient("m2m-client (Postman / M2M testing)", m2mClient);

        return new InMemoryRegisteredClientRepository(demoClient, m2mClient);
    }

    private void logRegisteredClient(String label, RegisteredClient c) {
        log.info("""
            ================== CLIENT REGISTERED ==================
            LABEL:         {}
            ID:            {}
            CLIENT_ID:     {}
            AUTH METHODS:  {}
            GRANT TYPES:   {}
            SCOPES:        {}
            REDIRECT URIs: {}
            =======================================================""",
                label,
                c.getId(),
                c.getClientId(),
                c.getClientAuthenticationMethods(),
                c.getAuthorizationGrantTypes(),
                c.getScopes(),
                c.getRedirectUris());
    }
    @Bean
    public OAuth2AuthorizationService authorizationService() {
        return new InMemoryOAuth2AuthorizationService();
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        KeyPair keyPair = generateRsaKey();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        log.info("Generated RSA key pair: public={} private={} (will create JWKSet)",
                publicKey.getAlgorithm(), privateKey.getAlgorithm());
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }

    private static KeyPair generateRsaKey() {
        KeyPair keyPair;
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            keyPair = keyPairGenerator.generateKeyPair();
        }
        catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return keyPair;
    }

//    @Bean
//    public JWKSource<SecurityContext> jwkSource() {
//        RSAKey rsaKey = Jwks.generateRsa();
//        JWKSet jwkSet = new JWKSet(rsaKey);
//        return (selector, context) -> selector.select(jwkSet);
//    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        AuthorizationServerSettings settings =
                AuthorizationServerSettings.builder()
                        .issuer("http://localhost:8095")
                        .build();

        log.info("AuthorizationServerSettings initialized: issuer={}", settings.getIssuer());
        return settings;
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    // Demo in‑memory user for login
//    @Bean
//    public UserDetailsService users() {
//        UserDetails user = User.withUsername("user")
//                .password("{noop}password")
//                .roles("USER")
//                .build();
//        return new InMemoryUserDetailsManager(user);
//    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails userDetails = User.withDefaultPasswordEncoder()
                .username("user")
                .password("{noop}password")
                .roles("USER")
                .build();
        log.info("In‑memory demo user created: username={}", userDetails.getUsername());
        return new InMemoryUserDetailsManager(userDetails);
    }

    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter2() {
        CommonsRequestLoggingFilter loggingFilter = new CommonsRequestLoggingFilter();
        loggingFilter.setIncludeClientInfo(true);
        loggingFilter.setIncludeQueryString(true);
        loggingFilter.setIncludePayload(true);
        loggingFilter.setMaxPayloadLength(10000);
        loggingFilter.setIncludeHeaders(false);
        log.info("CommonsRequestLoggingFilter configured to log all HTTP requests");
        return loggingFilter;
    }
}