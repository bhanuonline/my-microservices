package com.example.securityapps.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.logging.Logger;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger logger = Logger.getLogger(RequestLoggingFilter.class.getName());

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String uri = request.getRequestURI();
        String method = request.getMethod();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null && auth.isAuthenticated()) ? auth.getName() : "anonymous";

        // Determine if the endpoint is public or protected
        boolean isPublic =
                uri.startsWith("/public/")
                        || uri.startsWith("/css/")
                        || uri.startsWith("/js/")
                        || uri.equals("/login")
                        || uri.equals("/")
                        || uri.startsWith("/api/test");

        String accessType = isPublic ? "PUBLIC" : "PROTECTED";

        logger.info(() -> String.format(
                "[%s] %s %s [%s] requested by %s",
                LocalDateTime.now(),
                method,
                uri,
                accessType,
                username
        ));

        // Continue filter chain
        filterChain.doFilter(request, response);

        // Log response info
        logger.info(() -> String.format(
                "[%s] %s %s completed with status %d for %s",
                LocalDateTime.now(),
                method,
                uri,
                response.getStatus(),
                username
        ));
    }
}