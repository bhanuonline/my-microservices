package com.example.securityapps.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    // Public endpowint - no authentication needed
    @GetMapping("/test")
    public ResponseEntity<Map<String, String>> publicEndpoint() {
        Map<String, String> body = Map.of(
                "status", "ok",
                "message", "This is a PUBLIC API endpoint — no authentication required."
        );
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(body);
    }

    // Secure endpoint - requires authentication
    @GetMapping("/secure")
    public ResponseEntity<Map<String, Object>> secureEndpoint(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            // If somehow unauthenticated (should be blocked by security filter anyway)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized"));
        }

        Map<String, Object> body = Map.of(
                "status", "ok",
                "message", "Secure endpoint access granted!",
                "user", auth.getName(),
                "authorities", auth.getAuthorities()
        );

        // Return 200 OK with user info
        return ResponseEntity.ok(body);
    }

    // Example of custom status
    @GetMapping("/custom")
    public ResponseEntity<Map<String, String>> customResponse() {
        Map<String, String> body = Map.of(
                "info", "Custom response with CREATED status"
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("X-Example-Header", "demo-value")
                .body(body);
    }
}