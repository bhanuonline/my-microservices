CREATE TABLE signing_key (
    kid         VARCHAR(64) PRIMARY KEY,
    public_key  TEXT        NOT NULL,
    private_key TEXT        NOT NULL,
    active      TINYINT(1)  NOT NULL DEFAULT 1,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
