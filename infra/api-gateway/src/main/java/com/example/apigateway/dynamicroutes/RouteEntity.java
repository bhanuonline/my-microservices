package com.example.apigateway.dynamicroutes;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("route_definition")
public class RouteEntity {

    @Id
    private String id;

    private String uri;

    /** JSON-encoded list of PredicateDefinition */
    private String predicates;

    /** JSON-encoded list of FilterDefinition (nullable) */
    private String filters;

    @Column("route_order")
    private Integer routeOrder;

    /** JSON-encoded metadata map (nullable) */
    private String metadata;

    private Boolean enabled;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUri() { return uri; }
    public void setUri(String uri) { this.uri = uri; }

    public String getPredicates() { return predicates; }
    public void setPredicates(String predicates) { this.predicates = predicates; }

    public String getFilters() { return filters; }
    public void setFilters(String filters) { this.filters = filters; }

    public Integer getRouteOrder() { return routeOrder; }
    public void setRouteOrder(Integer routeOrder) { this.routeOrder = routeOrder; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
