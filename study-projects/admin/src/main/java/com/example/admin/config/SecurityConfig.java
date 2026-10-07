package com.example.admin.config;

import com.example.admin.security.authentication.MyAuthenticationProvider;
import com.example.admin.security.filter.MyCustomFilter;
import com.example.admin.security.filter.MyLoggingFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Slf4j
public class SecurityConfig {
    private final MyCustomFilter myCustomFilter;
    private final MyLoggingFilter myLoggingFilter;
    //private final MyAuthenticationProvider authenticationProvider;

    public SecurityConfig(MyCustomFilter customFilter,
                          MyLoggingFilter loggingFilter) {
        this.myCustomFilter = customFilter;
        this.myLoggingFilter = loggingFilter;
       // this.authenticationProvider = authenticationProvider;
    }

//    @Bean
//    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
//        http
//            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
//            .formLogin(Customizer.withDefaults())
//            .httpBasic(Customizer.withDefaults());
//        return http.build();
//    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        // custom login failure handler
        AuthenticationFailureHandler failureHandler = (request, response, exception) -> {
            String username = request.getParameter("username");
            System.out.println("⚠️ Login failed for user: " + username + " Reason: " + exception.getMessage());
            response.sendRedirect("/login?error");
        };

        AccessDeniedHandler accessDeniedHandler = (HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) -> {
            // You can log or store a message in session
            request.setAttribute("errorMessage", ex.getMessage());
            // Send user to a custom error page
            request.getRequestDispatcher("/access-denied").forward(request, response);
        };

        http
                // register *your* AuthenticationProvider
                //.authenticationProvider(authenticationProvider)
                // Which URLs are public and which require specific roles
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        //public end point
                        .requestMatchers("/", "/home", "/about", "/css/**"
                                , "/js/**", "/images/**","/users/register","/.well-known/**")
                        .permitAll()
                        .requestMatchers("/public/**").permitAll()
                       // .requestMatchers("/users/register").permitAll()
                        // Admin-only endpoints
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // All other endpoints require login
                        .anyRequest().authenticated())

                // Insert your custom filter before username/password filter
                .addFilterBefore(myCustomFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(myLoggingFilter, UsernamePasswordAuthenticationFilter.class)
                // Login form configuration
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/home", true)
                        .failureHandler(failureHandler).permitAll())

                // Logout configuration
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll())
                .exceptionHandling(exception -> exception.accessDeniedHandler(accessDeniedHandler));

        return http.build();
    }

//    @Bean
//    UserDetailsService users() {
//        UserDetails user = User.withUsername("user")
//                .password("{noop}password") // {noop} = no encoding
//                .roles("USER")
//                .build();
//        return new InMemoryUserDetailsManager(user);
//    }

//    @Bean
//    public UserDetailsService users(PasswordEncoder encoder) {
//        UserDetails admin = User.builder().username("admin").password(encoder.encode("admin")).roles("ADMIN").build();
//        var user = User.withUsername("user").password(encoder.encode("user")).roles("USER") // role USER
//                .build();
//        var manager = User.withUsername("manager").password(encoder.encode("manager")).roles("MANAGER") // role MANAGER
//                .build();
//        return new InMemoryUserDetailsManager(admin, user, manager);
//    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public ApplicationRunner printSecurityFilters(FilterChainProxy proxy) {
        return args -> {
            proxy.getFilterChains().forEach(chain -> {

                chain.getFilters().forEach(f -> log.info("Filter: {}", f));
            });
        };
    }
}