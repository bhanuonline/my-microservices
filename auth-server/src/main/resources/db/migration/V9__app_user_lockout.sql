-- Feature 5: account lockout.
-- Adds a running failure counter + a soft-lock expiry timestamp.
--
--   failed_attempts  running count since last successful login (or forever if none)
--   locked_until     when the current lock expires; NULL = not locked
--
-- CustomUserDetails.isAccountNonLocked() returns true iff locked_until IS NULL
-- OR locked_until < now(). No migration is needed to un-lock; simply setting
-- locked_until back to NULL (or letting it expire) restores access.

ALTER TABLE app_user
    ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN locked_until TIMESTAMP NULL DEFAULT NULL;
