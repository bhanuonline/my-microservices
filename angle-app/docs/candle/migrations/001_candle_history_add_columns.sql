-- ─────────────────────────────────────────────────────────────────────────────
-- Migration 001 — candle_history: add symbol + open_interest columns,
--                                 upgrade idx_lookup to covering index
--
-- Safe to run on live table.
-- ADD COLUMN:  metadata-only in InnoDB (MySQL 8) — instant, no table rebuild.
-- DROP+ADD INDEX: causes index rebuild — run during off-hours (< 2 min on 50k rows).
--
-- Run:
--   mysql -u root -p angleapp < 001_candle_history_add_columns.sql
-- ─────────────────────────────────────────────────────────────────────────────

-- Step 1: Add new columns (instant — metadata change only in MySQL 8 InnoDB)

ALTER TABLE candle_history
    ADD COLUMN symbol       VARCHAR(64) DEFAULT NULL
        COMMENT 'Display name e.g. "Nifty 50" — denormalised from bias_instrument at save time'
        AFTER volume,

    ADD COLUMN open_interest BIGINT DEFAULT NULL
        COMMENT 'Open interest — NFO/MCX futures only, NULL for equity and index candles'
        AFTER symbol;

-- Step 2: Upgrade idx_lookup to covering index (adds close as 4th column)
-- Screener queries read (symbol_token, interval_type, ts, close) → no table row fetch needed.

ALTER TABLE candle_history
    DROP   INDEX idx_lookup,
    ADD    INDEX idx_lookup (symbol_token, interval_type, ts, close);

-- Step 3: Backfill symbol for existing rows (one-time, safe to run again)
-- Joins candle_history to bias_instrument on symbol_token.
-- Rows with no matching instrument stay NULL.

UPDATE candle_history c
    JOIN bias_instrument b ON b.symbol_token = c.symbol_token
SET c.symbol = b.symbol
WHERE c.symbol IS NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- Verify
-- ─────────────────────────────────────────────────────────────────────────────

SELECT
    COUNT(*)                                          AS total_rows,
    SUM(symbol IS NOT NULL)                           AS rows_with_symbol,
    SUM(symbol IS NULL)                               AS rows_without_symbol,
    SUM(open_interest IS NOT NULL)                    AS rows_with_oi
FROM candle_history;

SHOW INDEX FROM candle_history;
