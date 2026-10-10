package com.angle.trading.config;

import com.angle.trading.security.CustomAuthenticationFailureHandler;
import com.angle.trading.security.CustomLogoutSuccessHandler;
import com.angle.trading.security.RateLimitFilter;
import com.angle.trading.security.TwoFactorAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.RememberMeConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import javax.sql.DataSource;

@Configuration
@EnableWebSecurity
@Profile("!nosec")
public class SecurityConfig {

    private final CustomLogoutSuccessHandler logoutSuccessHandler;
    private final CustomAuthenticationFailureHandler loginFailureHandler;
    private final SessionProperties sessionProps;
    private final RateLimitFilter rateLimitFilter;
    private final TwoFactorAuthenticationFilter twoFactorFilter;
    private final RememberMeProperties rememberMeProps;
    private final DataSource dataSource;
    private final UserDetailsService userDetailsService;

    public SecurityConfig(CustomLogoutSuccessHandler logoutSuccessHandler,
                          @Lazy CustomAuthenticationFailureHandler loginFailureHandler,
                          SessionProperties sessionProps,
                          RateLimitFilter rateLimitFilter,
                          TwoFactorAuthenticationFilter twoFactorFilter,
                          RememberMeProperties rememberMeProps,
                          DataSource dataSource,
                          @Lazy UserDetailsService userDetailsService) {
        this.logoutSuccessHandler = logoutSuccessHandler;
        this.loginFailureHandler = loginFailureHandler;
        this.sessionProps = sessionProps;
        this.rateLimitFilter = rateLimitFilter;
        this.twoFactorFilter = twoFactorFilter;
        this.rememberMeProps = rememberMeProps;
        this.dataSource = dataSource;
        this.userDetailsService = userDetailsService;
    }

    // ============================================================
    // Chain 1: REST API — /api/**
    // Stateless, HTTP Basic, no CSRF, returns 401 (no login redirect)
    // ============================================================
    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                // Rate-limit AFTER auth so SecurityContextHolder has the username.
                // Unauthenticated requests get a 401 from BasicAuthFilter before
                // reaching the rate-limit filter — no bypass risk.
                .addFilterAfter(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // ============================================================
    // Chain 1b: Actuator — /actuator/**
    // Stateless HTTP Basic, requires ROLE_ADMIN. No CSRF (POST /refresh must work with curl).
    // Ordered before /admin so it doesn't get pulled into the form-login chain.
    // ============================================================
    @Bean
    @Order(2)
    public SecurityFilterChain actuatorFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/actuator/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    // ============================================================
    // Chain 3: Admin console — /admin/**
    // Form login, requires ROLE_ADMIN, custom access-denied page
    // ============================================================
    @Bean
    @Order(3)
    public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/admin/**")
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().hasRole("ADMIN"))
                .sessionManagement(this::configureFormSession)
                .formLogin(login -> login
                        .loginPage("/auth/login")
                        .loginProcessingUrl("/auth/login")
                        .defaultSuccessUrl("/admin", true)
                        .failureHandler(loginFailureHandler)
                        .permitAll())
                .exceptionHandling(ex -> ex
                        .accessDeniedPage("/auth/access-denied"))
                .rememberMe(this::configureRememberMe)
                // 2FA gate runs AFTER the username/password filter so the
                // SecurityContext has the authenticated principal to read from.
                .addFilterAfter(twoFactorFilter, UsernamePasswordAuthenticationFilter.class)
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/auth/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("ANGLE_SESSION", rememberMeProps.getCookieName())
                        .permitAll());
        return http.build();
    }

    // ============================================================
    // Chain 4: Web (catch-all) — everything else
    // Form login, session-based, CSRF enabled, public static assets
    // ============================================================
    @Bean
    @Order(4)
    public SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**", "/css/**", "/js/**", "/images/**").permitAll()
                        .anyRequest().authenticated())
                .sessionManagement(this::configureFormSession)
                .formLogin(login -> login
                        .loginPage("/auth/login")
                        .loginProcessingUrl("/auth/login")
                        .defaultSuccessUrl("/dashboard", true)
                        .failureHandler(loginFailureHandler)
                        .permitAll())
                .rememberMe(this::configureRememberMe)
                .addFilterAfter(twoFactorFilter, UsernamePasswordAuthenticationFilter.class)
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/auth/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("ANGLE_SESSION", rememberMeProps.getCookieName())
                        .permitAll());
        return http.build();
    }

    /**
     * Shared session policy for both form-login chains:
     *   • IF_REQUIRED — only create a session when actually needed
     *   • invalidSessionUrl — stale cookies land on a friendly page, not a 403
     *   • sessionFixation = migrateSession — new JSESSIONID on login (default,
     *     set explicitly for documentation)
     */
    private void configureFormSession(org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer<HttpSecurity> s) {
        s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
         .invalidSessionUrl(sessionProps.getInvalidSessionUrl())
         .sessionFixation(sf -> sf.migrateSession());
    }

    /**
     * Shared remember-me config for both form chains.
     *
     * Uses JdbcTokenRepositoryImpl against the MySQL datasource. Spring
     * auto-creates persistent_logins on first boot when
     * createTableOnStartup=true. UserDetailsService must be set explicitly
     * because the auto-detection breaks when multiple filter chains exist.
     *
     * Short-circuited when rememberMeProps.enabled=false: builder stays
     * default (checkbox silently ignored).
     */
    private void configureRememberMe(RememberMeConfigurer<HttpSecurity> rm) {
        if (!rememberMeProps.isEnabled()) {
            return;
        }
        rm.key(rememberMeProps.getKey())
          .rememberMeParameter(rememberMeProps.getParameterName())
          .rememberMeCookieName(rememberMeProps.getCookieName())
          .tokenValiditySeconds(rememberMeProps.getTokenValiditySeconds())
          .userDetailsService(userDetailsService)
          .tokenRepository(persistentTokenRepository());
    }

    @Bean
    public PersistentTokenRepository persistentTokenRepository() {
        JdbcTokenRepositoryImpl repo = new JdbcTokenRepositoryImpl();
        repo.setDataSource(dataSource);
        repo.setCreateTableOnStartup(rememberMeProps.isCreateTableOnStartup());
        return repo;
    }

    /**
     * Password encoder for the whole app. bcrypt with cost 10.
     *
     * Spring Security finds this bean automatically for password matching,
     * so we don't need to wire a DaoAuthenticationProvider — the default
     * uses our JpaUserDetailsService + this encoder.
     *
     * UserService also injects this to hash passwords on write.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    // Note: JpaUserDetailsService (in com.angle.trading.user) is picked up
    // automatically as a Spring @Service bean implementing UserDetailsService.
    // The in-memory users() bean has been removed; users now live in the
    // app_user MySQL table, seeded on first boot by UserSeeder.
}
