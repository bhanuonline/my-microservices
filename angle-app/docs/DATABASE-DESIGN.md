# Database Design — angle-app

> **Scope:** All tables in the `angleapp` MySQL database.
> Covers existing tables (actual schema), new tables to add,
> design decisions, index rationale, volume estimates, and retention.
>
> **Target scale:** 100 users

---

## Table of Contents

1. [Stack Overview](#1-stack-overview)
2. [Entity Relationship Diagram](#2-entity-relationship-diagram)
3. [Existing Tables](#3-existing-tables)
   - [candle_history](#31-candle_history)
   - [bias_instrument](#32-bias_instrument)
   - [bias_signal](#33-bias_signal)
   - [trade_journal](#34-trade_journal)
   - [app_user](#35-app_user)
   - [user_login_audit](#36-user_login_audit)
   - [paper_session](#37-paper_session)
   - [paper_trade](#38-paper_trade)
   - [ai_pick](#39-ai_pick)
   - [briefing](#310-briefing)
   - [news_headline](#311-news_headline)
   - [angel_token](#312-angel_token)
4. [New Tables to Add](#4-new-tables-to-add)
   - [indicator_snapshot](#41-indicator_snapshot)
   - [user_watchlist](#42-user_watchlist)
5. [Tables to Modify](#5-tables-to-modify)
   - [trade_journal — add user_id](#51-trade_journal--add-user_id)
6. [What NOT to Store](#6-what-not-to-store)
7. [Volume Estimates](#7-volume-estimates)
8. [Retention Policy](#8-retention-policy)
9. [Index Design](#9-index-design)
10. [Connection Pool Config](#10-connection-pool-config)
11. [Redis — What Goes There](#11-redis--what-goes-there)
12. [Future — When to Add TimescaleDB](#12-future--when-to-add-timescaledb)

---

## 1. Stack Overview

```
┌──────────────────────────────────────────────────────────┐
│                    DATABASE STACK                         │
│                                                          │
│  MySQL 8                                                 │
│  ─────────────────────────────────────────────────────  │
│  All relational + time-series data                       │
│  12 existing tables + 2 new tables                       │
│  HikariCP pool: max 20 connections                       │
│  Retention: varies per table (see Section 8)             │
│                                                          │
│  Redis 7                                                 │
│  ─────────────────────────────────────────────────────  │
│  Cache only — no permanent storage                       │
│  Candle cache L2 (TTL 5 min)                             │
│  Screener results (TTL 15 min)                           │
│  User sessions (TTL 30 min)                              │
│                                                          │
│  No NoSQL (MongoDB / Cassandra) — not needed at 100 users│
│  No Read Replica — one MySQL handles 100 users fine      │
└──────────────────────────────────────────────────────────┘
```

---

## 2. Entity Relationship Diagram

```
app_user
│  id, username, email, password, role
│
├──► user_login_audit      (one user → many audit rows)
│     user_id FK → app_user
│
├──► trade_journal         (one user → many trades)  ← user_id TO ADD
│     user_id FK → app_user (planned)
│
└──► user_watchlist        (one user → many watchlist items) ← NEW TABLE
      user_id FK → app_user


bias_instrument
│  id, symbol, symbol_token, exchange, broker
│
├──► candle_history        (one instrument → many candles)
│     symbol_token (no FK — denormalized for performance)
│
├──► indicator_snapshot    (one instrument → many snapshots) ← NEW TABLE
│     symbol_token (no FK — denormalized for performance)
│
└──► bias_signal           (one instrument → many signals)
      symbol_token (no FK — denormalized for performance)


paper_session
│  id, strategy_name, status
│
└──► paper_trade           (one session → many trades)
      session_id FK → paper_session


(standalone — no FK relationships)
  ai_pick
  briefing
  news_headline
  angel_token
```

> **Why no FK on symbol_token?**
> candle_history, bias_signal, indicator_snapshot all reference
> instruments by `symbol_token` without a formal FK. Reason:
> Angel One tokens can change (contract rollover), and FK constraints
> would block saves when a token is updated. Denormalized by design.

---

## 3. Existing Tables

### 3.1 candle_history

**Purpose:** Stores OHLCV price bars fetched from Angel One.
One row = one candle bar for one instrument at one timestamp.

```sql
CREATE TABLE candle_history (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    broker        VARCHAR(16)    NOT NULL,          -- always "ANGEL"
    exchange      VARCHAR(8)     NOT NULL,           -- NSE / MCX / NFO / CDS
    symbol_token  VARCHAR(32)    NOT NULL,           -- Angel token e.g. "99926000"
    interval_type VARCHAR(16)    NOT NULL,           -- FIVE_MINUTE / ONE_DAY
    ts            DATETIME(6)    NOT NULL,           -- bar open time (UTC)
    open          DECIMAL(15,4)  NOT NULL,
    high          DECIMAL(15,4)  NOT NULL,
    low           DECIMAL(15,4)  NOT NULL,
    close         DECIMAL(15,4)  NOT NULL,
    volume        BIGINT         NOT NULL,
    saved_at      DATETIME(6)    NOT NULL,           -- when we saved this row

    PRIMARY KEY (id),
    UNIQUE KEY uk_candle (broker, exchange, symbol_token, interval_type, ts),
    KEY idx_lookup  (symbol_token, interval_type, ts),   -- hot read path
    KEY idx_saved_at (saved_at)                          -- retention purge
);
```

**Key decisions:**
- One table for ALL segments (NSE, MCX, NFO futures, CDS) — `exchange` column differentiates
- `UNIQUE` on 5 columns — safe to re-save same candle (dedup at DB level)
- `idx_lookup` covers every range query — always query by token + interval + date
- `idx_saved_at` used only by nightly retention purge
- **NFO options (CE/PE) NOT stored** — too many strikes, see Section 6

**Retention:** 365 days (nightly purge at 03:00 AM)

---

### 3.2 bias_instrument

**Purpose:** Config table — which instruments the app tracks and scans.
Seeded from `application.properties` on first boot, then managed via admin UI.

```sql
CREATE TABLE bias_instrument (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    symbol        VARCHAR(64)    NOT NULL,           -- display name e.g. "Nifty 50"
    symbol_token  VARCHAR(32)    NOT NULL,           -- Angel token
    broker        VARCHAR(16)    NOT NULL,           -- "ANGEL"
    exchange      VARCHAR(8)     NOT NULL,           -- NSE / MCX / NFO
    interval_type VARCHAR(16)    NOT NULL,           -- default intraday interval
    lot_size      INT            NOT NULL,           -- 75 for NIFTY, 1 for equity
    enabled       BIT(1)         NOT NULL,           -- toggle without deleting
    priority      INT            NOT NULL,           -- lower = higher priority
    notes         VARCHAR(255)   DEFAULT NULL,
    created_at    DATETIME(6)    NOT NULL,
    updated_at    DATETIME(6)    NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_bias_instr_token (symbol_token),
    KEY idx_bias_instr_enabled_priority (enabled, priority)
);
```

**Current row count:** ~52 instruments (4 original + 50 seeded via bulk-seed)

**Key decisions:**
- `enabled` flag — disable instrument without deleting (preserves history)
- `priority` — controls display order and warmer execution order
- `lot_size` stored here — used by screener for capital calculation

**Retention:** Permanent (no purge — config data)

---

### 3.3 bias_signal

**Purpose:** Every BUY/SELL signal fired by the detection pipeline.
Includes lifecycle (OPEN → HIT_TARGET / HIT_STOP / EXPIRED / CANCELLED)
and indicator snapshot at the moment of signal.

```sql
CREATE TABLE bias_signal (
    id                BIGINT         NOT NULL AUTO_INCREMENT,

    -- What instrument
    symbol            VARCHAR(64)    NOT NULL,
    symbol_token      VARCHAR(32)    NOT NULL,
    exchange          VARCHAR(8)     NOT NULL,
    interval_type     VARCHAR(16)    NOT NULL,

    -- Signal details
    action            VARCHAR(8)     NOT NULL,       -- BUY / SELL
    source            VARCHAR(16)    NOT NULL,       -- CONSENSUS / AI
    entry             DECIMAL(15,4)  NOT NULL,
    stop              DECIMAL(15,4)  NOT NULL,
    target            DECIMAL(15,4)  NOT NULL,
    original_stop     DECIMAL(15,4)  DEFAULT NULL,   -- stop before trailing
    agreed_strategies VARCHAR(500)   DEFAULT NULL,   -- csv: macd,rsi,...
    agreed_count      INT            NOT NULL,

    -- AI fields (null for CONSENSUS)
    ai_confidence     VARCHAR(8)     DEFAULT NULL,   -- HIGH / MEDIUM / LOW
    ai_rationale      VARCHAR(1500)  DEFAULT NULL,

    -- Indicator snapshot at signal time
    adx               DECIMAL(8,2)   DEFAULT NULL,
    plus_di           DECIMAL(8,2)   DEFAULT NULL,
    minus_di          DECIMAL(8,2)   DEFAULT NULL,
    rsi               DECIMAL(8,2)   DEFAULT NULL,
    macd_line         DECIMAL(12,4)  DEFAULT NULL,
    macd_signal       DECIMAL(12,4)  DEFAULT NULL,
    macd_histogram    DECIMAL(12,4)  DEFAULT NULL,
    atr               DECIMAL(12,4)  DEFAULT NULL,

    -- Lifecycle
    status            VARCHAR(16)    NOT NULL,       -- OPEN / HIT_TARGET / HIT_STOP / EXPIRED / CANCELLED
    created_at        DATETIME(6)    NOT NULL,
    closed_at         DATETIME(6)    DEFAULT NULL,
    closed_price      DECIMAL(15,4)  DEFAULT NULL,
    profit_points     DECIMAL(15,4)  DEFAULT NULL,
    profit_percent    DECIMAL(6,2)   DEFAULT NULL,
    high_water_mark   DECIMAL(15,4)  DEFAULT NULL,   -- trailing stop reference
    trail_updates     INT            NOT NULL,        -- how many times stop tightened
    notes             VARCHAR(500)   DEFAULT NULL,

    PRIMARY KEY (id),
    KEY idx_sig_status  (status),
    KEY idx_sig_created (created_at),
    KEY idx_sig_symbol  (symbol_token, created_at)
);
```

**Key decisions:**
- Indicator values stored AT signal time — lets you analyze why a signal fired
- `high_water_mark` + `trail_updates` — tracks trailing stop activity
- `source` = CONSENSUS (8-strategy vote) or AI (Claude signal)

**Retention:** 365 days

---

### 3.4 trade_journal

**Purpose:** User's manual trade diary. Log every trade you take,
with entry/exit, indicators used, emotional state, and post-trade notes.

```sql
CREATE TABLE trade_journal (
    id                BIGINT         NOT NULL AUTO_INCREMENT,

    -- PLANNED: user_id BIGINT NOT NULL (FK → app_user)

    -- What trade
    symbol            VARCHAR(64)    NOT NULL,
    segment           VARCHAR(16)    NOT NULL,       -- NSE / BSE / MCX / NFO
    trade_type        VARCHAR(8)     NOT NULL,       -- BUY / SELL
    timeframe         VARCHAR(8)     NOT NULL,       -- 5m / 15m / 1h / 1d

    -- Entry
    entry_price       DECIMAL(15,4)  NOT NULL,
    entry_date_time   DATETIME(6)    NOT NULL,
    stop_loss         DECIMAL(15,4)  DEFAULT NULL,
    target            DECIMAL(15,4)  DEFAULT NULL,
    quantity          INT            NOT NULL,
    lot_size          INT            DEFAULT NULL,

    -- Exit (optional — null if trade still open)
    exit_price        DECIMAL(15,4)  DEFAULT NULL,
    exit_date_time    DATETIME(6)    DEFAULT NULL,

    -- Setup analysis
    strategy          VARCHAR(128)   DEFAULT NULL,
    indicators_used   VARCHAR(500)   DEFAULT NULL,   -- csv: "RSI,MACD,ADX"
    market_condition  VARCHAR(32)    DEFAULT NULL,   -- Trending / Sideways / Volatile
    emotional_state   VARCHAR(32)    DEFAULT NULL,   -- Calm / FOMO / Anxious

    -- Outcome (auto-computed on save)
    outcome           VARCHAR(16)    NOT NULL,       -- WIN / LOSS / BREAKEVEN / OPEN
    pnl               DECIMAL(15,4)  DEFAULT NULL,
    pnl_percent       DECIMAL(8,2)   DEFAULT NULL,

    -- Post-trade review
    reason_for_entry  VARCHAR(1000)  DEFAULT NULL,
    notes             VARCHAR(2000)  DEFAULT NULL,

    created_at        DATETIME(6)    NOT NULL,
    updated_at        DATETIME(6)    DEFAULT NULL,

    PRIMARY KEY (id),
    KEY idx_tj_entry_dt (entry_date_time),
    KEY idx_tj_outcome  (outcome),
    KEY idx_tj_symbol   (symbol)
);
```

**Pending change:** Add `user_id` column for multi-user support (see Section 5.1)

**Retention:** Permanent (user's personal data, never auto-delete)

---

### 3.5 app_user

**Purpose:** User accounts. Managed by Spring Security.
Includes lockout, 2FA (TOTP), and session tracking.

```sql
CREATE TABLE app_user (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    username              VARCHAR(64)  NOT NULL,
    email                 VARCHAR(120) DEFAULT NULL,
    password              VARCHAR(100) NOT NULL,    -- BCrypt hash
    role                  VARCHAR(32)  NOT NULL,    -- ROLE_USER / ROLE_ADMIN
    enabled               BIT(1)       NOT NULL,
    email_verified        BIT(1)       NOT NULL,

    -- Security / lockout
    failed_login_attempts INT          NOT NULL,
    locked_until          DATETIME(6)  DEFAULT NULL,

    -- 2FA
    totp_enabled          BIT(1)       NOT NULL,
    totp_secret           VARCHAR(64)  DEFAULT NULL,  -- encrypted TOTP key
    totp_enabled_at       DATETIME(6)  DEFAULT NULL,

    -- Tracking
    last_login_at         DATETIME(6)  DEFAULT NULL,
    password_changed_at   DATETIME(6)  DEFAULT NULL,
    created_at            DATETIME(6)  NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    KEY idx_user_email   (email),
    KEY idx_user_enabled (enabled)
);
```

**Retention:** Permanent

---

### 3.6 user_login_audit

**Purpose:** Every login attempt (success + failure) logged for security.
Used for lockout logic, IP throttling, and security review.

```sql
CREATE TABLE user_login_audit (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    username        VARCHAR(64)  NOT NULL,
    user_id         BIGINT       DEFAULT NULL,       -- null if user not found
    ip_address      VARCHAR(45)  NOT NULL,           -- supports IPv6
    user_agent      VARCHAR(255) DEFAULT NULL,
    success         BIT(1)       NOT NULL,
    failure_reason  VARCHAR(32)  DEFAULT NULL,       -- BAD_PASSWORD / LOCKED / 2FA_FAIL
    created_at      DATETIME(6)  NOT NULL,

    PRIMARY KEY (id),
    KEY idx_audit_user_time (username, created_at DESC),
    KEY idx_audit_ip_time   (ip_address, created_at DESC),
    KEY idx_audit_time      (created_at DESC),
    KEY idx_audit_success   (success)
);
```

**Current rows:** 5,734 (active log)

**Retention:** 90 days (auto-purge via `securityAuditProperties.pruneCron`)

---

### 3.7 paper_session

**Purpose:** One backtest/paper trading run. Stores the summary result
of replaying historical candles through a strategy.

```sql
CREATE TABLE paper_session (
    id             VARCHAR(36)  NOT NULL,     -- UUID
    strategy_name  VARCHAR(64)  NOT NULL,     -- "ensemble" / "macd-crossover"
    source_name    VARCHAR(256) NOT NULL,     -- "angel-historical-replay:NIFTY:..."
    status         ENUM('RUNNING','COMPLETED','STOPPED') NOT NULL,
    started_at     DATETIME(6)  NOT NULL,
    ended_at       DATETIME(6)  DEFAULT NULL,

    -- Summary stats
    candle_count   INT          NOT NULL,
    total_trades   INT          NOT NULL,
    winners        INT          NOT NULL,
    losers         INT          NOT NULL,
    net_pnl        DECIMAL(20,4) NOT NULL,

    PRIMARY KEY (id),
    KEY idx_session_status   (status),
    KEY idx_session_strategy (strategy_name),
    KEY idx_session_started  (started_at)
);
```

**Retention:** 180 days (backtest history, can be re-run)

---

### 3.8 paper_trade

**Purpose:** Individual trades within a paper session.
One session → many trades.

```sql
CREATE TABLE paper_trade (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    session_id   VARCHAR(36)  NOT NULL,      -- FK → paper_session.id
    direction    ENUM('ENTER_LONG','ENTER_SHORT','EXIT','HOLD') NOT NULL,
    entry_time   DATETIME(6)  NOT NULL,
    entry_price  DECIMAL(20,4) NOT NULL,
    exit_time    DATETIME(6)  NOT NULL,
    exit_price   DECIMAL(20,4) NOT NULL,
    exit_reason  ENUM('STOP','TARGET','SIGNAL_EXIT','END_OF_SERIES') NOT NULL,
    stop_loss    DECIMAL(20,4) DEFAULT NULL,
    target       DECIMAL(20,4) DEFAULT NULL,
    pnl          DECIMAL(20,4) NOT NULL,
    rationale    VARCHAR(512) DEFAULT NULL,  -- why strategy fired

    PRIMARY KEY (id),
    KEY idx_trade_session   (session_id),
    KEY idx_trade_entry     (entry_time),
    KEY idx_trade_direction (direction)
);
```

**Retention:** Same as paper_session (180 days)

---

### 3.9 ai_pick

**Purpose:** Medium-term stock picks generated by Claude AI.
Tracks outcome (did it hit target or stop?).

```sql
CREATE TABLE ai_pick (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    symbol         VARCHAR(64)   NOT NULL,
    symbol_token   VARCHAR(32)   NOT NULL,
    exchange       VARCHAR(8)    NOT NULL,
    action         VARCHAR(8)    NOT NULL,      -- BUY / HOLD / AVOID
    confidence     VARCHAR(8)    DEFAULT NULL,  -- HIGH / MEDIUM / LOW
    entry_price    DECIMAL(15,4) DEFAULT NULL,
    target_price   DECIMAL(15,4) DEFAULT NULL,
    stop_loss      DECIMAL(15,4) DEFAULT NULL,
    horizon_days   INT           DEFAULT NULL,  -- expected hold period
    rationale      VARCHAR(1500) DEFAULT NULL,  -- Claude's reasoning
    model          VARCHAR(32)   DEFAULT NULL,  -- Claude model version

    -- Lifecycle
    status         VARCHAR(16)   NOT NULL,      -- OPEN / HIT_TARGET / HIT_STOP / EXPIRED / CANCELLED
    created_at     DATETIME(6)   NOT NULL,
    closed_at      DATETIME(6)   DEFAULT NULL,
    closed_price   DECIMAL(15,4) DEFAULT NULL,
    return_percent DECIMAL(8,2)  DEFAULT NULL,

    PRIMARY KEY (id),
    KEY idx_aip_status  (status),
    KEY idx_aip_created (created_at),
    KEY idx_aip_symbol  (symbol_token, created_at)
);
```

**Retention:** 365 days

---

### 3.10 briefing

**Purpose:** Daily pre-market briefing generated by Claude.
One row per trading day.

```sql
CREATE TABLE briefing (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    for_date              DATE         NOT NULL,     -- which trading day
    bias                  VARCHAR(16)  NOT NULL,     -- BULLISH / BEARISH / NEUTRAL
    summary               VARCHAR(4000) NOT NULL,    -- Claude's briefing text
    key_drivers           VARCHAR(1500) DEFAULT NULL,
    markets_snapshot_json TEXT         DEFAULT NULL, -- global markets JSON
    news_titles_json      TEXT         DEFAULT NULL, -- top news JSON
    model                 VARCHAR(40)  DEFAULT NULL,  -- Claude model used
    generated_at          DATETIME(6)  NOT NULL,
    email_sent            BIT(1)       NOT NULL,
    telegram_sent         BIT(1)       NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_br_date (for_date),
    KEY idx_br_date (for_date)
);
```

**Retention:** 90 days (historical briefings less useful after time)

---

### 3.11 news_headline

**Purpose:** News headlines fetched from external feeds.
Tagged with sentiment and sector by AI.

```sql
CREATE TABLE news_headline (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    title            VARCHAR(500) NOT NULL,
    url              VARCHAR(500) NOT NULL,
    source_name      VARCHAR(80)  NOT NULL,
    source_category  VARCHAR(16)  NOT NULL,  -- MARKET / SECTOR / MACRO
    published_at     DATETIME(6)  DEFAULT NULL,
    fetched_at       DATETIME(6)  NOT NULL,

    -- AI tagging
    ai_tagged        BIT(1)       NOT NULL,
    sentiment        VARCHAR(12)  DEFAULT NULL,  -- POSITIVE / NEGATIVE / NEUTRAL
    sector           VARCHAR(32)  DEFAULT NULL,  -- BANKING / IT / METALS
    rationale        VARCHAR(500) DEFAULT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_nh_url    (url),
    KEY idx_nh_published    (published_at),
    KEY idx_nh_sentiment    (sentiment),
    KEY idx_nh_source       (source_name)
);
```

**Current rows:** 482

**Retention:** 30 days (news becomes stale quickly)

---

### 3.12 angel_token

**Purpose:** Persists Angel One JWT + refresh tokens across app restarts.
One row per Angel One client account.

```sql
CREATE TABLE angel_token (
    client_code    VARCHAR(32)   NOT NULL,    -- Angel One client ID
    jwt_token      VARCHAR(2048) NOT NULL,    -- short-lived JWT
    refresh_token  VARCHAR(2048) DEFAULT NULL,
    feed_token     VARCHAR(2048) DEFAULT NULL, -- for WebSocket auth
    expires_at     DATETIME(6)   NOT NULL,
    saved_at       DATETIME(6)   NOT NULL,

    PRIMARY KEY (client_code)
);
```

**Retention:** Permanent (auto-overwritten on each login)

---

## 4. New Tables to Add

### 4.1 indicator_snapshot

**Purpose:** Save computed indicator values (ADX, RSI, EMA, ATR, MACD)
alongside each candle. Avoids recomputing on every screener scan.

**Why separate table (not columns in candle_history):**
```
✅ No NULLs — only rows where warmup is complete (no EMA200 for first 200 bars)
✅ Easy to add new indicators — just ALTER this table, not candle_history
✅ Easy to recompute — DELETE + INSERT, candle_history untouched
✅ Different retention — indicators: 90 days, candles: 365 days
✅ Raw data stays pure — OHLCV in candle_history, derived in indicator_snapshot
```

```sql
CREATE TABLE indicator_snapshot (
    id            BIGINT        NOT NULL AUTO_INCREMENT,

    -- What instrument + timeframe (matches candle_history)
    symbol_token  VARCHAR(32)   NOT NULL,
    interval_type VARCHAR(16)   NOT NULL,     -- FIVE_MINUTE / ONE_DAY

    -- When (same ts as the candle this was computed from)
    ts            DATETIME(6)   NOT NULL,

    -- TREND STRENGTH
    adx           DECIMAL(8,2)  DEFAULT NULL, -- 0-100, >25 = trending
    plus_di       DECIMAL(8,2)  DEFAULT NULL, -- +DI line
    minus_di      DECIMAL(8,2)  DEFAULT NULL, -- -DI line

    -- MOMENTUM
    rsi           DECIMAL(8,2)  DEFAULT NULL, -- 0-100

    -- TREND DIRECTION (EMAs)
    ema_9         DECIMAL(15,4) DEFAULT NULL,
    ema_20        DECIMAL(15,4) DEFAULT NULL,
    ema_50        DECIMAL(15,4) DEFAULT NULL,
    ema_200       DECIMAL(15,4) DEFAULT NULL,

    -- VOLATILITY
    atr           DECIMAL(12,4) DEFAULT NULL, -- average true range
    bb_upper      DECIMAL(15,4) DEFAULT NULL, -- Bollinger band upper
    bb_lower      DECIMAL(15,4) DEFAULT NULL, -- Bollinger band lower

    -- MOMENTUM / TREND
    macd_line     DECIMAL(12,4) DEFAULT NULL,
    macd_signal   DECIMAL(12,4) DEFAULT NULL,
    macd_hist     DECIMAL(12,4) DEFAULT NULL,

    -- Supertrend
    supertrend    DECIMAL(15,4) DEFAULT NULL,
    st_bullish    BIT(1)        DEFAULT NULL, -- 1 = price above supertrend

    -- META
    computed_at   DATETIME(6)   NOT NULL,     -- when we computed this

    PRIMARY KEY (id),
    UNIQUE KEY uk_snap (symbol_token, interval_type, ts),
    KEY idx_snap_lookup (symbol_token, interval_type, ts)
);
```

**How it's populated:**
```
CandleHistoryJob (post-market, 15:40 PM)
  or BiasWarmer (every 5 min during market hours)
        │
        ▼
  fetch last 260 candles from candle_history
        │
        ▼
  compute all indicators on the full list
        │
        ▼
  take only the LAST computed values (latest bar)
        │
        ▼
  INSERT INTO indicator_snapshot
  ON DUPLICATE KEY UPDATE ...  (upsert)
```

**How screener uses it:**
```sql
-- Fast screener query — no indicator computation needed
SELECT
    i.symbol_token,
    b.symbol,
    b.exchange,
    b.lot_size,
    i.adx,
    i.rsi,
    i.ema_50,
    i.ema_200,
    i.atr,
    i.ts
FROM indicator_snapshot i
JOIN bias_instrument b ON b.symbol_token = i.symbol_token
WHERE i.interval_type = 'ONE_DAY'
  AND i.ts = (
      SELECT MAX(ts) FROM indicator_snapshot
      WHERE symbol_token = i.symbol_token
        AND interval_type = 'ONE_DAY'
  )
  AND b.enabled = 1
ORDER BY i.adx DESC;
```

**Volume estimate:**
```
50 instruments × 1D × 250 days  =   12,500 rows  (screener use)
50 instruments × 5m × 250 days  =  937,500 rows  (intraday use)
─────────────────────────────────────────────────
Total (1 year)                   =  ~950,000 rows
After 90-day retention           =  ~300,000 rows
```

**Retention:** 90 days (recomputable from candle_history anytime)

---

### 4.2 user_watchlist

**Purpose:** Each of the 100 users can have their own personal
watchlist of stocks — separate from the global bias_instrument list.

```sql
CREATE TABLE user_watchlist (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,    -- FK → app_user.id
    symbol       VARCHAR(64)  NOT NULL,    -- display name
    symbol_token VARCHAR(32)  NOT NULL,    -- Angel token
    exchange     VARCHAR(8)   NOT NULL,    -- NSE / MCX / NFO
    lot_size     INT          DEFAULT 1,
    notes        VARCHAR(255) DEFAULT NULL,
    added_at     DATETIME(6)  NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_user_token (user_id, symbol_token),    -- no duplicates per user
    KEY idx_wl_user (user_id),
    CONSTRAINT fk_wl_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);
```

**Retention:** Permanent (user's personal config)

---

## 5. Tables to Modify

### 5.1 trade_journal — add user_id

Currently `trade_journal` has no `user_id`. For 100 users, each user
must only see their own trades.

```sql
-- Migration
ALTER TABLE trade_journal
    ADD COLUMN user_id BIGINT NOT NULL DEFAULT 1 AFTER id,
    ADD CONSTRAINT fk_tj_user FOREIGN KEY (user_id)
        REFERENCES app_user(id) ON DELETE CASCADE,
    ADD KEY idx_tj_user (user_id);

-- Also update the existing index to include user_id
-- for efficient per-user queries
ALTER TABLE trade_journal
    ADD KEY idx_tj_user_date (user_id, entry_date_time DESC);
```

**After migration — typical query:**
```sql
SELECT * FROM trade_journal
WHERE user_id = ?
ORDER BY entry_date_time DESC;
```

---

## 6. What NOT to Store

```
❌ NFO options candles (CE / PE strikes)
   Reason: 200+ strikes × 2 (CE+PE) × 52 weekly expiries
           = billions of rows per year
           Options expire weekly — data useless after expiry
   Solution: fetch from Angel One live only when needed, never cache to MySQL

❌ Raw tick data (LTP every second from WebSocket)
   Reason: 50 instruments × 1 tick/second × 6.25 market hours
           = 1.125 million rows per day
           Aggregated into OHLCV bars by CandleAggregator (in memory)
   Solution: only store the aggregated candles, never raw ticks

❌ Redis data as permanent storage
   Reason: Redis is cache only — data can be reconstructed
           Never use Redis as primary data store
```

---

## 7. Volume Estimates

```
Table                  Rows today   Growth/day    1 year        Size (1yr)
────────────────────────────────────────────────────────────────────────────
candle_history         134,436      ~5,000        1.38M rows    ~300 MB
bias_signal            248          ~50           18,250        ~5 MB
indicator_snapshot     0 (new)      ~4,000        950,000       ~190 MB
trade_journal          2            ~5/user×100   182,500       ~50 MB
paper_session          0            ~10           3,650         tiny
paper_trade            0            ~100          36,500        tiny
ai_pick                0            ~20/week      1,040         tiny
user_login_audit       5,734        ~100          36,500        ~10 MB
briefing               0            1             365           tiny
news_headline          482          ~50           18,250        ~20 MB
app_user               2            rare          ~100 users    tiny
bias_instrument        52           rare          ~100 max      tiny
angel_token            1            rare          1 (overwrite) tiny
────────────────────────────────────────────────────────────────────────────
TOTAL (est.)                                      ~2.6M rows    ~575 MB
```

Very manageable for MySQL. No sharding needed at this scale.

---

## 8. Retention Policy

```
Table                  Retention    Purge mechanism
────────────────────────────────────────────────────────────────────
candle_history         365 days     MysqlRetentionScheduler (03:00 AM daily)
bias_signal            365 days     manual or future cron
indicator_snapshot     90 days      future cron (recomputable)
trade_journal          PERMANENT    never auto-delete (user data)
paper_session          180 days     future cron
paper_trade            180 days     cascade with paper_session
ai_pick                365 days     future cron
user_login_audit       90 days      LoginAuditService cron
briefing               90 days      BriefingService cleanup (00:20 AM)
news_headline          30 days      NewsHeadlineService cleanup (00:10 AM)
app_user               PERMANENT    admin action only
bias_instrument        PERMANENT    admin action only
angel_token            PERMANENT    overwritten on each login
user_watchlist         PERMANENT    user action only
```

---

## 9. Index Design

### Design principles
```
1. Every WHERE clause column needs an index
2. Every ORDER BY column needs an index
3. Composite index: put most selective column first
4. Avoid over-indexing writes (each index slows INSERT/UPDATE)
```

### Index map

```
candle_history:
  uk_candle (broker,exchange,symbol_token,interval_type,ts)  → dedup on INSERT
  idx_lookup (symbol_token,interval_type,ts)                 → hot read path
  idx_saved_at (saved_at)                                    → retention purge

bias_signal:
  idx_sig_status (status)                                    → filter OPEN signals
  idx_sig_created (created_at)                               → time-range feed
  idx_sig_symbol (symbol_token,created_at)                   → per-instrument feed

bias_instrument:
  uk_bias_instr_token (symbol_token)                         → lookup by token
  idx_bias_instr_enabled_priority (enabled,priority)         → list enabled, sorted

trade_journal:
  idx_tj_user_date (user_id,entry_date_time DESC)            → per-user history
  idx_tj_outcome (outcome)                                   → filter by WIN/LOSS
  idx_tj_symbol (symbol)                                     → search by stock

indicator_snapshot:
  uk_snap (symbol_token,interval_type,ts)                    → upsert key
  idx_snap_lookup (symbol_token,interval_type,ts)            → screener query

user_watchlist:
  uk_user_token (user_id,symbol_token)                       → no duplicate watchlist
  idx_wl_user (user_id)                                      → per-user list

user_login_audit:
  idx_audit_user_time (username,created_at DESC)             → lockout check
  idx_audit_ip_time (ip_address,created_at DESC)             → IP throttle
```

---

## 10. Connection Pool Config

For 100 users (20-30 concurrent at peak):

```properties
# application.properties

# Pool size: 20 is right for 100 users
# Formula: (core count × 2) + disk spindles
# Our DB server: ~4 cores → 4×2+1 = 9... but 20 gives headroom
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5

# How long to wait for a connection before failing
spring.datasource.hikari.connection-timeout=30000

# How long a connection can sit idle before being released
spring.datasource.hikari.idle-timeout=600000

# Max lifetime of any connection (prevent stale connections)
spring.datasource.hikari.max-lifetime=1800000

# Validate connection before using (prevents "connection closed" errors)
spring.datasource.hikari.connection-test-query=SELECT 1
```

---

## 11. Redis — What Goes There

Redis is cache only. Never primary storage.

```
Key pattern                              TTL       What it stores
────────────────────────────────────────────────────────────────────────
candles:ANGEL:NSE:{token}:{iv}:{f}:{t}  5 min     candle list (JSON)
screener:results                         15 min    ScreenerRow[] (JSON)
SPRING_SECURITY_SESSION:{id}            30 min    user session
```

**Memory estimate for 100 users:**
```
Candle cache:   50 instruments × 5 ranges × 5KB avg  = ~1.25 MB
Screener:       1 result set × 50 rows × 500 bytes   = ~25 KB
Sessions:       30 concurrent × 2 KB                  = ~60 KB
────────────────────────────────────────────────────────────────
Total Redis:    ~2 MB  (very small — 64 MB Redis container is fine)
```

---

## 12. Future — When to Add TimescaleDB

```
Stay on MySQL when:           Switch to TimescaleDB when:
────────────────────          ────────────────────────────
< 500 users                   > 500 users
candle_history < 5M rows      candle_history > 5M rows
Queries < 50ms                Range queries > 200ms
Single machine                Multiple machines

TimescaleDB benefits for candle data:
  - Automatic time partitioning (chunks by month)
  - 10-100× faster time-range queries at scale
  - 90%+ compression on OHLCV data
  - Continuous aggregates (auto-rollup 5m → 1h → 1D)
  - Same SQL as PostgreSQL (easy migration)
  - Drop-in replacement — change JDBC URL + driver

Migration path when needed:
  1. Run TimescaleDB alongside MySQL
  2. Backfill candle_history into TimescaleDB
  3. Switch CandleRepository to new datasource
  4. Keep MySQL for everything else (signals, users, journal)
```

---

## Summary — What to Do Next

```
Priority  Action                           Why
────────────────────────────────────────────────────────────────
HIGH      Create indicator_snapshot table  Screener loads instantly
          + populate via CandleHistoryJob  No recompute every scan

HIGH      Add user_id to trade_journal     Multi-user support
          + create user_watchlist          Personal watchlists

MEDIUM    Tune HikariCP pool size to 20   Ready for 100 users

MEDIUM    Add retention cron for:         Keep DB size bounded
          - indicator_snapshot (90 days)
          - paper_session (180 days)
          - bias_signal (365 days)

LOW       Consider TimescaleDB             Only if candle_history
          for candle storage               hits 5M+ rows
```
