package com.example.apigateway.dynamicroutes;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("route_audit")
public class RouteAuditEntity {

    @Id
    private Long id;

    @Column("route_id")
    private String routeId;

    private String action;              // CREATE | UPDATE | DELETE | REFRESH

    private String actor;               // JWT sub / API-key ownerId

    @Column("actor_type")
    private String actorType;           // JWT | API_KEY | ANONYMOUS

    private String payload;             // JSON snapshot (nullable)

    private String outcome;             // SUCCESS | VALIDATION_FAILED | STORE_ERROR

    private String reason;              // failure detail (nullable)

    @Column("correlation_id")
    private String correlationId;

    @Column("created_at")
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRouteId() { return routeId; }
    public void setRouteId(String routeId) { this.routeId = routeId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
