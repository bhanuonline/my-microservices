package com.example.apigateway.dynamicroutes;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface RouteAuditRepository extends ReactiveCrudRepository<RouteAuditEntity, Long> {

    Flux<RouteAuditEntity> findByRouteIdOrderByCreatedAtDesc(String routeId);

    @Query("SELECT * FROM route_audit ORDER BY created_at DESC LIMIT 100")
    Flux<RouteAuditEntity> findRecent();
}
