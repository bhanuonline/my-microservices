package com.example.apigateway.dynamicroutes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Bridges DB rows ↔ Spring Cloud Gateway RouteDefinition.
 *
 * Registered as a bean → Spring composes it with the yml-based locator so
 * routes from both sources are merged into the routing engine.
 *
 * Predicates/filters/metadata stored as JSON blobs (see RouteEntity).
 */
@Component
@ConditionalOnProperty(prefix = "gateway.dynamic-routes", name = "enabled", havingValue = "true")
public class JdbcRouteDefinitionRepository implements RouteDefinitionRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcRouteDefinitionRepository.class);

    private static final TypeReference<List<PredicateDefinition>> PREDICATE_LIST =
            new TypeReference<>() {};
    private static final TypeReference<List<FilterDefinition>> FILTER_LIST =
            new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> META_MAP =
            new TypeReference<>() {};

    private final RouteEntityRepository repo;
    private final ObjectMapper mapper;

    public JdbcRouteDefinitionRepository(RouteEntityRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        return repo.findByEnabledTrue()
                .map(this::toDefinition)
                .onErrorContinue((err, obj) ->
                        log.warn("Skipping invalid route from DB: {}", err.getMessage()));
    }

    @Override
    public Mono<Void> save(Mono<RouteDefinition> route) {
        return route.map(this::toEntity)
                .flatMap(repo::save)
                .then();
    }

    @Override
    public Mono<Void> delete(Mono<String> routeId) {
        return routeId.flatMap(repo::deleteById);
    }

    private RouteDefinition toDefinition(RouteEntity e) {
        RouteDefinition def = new RouteDefinition();
        def.setId(e.getId());
        def.setUri(URI.create(e.getUri()));
        def.setOrder(e.getRouteOrder() == null ? 0 : e.getRouteOrder());
        def.setPredicates(readJson(e.getPredicates(), PREDICATE_LIST, List.of()));
        def.setFilters(readJson(e.getFilters(), FILTER_LIST, List.of()));
        Map<String, Object> meta = readJson(e.getMetadata(), META_MAP, Map.of());
        if (!meta.isEmpty()) def.setMetadata(meta);
        return def;
    }

    private RouteEntity toEntity(RouteDefinition def) {
        RouteEntity e = new RouteEntity();
        e.setId(def.getId());
        e.setUri(def.getUri().toString());
        e.setRouteOrder(def.getOrder());
        e.setPredicates(writeJson(def.getPredicates()));
        e.setFilters(writeJson(def.getFilters()));
        e.setMetadata(writeJson(def.getMetadata()));
        e.setEnabled(Boolean.TRUE);
        e.setUpdatedAt(LocalDateTime.now());
        return e;
    }

    private <T> T readJson(String json, TypeReference<T> type, T defaultValue) {
        if (json == null || json.isBlank()) return defaultValue;
        try {
            return mapper.readValue(json, type);
        } catch (Exception ex) {
            log.warn("Failed to parse JSON: {} — {}", json, ex.getMessage());
            return defaultValue;
        }
    }

    private String writeJson(Object value) {
        if (value == null) return null;
        if (value instanceof List<?> l && l.isEmpty()) return null;
        if (value instanceof Map<?, ?> m && m.isEmpty()) return null;
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize route field: " + value, ex);
        }
    }
}
