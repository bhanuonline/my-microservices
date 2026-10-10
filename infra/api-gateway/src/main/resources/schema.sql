CREATE TABLE IF NOT EXISTS route_definition (
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

-- Append-only audit log for admin CRUD ops on routes.
-- One row per mutation. Never updated / deleted (in normal operation).
CREATE TABLE IF NOT EXISTS route_audit (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    route_id      VARCHAR(64)  NOT NULL,
    action        VARCHAR(16)  NOT NULL,          -- CREATE | UPDATE | DELETE | REFRESH
    actor         VARCHAR(128),                    -- JWT sub or API-key ownerId
    actor_type    VARCHAR(16),                     -- JWT | API_KEY | ANONYMOUS
    payload       TEXT,                            -- JSON snapshot of the RouteDefinition (nullable for DELETE/REFRESH)
    outcome       VARCHAR(16)  NOT NULL,          -- SUCCESS | VALIDATION_FAILED | STORE_ERROR
    reason        VARCHAR(255),                    -- failure message when outcome != SUCCESS
    correlation_id VARCHAR(64),
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_route_audit_route_id  ON route_audit (route_id);
CREATE INDEX IF NOT EXISTS idx_route_audit_created_at ON route_audit (created_at);
