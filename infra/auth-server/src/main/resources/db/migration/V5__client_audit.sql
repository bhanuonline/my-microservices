CREATE TABLE client_audit (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor      VARCHAR(64)  NOT NULL,
    action     VARCHAR(32)  NOT NULL,
    client_id  VARCHAR(200),
    changed_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    diff_json  TEXT
);
