-- ─────────────────────────────────────────────────────────────────────────────
-- Migration 002 — create candle_job_run + candle_job_run_detail tables
-- Hibernate (ddl-auto=update) will create these automatically on startup.
-- Run this manually only if auto-creation fails.
--
-- Run: mysql -u root -p angleapp < 002_candle_job_run.sql
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS candle_job_run (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    job_name       VARCHAR(64)    NOT NULL,
    trigger_type   VARCHAR(16)    NOT NULL,
    triggered_by   VARCHAR(64)    NOT NULL,
    target_token   VARCHAR(32)    DEFAULT NULL,
    started_at     TIMESTAMP      NOT NULL,
    ended_at       TIMESTAMP      DEFAULT NULL,
    status         VARCHAR(16)    NOT NULL,
    error_message  VARCHAR(500)   DEFAULT NULL,
    total_count    INT            NOT NULL DEFAULT 0,
    done_count     INT            NOT NULL DEFAULT 0,
    failed_count   INT            NOT NULL DEFAULT 0,
    total_saved    INT            NOT NULL DEFAULT 0,
    interval_type  VARCHAR(16)    DEFAULT NULL,
    lookback_days  INT            DEFAULT NULL,
    thread_count   INT            DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_job_started (started_at DESC),
    KEY idx_job_status  (status),
    KEY idx_job_name    (job_name)
);

CREATE TABLE IF NOT EXISTS candle_job_run_detail (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    run_id         BIGINT         NOT NULL,
    symbol_token   VARCHAR(32)    NOT NULL,
    symbol         VARCHAR(64)    DEFAULT NULL,
    status         VARCHAR(16)    NOT NULL,
    candles_saved  INT            NOT NULL DEFAULT 0,
    error_msg      VARCHAR(500)   DEFAULT NULL,
    started_at     TIMESTAMP      DEFAULT NULL,
    ended_at       TIMESTAMP      DEFAULT NULL,
    duration_ms    INT            DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_detail_run    (run_id),
    KEY idx_detail_status (run_id, status),
    CONSTRAINT fk_detail_run FOREIGN KEY (run_id) REFERENCES candle_job_run(id)
);
