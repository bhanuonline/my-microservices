package com.example.resourceserver;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link SecurityConfig.CombinedAuthoritiesConverter}.
 *
 * <p>No Spring context — just the converter under test. Fast feedback loop.
 */
class CombinedAuthoritiesConverterTest {

    private final SecurityConfig.CombinedAuthoritiesConverter converter =
            new SecurityConfig.CombinedAuthoritiesConverter(scopesConverter());

    @Test
    void scopeClaimOnly() {
        Jwt jwt = jwt().claim("scope", "read write").build();

        List<String> auths = authNames(converter.convert(jwt));

        assertThat(auths).containsExactlyInAnyOrder("SCOPE_read", "SCOPE_write");
    }

    @Test
    void rolesClaimAsList() {
        Jwt jwt = jwt().claim("roles", List.of("USER", "AUDITOR")).build();

        List<String> auths = authNames(converter.convert(jwt));

        assertThat(auths).containsExactlyInAnyOrder("ROLE_USER", "ROLE_AUDITOR");
    }

    @Test
    void rolesClaimAsCsvString() {
        Jwt jwt = jwt().claim("roles", "ADMIN,SUPPORT").build();

        List<String> auths = authNames(converter.convert(jwt));

        assertThat(auths).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_SUPPORT");
    }

    @Test
    void rolesClaimAsSpaceSeparatedString() {
        Jwt jwt = jwt().claim("roles", "ADMIN SUPPORT").build();

        List<String> auths = authNames(converter.convert(jwt));

        assertThat(auths).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_SUPPORT");
    }

    @Test
    void rolePrefixedValuePassesThrough() {
        // If caller already provides ROLE_X, don't double-prefix to ROLE_ROLE_X.
        Jwt jwt = jwt().claim("roles", List.of("ROLE_SOMETHING")).build();

        List<String> auths = authNames(converter.convert(jwt));

        assertThat(auths).containsExactly("ROLE_SOMETHING");
    }

    @Test
    void bothScopeAndRolesClaims() {
        Jwt jwt = jwt()
                .claim("scope", "read")
                .claim("roles", List.of("ADMIN"))
                .build();

        List<String> auths = authNames(converter.convert(jwt));

        assertThat(auths).containsExactlyInAnyOrder("SCOPE_read", "ROLE_ADMIN");
    }

    @Test
    void noClaimsReturnsEmpty() {
        Jwt jwt = jwt().build();

        List<String> auths = authNames(converter.convert(jwt));

        assertThat(auths).isEmpty();
    }

    @Test
    void blankRolesClaimIgnored() {
        Jwt jwt = jwt().claim("roles", "   ").build();

        assertThat(authNames(converter.convert(jwt))).isEmpty();
    }

    // ── helpers ────────────────────────────────────────────────────────

    private static JwtGrantedAuthoritiesConverter scopesConverter() {
        JwtGrantedAuthoritiesConverter c = new JwtGrantedAuthoritiesConverter();
        c.setAuthorityPrefix("SCOPE_");
        c.setAuthoritiesClaimName("scope");
        return c;
    }

    private static Jwt.Builder jwt() {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("test-user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
    }

    private static List<String> authNames(Collection<GrantedAuthority> authorities) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
    }
}
