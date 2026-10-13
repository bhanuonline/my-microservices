package com.example.orderservice.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

// Toggle via config (no restart needed if using Spring Cloud Config):
//   orderservice.maintenance.enabled=true
//
// All /api/** calls return 503 while enabled.
// Actuator endpoints are excluded (registered on a different path pattern in WebMvcConfig).
@Component
public class MaintenanceModeInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceModeInterceptor.class);

    @Value("${orderservice.maintenance.enabled:false}")
    private boolean maintenanceEnabled;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (maintenanceEnabled) {
            log.warn("Maintenance mode ON — rejecting {} {}", request.getMethod(), request.getRequestURI());
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Service is under maintenance. Please try again later.\"}");
            return false;   // false = stop processing, controller never runs
        }
        return true;
    }
}
