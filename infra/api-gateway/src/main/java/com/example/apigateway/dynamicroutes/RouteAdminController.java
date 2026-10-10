package com.example.apigateway.dynamicroutes;

import com.example.apigateway.dynamicroutes.RouteAuditService.Action;
import com.example.apigateway.dynamicroutes.RouteAuditService.Outcome;
import com.example.apigateway.dynamicroutes.RouteValidator.ValidationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Admin CRUD for dynamic routes. Requires JWT auth with admin authority
 * (see GatewaySecurityConfig + AdminJwtAuthenticationConverter).
 *
 *   GET    /admin/routes                    → list active routes
 *   POST   /admin/routes                    → create/replace (validated first)
 *   PUT    /admin/routes/{id}               → update (validated first)
 *   DELETE /admin/routes/{id}               → remove
 *   POST   /admin/routes/refresh            → force RefreshRoutesEvent
 *   GET    /admin/routes/audit              → recent 100 audit events
 *   GET    /admin/routes/{id}/audit         → per-route audit history
 *
 * On each mutation:
 *   1. Validate the RouteDefinition (400 if bad — never persists)
 *   2. Save to DB
 *   3. Record audit row (best-effort)
 *   4. Publish local RefreshRoutesEvent
 *   5. Broadcast via Redis pub/sub (if enabled) for peer replicas
 */
@RestController
@RequestMapping("/admin/routes")
@ConditionalOnProperty(prefix = "gateway.dynamic-routes", name = "enabled", havingValue = "true")
public class RouteAdminController {

    private final RouteDefinitionRepository repository;
    private final ApplicationEventPublisher events;
    private final ObjectProvider<RouteRefreshPublisher> publisher;
    private final RouteValidator validator;
    private final RouteAuditService audit;
    private final RouteAuditRepository auditRepo;

    public RouteAdminController(RouteDefinitionRepository repository,
                                ApplicationEventPublisher events,
                                ObjectProvider<RouteRefreshPublisher> publisher,
                                RouteValidator validator,
                                RouteAuditService audit,
                                RouteAuditRepository auditRepo) {
        this.repository = repository;
        this.events = events;
        this.publisher = publisher;
        this.validator = validator;
        this.audit = audit;
        this.auditRepo = auditRepo;
    }

    @GetMapping
    public Flux<RouteDefinition> list() {
        return repository.getRouteDefinitions();
    }

    @PostMapping
    public Mono<ResponseEntity<?>> create(@RequestBody RouteDefinition def, ServerWebExchange exchange) {
        try {
            validator.validate(def);
        } catch (ValidationException ve) {
            return audit.record(exchange, safeId(def), Action.CREATE,
                            Outcome.VALIDATION_FAILED, def, ve.getMessage())
                    .thenReturn(ResponseEntity.badRequest().body(errorBody(ve.getMessage())));
        }
        return repository.save(Mono.just(def))
                .then(audit.record(exchange, def.getId(), Action.CREATE, Outcome.SUCCESS, def, null))
                .doOnSuccess(v -> notifyRefresh())
                .<ResponseEntity<?>>thenReturn(ResponseEntity.status(HttpStatus.CREATED).build())
                .onErrorResume(err -> audit.record(exchange, def.getId(), Action.CREATE,
                                Outcome.STORE_ERROR, def, err.getMessage())
                        .thenReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(errorBody(err.getMessage()))));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<?>> update(@PathVariable String id,
                                          @RequestBody RouteDefinition def,
                                          ServerWebExchange exchange) {
        def.setId(id);
        try {
            validator.validate(def);
        } catch (ValidationException ve) {
            return audit.record(exchange, id, Action.UPDATE, Outcome.VALIDATION_FAILED, def, ve.getMessage())
                    .thenReturn(ResponseEntity.badRequest().body(errorBody(ve.getMessage())));
        }
        return repository.save(Mono.just(def))
                .then(audit.record(exchange, id, Action.UPDATE, Outcome.SUCCESS, def, null))
                .doOnSuccess(v -> notifyRefresh())
                .<ResponseEntity<?>>thenReturn(ResponseEntity.ok().build())
                .onErrorResume(err -> audit.record(exchange, id, Action.UPDATE,
                                Outcome.STORE_ERROR, def, err.getMessage())
                        .thenReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(errorBody(err.getMessage()))));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<?>> delete(@PathVariable String id, ServerWebExchange exchange) {
        return repository.delete(Mono.just(id))
                .then(audit.record(exchange, id, Action.DELETE, Outcome.SUCCESS, null, null))
                .doOnSuccess(v -> notifyRefresh())
                .<ResponseEntity<?>>thenReturn(ResponseEntity.noContent().build())
                .onErrorResume(err -> audit.record(exchange, id, Action.DELETE,
                                Outcome.STORE_ERROR, null, err.getMessage())
                        .thenReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(errorBody(err.getMessage()))));
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<Void>> refresh(ServerWebExchange exchange) {
        notifyRefresh();
        return audit.record(exchange, "-", Action.REFRESH, Outcome.SUCCESS, null, null)
                .thenReturn(ResponseEntity.ok().build());
    }

    @GetMapping("/audit")
    public Flux<RouteAuditEntity> auditRecent() {
        return auditRepo.findRecent();
    }

    @GetMapping("/{id}/audit")
    public Flux<RouteAuditEntity> auditForRoute(@PathVariable String id) {
        return auditRepo.findByRouteIdOrderByCreatedAtDesc(id);
    }

    private void notifyRefresh() {
        events.publishEvent(new RefreshRoutesEvent(this));
        RouteRefreshPublisher pub = publisher.getIfAvailable();
        if (pub != null) {
            pub.broadcast().subscribe();
        }
    }

    private String safeId(RouteDefinition def) {
        return def != null && def.getId() != null ? def.getId() : "-";
    }

    private Map<String, String> errorBody(String message) {
        return Map.of("error", "invalid_route", "message", message);
    }
}
