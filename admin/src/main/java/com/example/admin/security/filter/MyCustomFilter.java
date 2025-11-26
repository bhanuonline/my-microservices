package com.example.admin.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component // optional - you can also create this as a @Bean inside the config
@Slf4j
public class MyCustomFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // 🌟 Place your logic here
        log.info("MyCustomFilter executing before controller for URI: {}", request.getRequestURI());
        
        // continue the filter chain (important!)
        filterChain.doFilter(request, response);
    }
}