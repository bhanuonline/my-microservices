package com.example.apigateway.dynamicroutes;

import com.example.apigateway.apikey.ApiKeyAuthentication;
import com.example.apigateway.metrics.GatewayMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Append-only audit trail for admin CRUD on routes. One row per attempted
 * mutation — successes AND failures (validation, store errors).
 *
 *   Action:    CREATE | UPDATE | DELETE | REFRESH
 *   Actor:     JWT sub / API-key ownerId / "anonymous"
 *   Outcome:   SUCCESS | VALIDATION_FAILED | STORE_ERROR
 *
 * Writes are best-effort — audit failures are logged and swallowed so they
 * never break the admin API. The audit table itself has no locks/constraints
 * that could cascade back to the caller.
 */
@Service
@ConditionalOnProperty(prefix = "gateway.dynamic-routes", name = "enabled", havingValue = "true")
public class RouteAuditService {

    private static final Logger log = LoggerFactory.getLogger(RouteAuditService.class);

    public enum Action { CREATE, UPDATE, DELETE, REFRESH }
    public enum Outcome { SUCCESS, VALIDATION_FAILED, STORE_ERROR }

    private final RouteAuditRepository repo;
    private final ObjectMapper mapper;
    private final GatewayMetrics metrics;

    public RouteAuditService(RouteAuditRepository repo, ObjectMapper mapper, GatewayMetrics metrics) {
        this.repo = repo;
        this.mapper = mapper;
        this.metrics = metrics;
    }

    public Mono<Void> record(ServerWebExchange exchange, String routeId, Action action,
                             Outcome outcome, RouteDefinition payload, String reason) {
        String correlationId = exchange.getRequest().getHeaders().getFirst("X-Correlation-Id");
        return actor(exchange).flatMap(actor -> {
            RouteAuditEntity entity = new RouteAuditEntity();
            entity.setRouteId(routeId);
            entity.setAction(action.name());
            entity.setActor(actor.name());
            entity.setActorType(actor.type());
            entity.setPayload(serialize(payload));
            entity.setOutcome(outcome.name());
            entity.setReason(reason);
            entity.setCorrelationId(correlationId);
            return repo.save(entity)
                    .doOnSuccess(v -> metrics.audit("success"))
                    .doOnError(e -> {
                        log.warn("audit persist failed: {}", e.getMessage());
                        metrics.audit("failure");
                    })
                    .onErrorResume(e -> Mono.empty())
                    .then();
        });
    }

    private Mono<ActorInfo> actor(ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .map(this::toActor)
                .switchIfEmpty(Mono.just(new ActorInfo("anonymous", "ANONYMOUS")));
    }

    private ActorInfo toActor(Authentication auth) {
        if (auth == null) return new ActorInfo("anonymous", "ANONYMOUS");
        if (auth instanceof ApiKeyAuthentication api) {
            return new ActorInfo(api.getOwnerId(), "API_KEY");
        }
        if (auth instanceof JwtAuthenticationToken jwt) {
            return new ActorInfo(jwt.getName(), "JWT");
        }
        return new ActorInfo(auth.getName(), "OTHER");
    }

    private String serialize(RouteDefinition def) {
        if (def == null) return null;
        try {
            return mapper.writeValueAsString(def);
        } catch (JsonProcessingException e) {
            log.warn("audit payload serialize failed: {}", e.getMessage());
            return null;
        }
    }

    private record ActorInfo(String name, String type) {}
}
