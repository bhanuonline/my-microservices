package com.example.userservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Canary v2 endpoint. Same JVM as v1, different path prefix.
 *
 * The gateway routes /api/v1/users/** to EITHER:
 *   - user-service-v1 route → /api/v1/users/... (existing UserController)
 *   - user-service-v2 route → /api/v2/users/... (this controller, via RewritePath)
 *
 * Response shape differs from v1 so demo requests can visibly identify which
 * version served them (jq -r .canaryVersion).
 */
@RestController
@RequestMapping("/api/v2/users")
public class UserV2Controller {

    @GetMapping
    public List<Map<String, Object>> list() {
        return List.of(
                Map.of("id", 1, "name", "Alice", "email", "alice@example.com", "canaryVersion", "v2"),
                Map.of("id", 2, "name", "Bob",   "email", "bob@example.com",   "canaryVersion", "v2")
        );
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        // Extra fields vs v1 to demonstrate schema evolution
        return Map.of(
                "id", 1,
                "name", "Alice",
                "email", "alice@example.com",
                "canaryVersion", "v2",
                "features", List.of("dark-mode", "beta-search"),
                "note", "You are seeing the v2 canary response"
        );
    }

    @GetMapping("/{id}")
    public Map<String, Object> one(@PathVariable Long id) {
        return Map.of(
                "id", id,
                "name", "User-" + id,
                "email", "user-" + id + "@example.com",
                "canaryVersion", "v2"
        );
    }
}
