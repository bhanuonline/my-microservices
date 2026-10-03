package com.example.apigateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Reads a claim from the incoming JWT and injects it as an HTTP header
 * on the downstream request.
 *
 * YAML shortcut form:
 *   filters:
 *     - AddTenantHeader=X-Tenant-Id,tenant_id
 *
 * YAML named form:
 *   filters:
 *     - name: AddTenantHeader
 *       args:
 *         header-name: X-Tenant-Id
 *         claim-name: tenant_id
 *
 * If no authentication is present, or the claim is missing, the request
 * passes through unmodified (no error).
 */
@Component
public class AddTenantHeaderGatewayFilterFactory
        extends AbstractGatewayFilterFactory<AddTenantHeaderGatewayFilterFactory.Config> {

    public AddTenantHeaderGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return Arrays.asList("headerName", "claimName");
    }

    @Override
    public GatewayFilter apply(Config config) {
        GatewayFilter filter = (exchange, chain) ->
                exchange.getPrincipal()
                        .cast(Authentication.class)
                        .filter(auth -> auth instanceof JwtAuthenticationToken)
                        .map(auth -> ((JwtAuthenticationToken) auth).getToken())
                        .map(Jwt::getClaims)
                        .flatMap(claims -> {
                            Object value = claims.get(config.getClaimName());
                            if (value == null) {
                                return chain.filter(exchange);
                            }
                            var mutated = exchange.mutate()
                                    .request(r -> r.header(config.getHeaderName(), value.toString()))
                                    .build();
                            return chain.filter(mutated);
                        })
                        .switchIfEmpty(chain.filter(exchange));

        return new OrderedGatewayFilter(filter, 0);
    }

    public static class Config {
        private String headerName = "X-Tenant-Id";
        private String claimName = "tenant_id";

        public String getHeaderName() { return headerName; }
        public void setHeaderName(String headerName) { this.headerName = headerName; }

        public String getClaimName() { return claimName; }
        public void setClaimName(String claimName) { this.claimName = claimName; }
    }
}
