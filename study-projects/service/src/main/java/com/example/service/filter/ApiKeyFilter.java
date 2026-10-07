package com.example.service.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public  class ApiKeyFilter extends OncePerRequestFilter {
    
        @Value("${app.api.key}")
        private String apiKey;
    
        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain)
                throws ServletException, IOException {
    
            String requestKey = request.getHeader("X-API-KEY");
            String uri = request.getRequestURI();
    
            log.debug("Incoming request URI: {}", uri);
            log.debug("X-API-KEY header: {}", requestKey);
    
            // Allow public endpoints without API key
            if (uri.startsWith("/public")) {
                log.debug("Public endpoint accessed, skipping API key check");
                filterChain.doFilter(request, response);
                return;
            }
    
            if (apiKey.equals(requestKey)) {
                log.debug("API key valid, proceeding with request");
                filterChain.doFilter(request, response);
            } else {
                log.warn("Unauthorized access attempt to URI: {} with API Key: {}", uri, requestKey);
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.getWriter().write("Invalid API Key");
            }
        }
    }