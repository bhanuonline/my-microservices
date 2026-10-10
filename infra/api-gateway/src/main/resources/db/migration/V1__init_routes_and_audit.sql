-- Flyway migration V1 — initial schema for the postgres profile.
-- Mirrors src/main/resources/schema.sql (used by the default H2 profile) but
-- with Postgres idioms: BIGSERIAL instead of AUTO_INCREMENT, no IF NOT EXISTS
-- (Flyway guarantees idempotency via flyway_schema_history).

CREATE TABLE route_definition (
    id           VARCHAR(64)  PRIMARY KEY,
    uri          VARCHAR(255) NOT NULL,
    predicates   TEXT         NOT NULL,
    filters      TEXT,
    route_order  INTEGER      DEFAULT 0,
    metadata     TEXT,
    enabled      BOOLEAN      DEFAULT TRUE,
    created_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE route_audit (
    id             BIGSERIAL     PRIMARY KEY,
    route_id       VARCHAR(64)   NOT NULL,
    action         VARCHAR(16)   NOT NULL,   -- CREATE | UPDATE | DELETE | REFRESH
    actor          VARCHAR(128),
    actor_type     VARCHAR(16),               -- JWT | API_KEY | ANONYMOUS
    payload        TEXT,                      -- JSON snapshot (nullable for DELETE / REFRESH)
    outcome        VARCHAR(16)   NOT NULL,   -- SUCCESS | VALIDATION_FAILED | STORE_ERROR
    reason         VARCHAR(255),
    correlation_id VARCHAR(64),
    created_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_route_audit_route_id   ON route_audit (route_id);
CREATE INDEX idx_route_audit_created_at ON route_audit (created_at);
