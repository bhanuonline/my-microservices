-- Feature 4: audit log.
-- Broaden the V5 client_audit table so a single table records changes to
-- clients, users, and signing keys.
--
-- Design choices:
--   • Keep the table name 'client_audit' so V5 history stays readable.
--     Semantics are now "audit_log"; the physical name lags.
--   • Rename client_id → subject_id (still holds the target identifier).
--   • Add subject_type column so a single query can filter by kind
--     (CLIENT / USER / KEY).
--   • Add indexes on the columns admin queries will filter on.
--
-- We keep the existing 'diff_json' column for JSON snapshots.

ALTER TABLE client_audit
    CHANGE COLUMN client_id subject_id VARCHAR(200),
    ADD COLUMN subject_type VARCHAR(16) NOT NULL DEFAULT 'CLIENT' AFTER action,
    ADD INDEX idx_audit_subject (subject_type, subject_id),
    ADD INDEX idx_audit_actor (actor),
    ADD INDEX idx_audit_changed (changed_at);
