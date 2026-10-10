package com.example.resourceserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Public API of the resource-server.
 *
 * <h2>Endpoints</h2>
 * <pre>
 * ┌───────────────────────────────────────────────────────────────────────┐
 * │  Method  Path         Required authority    Purpose                   │
 * │  ──────  ───────────  ────────────────────  ────────────────────────  │
 * │  GET     /api/hello   SCOPE_read            Hello message             │
 * │  POST    /api/echo    SCOPE_write           Echoes back a JSON body   │
 * │  GET     /api/admin   ROLE_ADMIN            Admin-only ping           │
 * │  GET     /api/me      (any auth)            JWT claim diagnostic      │
 * └───────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>How method-level security works here</h2>
 * <ul>
 *   <li>{@link org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity}
 *       on {@link SecurityConfig} enables {@code @PreAuthorize}.</li>
 *   <li>{@code @PreAuthorize("hasAuthority('SCOPE_read')")} checks the
 *       authority list on the principal (populated by
 *       {@link SecurityConfig.CombinedAuthoritiesConverter}).</li>
 *   <li>Missing authority → Spring Security returns 403 Forbidden.</li>
 *   <li>Missing / invalid JWT → 401 Unauthorized (happens earlier in the
 *       filter chain, before method dispatch).</li>
 * </ul>
 *
 * <h2>Interview soundbites</h2>
 * <ul>
 *   <li>SCOPE is a token-level grant; ROLE is a user-level grant. Both end
 *       up as {@link GrantedAuthority GrantedAuthorities} in Spring.</li>
 *   <li>{@code hasAuthority('SCOPE_read')} is a strict string match;
 *       {@code hasRole('ADMIN')} is syntactic sugar for
 *       {@code hasAuthority('ROLE_ADMIN')}.</li>
 *   <li>Enforcement at the method level survives refactoring better than
 *       path-based rules — the policy lives next to the code it protects.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);

    /**
     * Returns a greeting. Requires the {@code SCOPE_read} authority
     * (equivalent to the {@code read} scope on the OAuth2 token).
     *
     * @param auth injected by Spring Security — the authenticated principal
     * @return 200 OK with a greeting string
     */
    @GetMapping("/hello")
    @PreAuthorize("hasAuthority('SCOPE_read')")
    public String hello(Authentication auth) {
        log.info("GET /api/hello sub={}", auth.getName());
        return "Hello, " + auth.getName();
    }

    /**
     * Echoes a client-supplied JSON object. Requires the {@code SCOPE_write}
     * authority. Demonstrates that scope-based gating applies to writes too.
     *
     * @param body the JSON body (freeform)
     * @param auth the authenticated principal
     * @return 200 OK with a wrapper containing the echoed body + metadata
     */
    @PostMapping("/echo")
    @PreAuthorize("hasAuthority('SCOPE_write')")
    public ResponseEntity<Map<String, Object>> echo(@RequestBody Map<String, Object> body,
                                                    Authentication auth) {
        log.info("POST /api/echo sub={} payloadKeys={}", auth.getName(), body.keySet());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("echoedBy", auth.getName());
        response.put("timestamp", Instant.now().toString());
        response.put("payload", body);
        return ResponseEntity.ok(response);
    }

    /**
     * Admin-only ping. Requires {@code ROLE_ADMIN} (expressed via Spring's
     * {@code hasRole} shortcut, which internally checks the
     * {@code ROLE_ADMIN} authority added by
     * {@link SecurityConfig.CombinedAuthoritiesConverter}).
     *
     * @param auth the authenticated principal
     * @return 200 OK with a short confirmation string
     */
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public String admin(Authentication auth) {
        log.info("GET /api/admin sub={} authorities={}", auth.getName(), auth.getAuthorities());
        return "Admin area — welcome, " + auth.getName();
    }

    /**
     * Diagnostic endpoint — returns a snapshot of the JWT claims and
     * resolved authorities for the current request. Useful for debugging
     * token issues ("what does the server actually see?") and for teaching.
     *
     * <p>Only requires authentication (no specific scope/role). The
     * information disclosed is already known to the client — they issued
     * the token — so this is not a privacy risk.
     *
     * @param auth the authenticated principal (always a
     *             {@link JwtAuthenticationToken} in this service)
     * @return 200 OK with sub, authorities, token-time claims, and the
     *         full raw claim map
     */
    @GetMapping("/me")
    public Map<String, Object> me(Authentication auth) {
        log.debug("GET /api/me sub={}", auth.getName());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", auth.getName());
        body.put("authorities", auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList()));

        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            body.put("issuer", String.valueOf(jwt.getIssuer()));
            body.put("issuedAt", String.valueOf(jwt.getIssuedAt()));
            body.put("expiresAt", String.valueOf(jwt.getExpiresAt()));
            body.put("tokenType", "JWT");
            body.put("claims", jwt.getClaims());
        } else {
            body.put("tokenType", auth.getClass().getSimpleName());
        }
        return body;
    }
}
