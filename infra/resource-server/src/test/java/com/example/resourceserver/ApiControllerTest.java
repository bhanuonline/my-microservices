package com.example.resourceserver;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link ApiController} verifying JWT scope + role
 * enforcement.
 *
 * <h2>Why {@code @SpringBootTest} and not {@code @WebMvcTest}?</h2>
 * <p>{@code @WebMvcTest} loads just the web slice; the auto-configured
 * {@code JwtDecoder} (which depends on actuator + resource-server auto-config)
 * is absent. {@code jwt()} test post-processor would still work for a slice
 * test, but we also want {@link SecurityConfig.CombinedAuthoritiesConverter}
 * exercised end-to-end, which requires the full context.
 *
 * <h2>How the mock JWT works</h2>
 * <p>{@code jwt().authorities(...)} skips signature validation entirely —
 * MockMvc installs a fake {@code JwtAuthenticationToken} directly into the
 * security context. We're testing authorization logic, not token parsing.
 *
 * <p>{@link TestPropertySource} overrides the issuer-uri so Spring doesn't
 * try to reach auth-server (which isn't running during tests).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        // Fake issuer-uri — JwtDecoder isn't actually invoked (mock JWTs via
        // .jwt() post-processor), but the property must parse at boot.
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:0",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:0/jwks"
})
class ApiControllerTest {

    @Autowired
    private MockMvc mvc;

    // ──────────────────────────────────────────────────────────────────
    // GET /api/hello — SCOPE_read
    // ──────────────────────────────────────────────────────────────────

    @Test
    void helloRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/hello"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void helloAllowedWithReadScope() throws Exception {
        mvc.perform(get("/api/hello")
                        .with(jwt().jwt(j -> j.subject("alice"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_read"))))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello, alice"));
    }

    @Test
    void helloDeniedWithoutReadScope() throws Exception {
        mvc.perform(get("/api/hello")
                        .with(jwt().jwt(j -> j.subject("bob"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_write"))))
                .andExpect(status().isForbidden());
    }

    // ──────────────────────────────────────────────────────────────────
    // POST /api/echo — SCOPE_write
    // ──────────────────────────────────────────────────────────────────

    @Test
    void echoAllowedWithWriteScope() throws Exception {
        mvc.perform(post("/api/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}")
                        .with(jwt().jwt(j -> j.subject("carol"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_write"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.echoedBy").value("carol"))
                .andExpect(jsonPath("$.payload.message").value("hi"));
    }

    @Test
    void echoDeniedWithOnlyReadScope() throws Exception {
        mvc.perform(post("/api/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(jwt().jwt(j -> j.subject("dave"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_read"))))
                .andExpect(status().isForbidden());
    }

    // ──────────────────────────────────────────────────────────────────
    // GET /api/admin — ROLE_ADMIN
    // ──────────────────────────────────────────────────────────────────

    @Test
    void adminRequiresRole() throws Exception {
        mvc.perform(get("/api/admin")
                        .with(jwt().jwt(j -> j.subject("eve"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminAllowedWithAdminRole() throws Exception {
        mvc.perform(get("/api/admin")
                        .with(jwt().jwt(j -> j.subject("frank"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    // ──────────────────────────────────────────────────────────────────
    // GET /api/me — any auth
    // ──────────────────────────────────────────────────────────────────

    @Test
    void meReturnsClaims() throws Exception {
        mvc.perform(get("/api/me")
                        .with(jwt().jwt(j -> j.subject("grace")
                                        .claim("roles", List.of("USER")))
                                .authorities(new SimpleGrantedAuthority("SCOPE_read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("grace"))
                .andExpect(jsonPath("$.tokenType").value("JWT"))
                .andExpect(jsonPath("$.authorities[*]").value(Matchers.hasItem("SCOPE_read")));
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }
}
