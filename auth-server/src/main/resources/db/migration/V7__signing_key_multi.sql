-- Feature 3: multi-key signing + rotation.
-- Adds a status column so we can distinguish PRIMARY (signs new tokens),
-- SECONDARY (verifies existing tokens only), and RETIRED (kept for audit).
--
-- Existing 'active' column keeps its meaning: active=1 → key appears in JWKS.
-- After this migration:
--   status=PRIMARY   → active=1
--   status=SECONDARY → active=1
--   status=RETIRED   → active=0

ALTER TABLE signing_key
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PRIMARY';

-- Migrate: the single existing row is currently active=1; make it PRIMARY.
-- Any hypothetical inactive rows become RETIRED.
UPDATE signing_key
   SET status = CASE WHEN active = 1 THEN 'PRIMARY' ELSE 'RETIRED' END;
