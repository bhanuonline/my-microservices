package com.example.securityapps.config;

import com.example.securityapps.filter.RequestLoggingFilter;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import javax.sql.DataSource;


@Configuration
@EnableWebSecurity
@Slf4j
public class SecurityConfig {



    // 🧠 API security (Postman, mobile apps, etc.)
    @Bean
    @Order(1)
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http
                // Only apply to /api/** endpoints
                .securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/test/**").permitAll()
                        .anyRequest().authenticated()
                )

                // Use Basic Auth or token‑based auth for APIs
                .httpBasic(Customizer.withDefaults())
                // Disable CSRF for stateless API


                // Below is the wring config
//                .formLogin(form -> form
//                        .loginPage("/login")
//                        .defaultSuccessUrl("/", true)
//                        .permitAll()
//                )
//                .logout(logout -> logout
//                        .logoutSuccessUrl("/login?logout")
//                )
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    // 🌐 Browser-based security (HTML pages, form login)
    @Bean
    @Order(3)
    SecurityFilterChain webSecurity(HttpSecurity http,
                                    RequestLoggingFilter requestLoggingFilter) throws Exception {
        http
                // Applies to everything else
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/public/**", "/css/**", "/js/**").permitAll()
                        .anyRequest().authenticated()
                )
                // Form login for browsers
                .formLogin(login -> login
                       .loginPage("/login") // for custom lgin page or .formLogin(withDefaults());
                        .defaultSuccessUrl("/", true)
                        .permitAll()
                )
               // .logout(logout -> logout.permitAll());
        .logout(logout -> logout
                .logoutSuccessUrl("/login?logout") // redirect after logout
                .permitAll()
        );

        http.addFilterBefore(requestLoggingFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // 2️⃣ Admin section
    @Bean
    @Order(2)
    SecurityFilterChain adminSecurity(HttpSecurity http) throws Exception {
        http
                // Apply this chain to only /admin/** paths
                .securityMatcher("/admin/**")

                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/login").permitAll()   // login page is public
                        .anyRequest().hasRole("ADMIN")                 // all others require ADMIN
                )

                // Form login configuration
                .formLogin(form -> form
                        .loginPage("/admin/login")          // where to redirect unauthenticated users
                        .loginProcessingUrl("/admin/login") // form POST handler
                        .defaultSuccessUrl("/admin/home", true)
                        .failureUrl("/admin/login?error=true")
                        .permitAll()
                )

                // Logout configuration
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutRequestMatcher(new AntPathRequestMatcher("/admin/logout", "GET"))
                        .logoutSuccessUrl("/admin/login?logout=true")
                        .permitAll()
                )
                .exceptionHandling(ex -> ex
                        .accessDeniedPage("/admin/login")   // optional safety net
                );

        return http.build();
    }


    // Adds one test user
    @Bean
    UserDetailsService userDetailsService() {
        UserDetails user = User.withUsername("user")
                .password("{noop}password")
                .roles("USER")
                .build();

        UserDetails admin = User.withUsername("admin")
                .password("{noop}admin123")
                .roles("ADMIN")
                .build();

        return new InMemoryUserDetailsManager(user, admin);
    }


    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    @Bean
    public UserDetailsManager users(DataSource dataSource) {
        JdbcUserDetailsManager users = new JdbcUserDetailsManager(dataSource);

        // You can create a default user if not already in DB:
        if (!users.userExists("admin")) {
            users.createUser(
                    User.withUsername("admin")
                            .password("{noop}password")  // {noop} = no encoding, for demo
                            .roles("ADMIN")
                            .build()
            );
        }

        return users;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig)
            throws Exception {
        return authConfig.getAuthenticationManager();
    }

}