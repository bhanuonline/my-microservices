package com.example.apigateway.dynamicroutes;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface RouteEntityRepository extends ReactiveCrudRepository<RouteEntity, String> {
    Flux<RouteEntity> findByEnabledTrue();
}
