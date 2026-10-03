package com.example.apigateway.apikey;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * Spring Security Authentication for API-key–authenticated requests.
 *
 * getPrincipal()  → ownerId (used by downstream code / KeyResolver)
 * getCredentials() → null (never expose the raw key)
 * getAuthorities() → SCOPE_read, SCOPE_write, ... derived from ApiKeyRecord.scopes
 */
public class ApiKeyAuthentication extends AbstractAuthenticationToken {

    private final String keyId;
    private final String ownerId;
    private final String prefix;

    public ApiKeyAuthentication(String keyId, String ownerId, String prefix,
                                Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.keyId = keyId;
        this.ownerId = ownerId;
        this.prefix = prefix;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return ownerId;
    }

    @Override
    public String getName() {
        return ownerId;
    }

    public String getKeyId() { return keyId; }
    public String getOwnerId() { return ownerId; }
    public String getPrefix() { return prefix; }
}
