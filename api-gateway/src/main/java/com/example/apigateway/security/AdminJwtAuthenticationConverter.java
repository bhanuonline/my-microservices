package com.example.apigateway.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reactive JWT → Authentication converter that populates SCOPE_* and ROLE_*
 * authorities from configurable claim names.
 *
 * Extraction rules:
 *   - scopeClaim value can be a space-separated string (`"read write admin"`)
 *     OR a List (`["read","write","admin"]`) → SCOPE_read, SCOPE_write, SCOPE_admin
 *   - rolesClaim value can be a List OR a comma-separated string
 *     → ROLE_ADMIN, ROLE_USER, ...
 *
 * Also: demoAdminSubs list bypasses claim inspection — any JWT whose `sub`
 * matches gets SCOPE_admin. For dev only. Leave empty in prod.
 */
public class AdminJwtAuthenticationConverter
        implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    private final AdminAuthProperties props;

    public AdminJwtAuthenticationConverter(AdminAuthProperties props) {
        this.props = props;
    }

    @Override
    public Mono<AbstractAuthenticationToken> convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        // Scopes → SCOPE_*
        for (String scope : extractStrings(jwt, props.getScopeClaim())) {
            authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
        }

        // Roles → ROLE_*
        for (String role : extractStrings(jwt, props.getRolesClaim())) {
            String upper = role.startsWith("ROLE_") ? role : "ROLE_" + role.toUpperCase();
            authorities.add(new SimpleGrantedAuthority(upper));
        }

        // Demo escape hatch — sub-based admin grant
        String sub = jwt.getSubject();
        if (sub != null && props.getDemoAdminSubs().contains(sub)) {
            authorities.add(new SimpleGrantedAuthority("SCOPE_admin"));
        }

        JwtAuthenticationToken token = new JwtAuthenticationToken(jwt, authorities, sub);
        return Mono.just(token);
    }

    @SuppressWarnings("unchecked")
    private Collection<String> extractStrings(Jwt jwt, String claim) {
        Object raw = jwt.getClaims().get(claim);
        if (raw == null) return Collections.emptyList();
        if (raw instanceof String s) {
            if (s.isBlank()) return Collections.emptyList();
            // handle both "read write admin" and "read,write,admin"
            String delim = s.contains(",") ? "," : "\\s+";
            List<String> out = new ArrayList<>();
            for (String piece : s.split(delim)) {
                String trimmed = piece.trim();
                if (!trimmed.isEmpty()) out.add(trimmed);
            }
            return out;
        }
        if (raw instanceof Collection<?> col) {
            List<String> out = new ArrayList<>(col.size());
            for (Object item : col) {
                if (item != null) out.add(item.toString());
            }
            return out;
        }
        return Collections.emptyList();
    }
}
