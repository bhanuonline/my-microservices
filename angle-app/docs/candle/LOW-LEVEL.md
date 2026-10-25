# Candle History — Low-Level Technical Document

> **Audience:** Developers who need to understand the implementation —
> class internals, method signatures, algorithms, config properties,
> code examples, and troubleshooting.
>
> Read [HIGH-LEVEL.md](./HIGH-LEVEL.md) first if you haven't already.

---

## Table of Contents

1. [Candle Data Model](#1-candle-data-model)
2. [MarketDataService — The Entry Point](#2-marketdataservice--the-entry-point)
3. [CandleCacheKey — Cache Identity](#3-candlecachekey--cache-identity)
4. [L1 — CaffeineCandleCache](#4-l1--caffeinecandlecache)
5. [L2 — RedisCandleCache](#5-l2--rediscandlecache)
6. [L3 — MysqlCandleStore](#6-l3--mysqlcandlestore)
7. [LayeredCandleCacheWithStore — Gap-Fill Algorithm](#7-layeredcandlecachewithstore--gap-fill-algorithm)
8. [Async Save — Thread Pool Internals](#8-async-save--thread-pool-internals)
9. [CandleEntity — JPA Mapping](#9-candleentity--jpa-mapping)
10. [CandleRepository — Queries](#10-candlerepository--queries)
11. [Configuration Reference](#11-configuration-reference)
12. [How to Add a New Consumer](#12-how-to-add-a-new-consumer)
13. [How to Switch Cache Provider](#13-how-to-switch-cache-provider)
14. [Troubleshooting](#14-troubleshooting)
15. [Useful Admin Endpoints](#15-useful-admin-endpoints)

---

## 1. Candle Data Model

### 1.1 `Candle` record (in-memory)

```java
// package: com.angle.trading.broker.model
public record Candle(
    Instant    timestamp,  // bar open time (UTC)
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    long       volume
) {}
```

**Why BigDecimal for prices?**
Floating-point (double) accumulates rounding errors when used in indicator
math (moving averages, EMA chains). For example, summing 200 doubles for
an EMA can drift by ~0.001%. BigDecimal is exact. Trade-off: slower math,
but correctness matters more for trading signals.

**Why Instant for timestamp?**
Instant is timezone-neutral (UTC). Candle timestamps from Angel arrive in
IST — AngelClient converts them to Instant at parse time. All internal
code works in UTC; only UI/display converts back to IST.

---

### 1.2 `CandleEntity` (persisted)

```java
// package: com.angle.trading.persistence
// Table: candle_history
@Entity
@Table(
    name = "candle_history",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"broker","exchange","symbol_token","interval_type","ts"}
    ),
    indexes = {
        @Index(columnList = "symbol_token,interval_type,ts"),  // hot read path
        @Index(columnList = "saved_at")                        // retention purge
    }
)
public class CandleEntity {
    Long       id;
    String     broker;        // "ANGEL"
    String     exchange;      // "NSE" / "MCX" / "NFO"
    String     symbolToken;   // Angel token e.g. "99926000"
    String     intervalType;  // "FIVE_MINUTE" / "ONE_DAY"
    Instant    ts;            // candle open timestamp (UTC)
    BigDecimal open, high, low, close;
    long       volume;
    Instant    savedAt;       // when we inserted this row
}
```

**UNIQUE constraint** on 5 columns prevents duplicates. If Angel returns a
candle we already have, the INSERT fails silently (handled in `flushSafely()`).

**Index on `(symbol_token, interval_type, ts)`** — this is the hot read path.
Every `findRange()` call uses this index. Without it, range queries would
be full table scans.

**Index on `saved_at`** — used only by the retention purge cron.

---

### 1.3 Converting between Candle ↔ CandleEntity

```java
// CandleEntity → Candle (read path, inside MysqlCandleStore)
new Candle(entity.getTs(), entity.getOpen(), entity.getHigh(),
           entity.getLow(), entity.getClose(), entity.getVolume())

// Candle → CandleEntity (write path)
new CandleEntity(broker, exchange, symbolToken, intervalType,
                 candle.timestamp(), candle.open(), candle.high(),
                 candle.low(), candle.close(), candle.volume())
// savedAt is set automatically in the CandleEntity constructor
```

---

## 2. MarketDataService — The Entry Point

```java
// package: com.angle.trading.marketdata
@Service
public class MarketDataService {

    public List<Candle> getCandles(
        String   brokerName,    // "ANGEL" / "UPSTOX" / "KITE"
        Exchange exchange,      // NSE / BSE / NFO / MCX / CDS
        String   symbolToken,   // Angel's internal ID e.g. "99926000"
        Interval interval,      // ONE_MINUTE / FIVE_MINUTE / ONE_DAY / ...
        LocalDate from,         // start date (inclusive)
        LocalDate to            // end date (inclusive)
    )
}
```

**What it does internally:**

```java
// 1. Find the right broker client
BrokerClient broker = brokersByName.get(brokerName.toUpperCase());

// 2. Build a cache key from all 6 parameters
CandleCacheKey key = new CandleCacheKey(
    brokerName, exchange.name(), symbolToken, interval.name(), from, to);

// 3. Delegate to cache — which calls broker only on miss
return candleCache.get(key,
    (loaderFrom, loaderTo) -> broker.getCandles(exchange, symbolToken, interval, loaderFrom, loaderTo));
```

The `RangeLoader` lambda (the second argument) is only called when the cache
cannot serve the request. The `loaderFrom` / `loaderTo` dates may differ from
`from` / `to` when gap-fill narrows the fetch window.

---

### 2.1 How to Call It

```java
@Service
@RequiredArgsConstructor
public class MyNewService {

    private final MarketDataService marketDataService;

    public void doSomething() {
        List<Candle> candles = marketDataService.getCandles(
            "ANGEL",
            Exchange.NSE,
            "99926000",         // NIFTY 50 token
            Interval.FIVE_MINUTE,
            LocalDate.now().minusDays(5),
            LocalDate.now()
        );
        // candles is sorted oldest → newest
        // candles.get(candles.size() - 1) is the most recent bar
    }
}
```

**Result is always sorted ascending by timestamp** (oldest first). This matches
what indicator libraries expect: `candles.get(i)` is bar i, `candles.get(0)`
is the oldest bar.

---

## 3. CandleCacheKey — Cache Identity

```java
// package: com.angle.trading.cache
public record CandleCacheKey(
    String    broker,      // "ANGEL"
    String    exchange,    // "NSE"
    String    symbolToken, // "99926000"
    String    interval,    // "FIVE_MINUTE"
    LocalDate from,
    LocalDate to
) {}
```

Two requests for the same instrument but different date ranges → **different keys**.
This means each unique (token, interval, from, to) combination is cached separately.

**Redis key format:**
```
candles:ANGEL:NSE:99926000:FIVE_MINUTE:2026-10-20:2026-10-25
         ▲     ▲  ▲         ▲            ▲           ▲
         prefix broker exch  token        interval    from  to
```

The prefix `candles` is configurable: `bias.cache.redis.key-prefix=candles`

---

## 4. L1 — CaffeineCandleCache

```java
// package: com.angle.trading.cache.impl
public class CaffeineCandleCache implements CandleCache {

    // Caffeine config:
    //   maximumSize = bias.cache.max-size  (default: 500)
    //   expireAfterWrite = bias.cache.ttl-seconds  (default: 60)
    //   recordStats = true  (for hit-rate logging)

    private final Cache<CandleCacheKey, List<Candle>> cache;
}
```

**When a key expires (60s TTL):**
The entry is evicted. Next request goes to L2 (Redis). If Redis still has it
(5 min TTL), L1 is re-populated without hitting L3 or Angel.

**maximumSize = 500:**
If 501 different keys are requested, Caffeine evicts the least-recently-used
entry. 500 entries covers ~10 instruments × 5 date ranges × 10 intervals.
Increase if you add more instruments.

**Thread safety:** Caffeine's internal map is thread-safe. Multiple threads
can call `get()` concurrently — only one thread calls the loader on a miss.

---

## 5. L2 — RedisCandleCache

```java
// package: com.angle.trading.cache.impl
public class RedisCandleCache implements CandleCache {

    private final RedisTemplate<String, List<Candle>> template;
    // TTL: bias.cache.redis.ttl-seconds (default: 300 = 5 minutes)
}
```

**Serialization:**
Candles are serialized to JSON using Jackson with the `JavaTimeModule`
(handles `Instant` and `LocalDate`). Stored as a JSON array in Redis.

**Redis key structure:**
```
SET candles:ANGEL:NSE:99926000:FIVE_MINUTE:2026-10-20:2026-10-25 "[...]" EX 300
```

**What `invalidateByToken()` does:**
Scans Redis for all keys matching `candles:*:{token}:*` and deletes them.
Used by the admin endpoint when manually refreshing an instrument.

**Why TTL = 5 min?**
Angel One updates candles every minute during market hours. A 5-minute TTL
means at most 5-minute-stale data in Redis, which is acceptable for dashboard
and screener pages. Signal detection always uses the latest via L1 (60s TTL).

---

## 6. L3 — MysqlCandleStore

```java
// package: com.angle.trading.cache.impl
public class MysqlCandleStore implements CandleStore {

    private final CandleRepository repo;
    private final ExecutorService  writer;   // async write pool
    private final int              batchSize; // default 500
}
```

### 6.1 Read path — `findRange()`

```java
public List<Candle> findRange(String broker, String exchange,
        String symbolToken, String intervalType,
        LocalDate from, LocalDate to) {

    // Convert dates to Instants (start-of-day in system timezone)
    Instant fromTs = from.atStartOfDay(ZoneId.systemDefault()).toInstant();
    Instant toTs   = to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

    // JPA range query — uses idx_lookup index
    List<CandleEntity> rows = repo.findRange(
            broker, exchange, symbolToken, intervalType, fromTs, toTs);

    // Convert entities to Candle records
    return rows.stream()
               .map(e -> new Candle(e.getTs(), ...))
               .toList();
}
```

Typical performance: **5–15ms** for a 250-candle range query (indexed).

### 6.2 Write path — `saveAsync()`

```java
public CompletableFuture<Integer> saveAsync(String broker, String exchange,
        String symbolToken, String intervalType, List<Candle> candles) {

    return CompletableFuture.supplyAsync(
            () -> saveBlocking(broker, exchange, symbolToken, intervalType, candles),
            writer  // 2-thread pool named "candle-store-writer"
    );
}
```

The `CompletableFuture` is returned to the caller but **nobody waits on it**.
It's fire-and-forget. If it fails (e.g. DB down), the exception is logged
but not propagated to the user.

### 6.3 Batch write with duplicate handling — `flushSafely()`

```java
private int flushSafely(List<CandleEntity> batch) {
    try {
        repo.saveAll(batch);   // attempt bulk INSERT
        return batch.size();
    } catch (Exception bulkErr) {
        // Bulk failed — likely a duplicate in the batch.
        // Retry one-by-one so the good ones still get saved.
        int ok = 0;
        for (CandleEntity e : batch) {
            try {
                repo.save(e);
                ok++;
            } catch (Exception ignored) {
                // Duplicate key — already exists, skip silently.
            }
        }
        return ok;
    }
}
```

**Why retry one-by-one?**
`saveAll()` is a single transaction. If one row violates the UNIQUE constraint,
the entire batch rolls back. By catching and retrying individually, all
non-duplicate rows are still saved. Duplicates are silently skipped.

---

## 7. LayeredCandleCacheWithStore — Gap-Fill Algorithm

This is the most important class. It orchestrates L1 → L2 → L3 → Angel with
smart gap-fill.

```java
// package: com.angle.trading.cache.impl
public class LayeredCandleCacheWithStore implements CandleCache {

    private final CandleCache l1;   // Caffeine or Noop
    private final CandleCache l2;   // Redis or Noop
    private final CandleStore l3;   // MySQL
}
```

### 7.1 get() — main entry

```java
public List<Candle> get(CandleCacheKey key, RangeLoader loader) {
    return l1.get(key, (from, to) ->       // L1 miss → try L2
            l2.get(key, (f2, t2) ->        // L2 miss → gap-fill from L3
                    loadWithGapFill(key, loader)));
}
```

L1 and L2 get the full key. The inner lambda `loadWithGapFill` is only called
when both L1 and L2 miss.

### 7.2 Gap-fill algorithm — `loadWithGapFill()`

```
Step 1: Query MySQL for the requested date range
        fromDb = l3.findRange(broker, exchange, token, interval, from, to)

Step 2: Check what's the NEWEST candle we have (across ALL dates)
        latest = l3.latestTimestamp(broker, exchange, token, interval)

Step 3: Calculate request end as an Instant
        requestEnd = to.plusDays(1).atStartOfDay().toInstant()

Then branch on 3 cases:
```

**Case A — MySQL is completely empty:**
```
latest = empty
→ fetch ALL from Angel (full range: from → to)
→ saveAsync() all rows to MySQL
→ return Angel rows
```

**Case B — MySQL covers the full request:**
```
latest >= requestEnd
→ MySQL has everything we need
→ return fromDb directly
→ NO Angel call
```

**Case C — MySQL has partial data (the common case after day 1):**
```
latest < requestEnd  AND  latest is not empty
→ gapFrom = latest date (start fetching from there)
→ fetch ONLY the delta: gapFrom → to
→ saveAsync() delta rows to MySQL
→ merge: fromDb + delta (by timestamp, dedup, sort)
→ return merged result
```

### 7.3 Merge logic

```java
private static List<Candle> mergeByTimestamp(List<Candle> older, List<Candle> newer) {
    Map<Instant, Candle> byTs = new HashMap<>();
    for (Candle c : older) byTs.put(c.timestamp(), c);
    for (Candle c : newer) byTs.put(c.timestamp(), c);  // newer overwrites on collision
    List<Candle> merged = new ArrayList<>(byTs.values());
    merged.sort((a, b) -> a.timestamp().compareTo(b.timestamp()));
    return merged;
}
```

**Why newer overwrites?** Angel One is the source of truth. If MySQL has a
partially-formed candle (e.g. the bar closed early), Angel's version wins.

---

## 8. Async Save — Thread Pool Internals

```java
// Created in MysqlCandleStore constructor:
ExecutorService writer = Executors.newFixedThreadPool(
    Math.max(1, asyncPoolSize),          // default: 2 threads
    r -> {
        Thread t = new Thread(r, "candle-store-writer");
        t.setDaemon(true);               // won't block JVM shutdown
        return t;
    });
```

**Pool size = 2** (`bias.cache.mysql.async-pool-size=2`)
Two threads can write simultaneously. If both are busy (e.g. warmer is
running), new save tasks queue up in the thread pool's unbounded queue.

**Daemon threads:** when the JVM shuts down, daemon threads are killed
immediately. This means in-flight saves may be lost on graceful shutdown.
Acceptable trade-off — Angel One can re-provide the data.

**Batch size = 500** (`bias.cache.mysql.batch-size=500`)
Up to 500 CandleEntities are sent to `repo.saveAll()` in one transaction.
For 260 daily candles, this is 1 batch. For a day of 5m candles (75 bars × 50
instruments = 3,750 candles), this is ~8 batches.

---

## 9. CandleEntity — JPA Mapping

### 9.1 Table structure

```sql
-- Auto-created by Hibernate (spring.jpa.hibernate.ddl-auto=update)
CREATE TABLE candle_history (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    broker        VARCHAR(16)    NOT NULL,
    exchange      VARCHAR(8)     NOT NULL,
    symbol_token  VARCHAR(32)    NOT NULL,
    interval_type VARCHAR(16)    NOT NULL,
    ts            DATETIME(6)    NOT NULL,
    open          DECIMAL(15,4)  NOT NULL,
    high          DECIMAL(15,4)  NOT NULL,
    low           DECIMAL(15,4)  NOT NULL,
    close         DECIMAL(15,4)  NOT NULL,
    volume        BIGINT         NOT NULL,
    saved_at      DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_candle (broker, exchange, symbol_token, interval_type, ts),
    KEY idx_lookup   (symbol_token, interval_type, ts),
    KEY idx_saved_at (saved_at)
);
```

### 9.2 Useful SQL queries

```sql
-- How many candles do we have?
SELECT COUNT(*) FROM candle_history;

-- Candles per interval for NIFTY (token 99926000)
SELECT interval_type, COUNT(*), MIN(ts), MAX(ts)
FROM candle_history
WHERE symbol_token = '99926000'
GROUP BY interval_type;

-- Check gap-fill is working: newest candle per instrument
SELECT symbol_token, interval_type, MAX(ts) as latest
FROM candle_history
GROUP BY symbol_token, interval_type
ORDER BY symbol_token, interval_type;

-- Table size
SELECT
    table_name,
    ROUND(data_length/1024/1024, 2) AS data_MB,
    ROUND(index_length/1024/1024, 2) AS index_MB,
    table_rows
FROM information_schema.tables
WHERE table_name = 'candle_history';
```

---

## 10. CandleRepository — Queries

```java
// package: com.angle.trading.persistence
public interface CandleRepository extends JpaRepository<CandleEntity, Long> {

    // PRIMARY READ — range query on hot index
    @Query("""
        SELECT c FROM CandleEntity c
        WHERE c.broker = :broker AND c.exchange = :exchange
          AND c.symbolToken = :symbolToken AND c.intervalType = :intervalType
          AND c.ts BETWEEN :from AND :to
        ORDER BY c.ts ASC
        """)
    List<CandleEntity> findRange(...);

    // LATEST TIMESTAMP — used by gap-fill
    @Query("SELECT MAX(c.ts) FROM CandleEntity c WHERE ...")
    Optional<Instant> findLatestTimestamp(...);

    // RETENTION PURGE — nightly cleanup
    @Modifying
    @Query("DELETE FROM CandleEntity c WHERE c.savedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

    // INSTRUMENT WIPE — admin action
    @Modifying
    @Query("DELETE FROM CandleEntity c WHERE c.symbolToken = :symbolToken")
    int deleteByToken(@Param("symbolToken") String symbolToken);
}
```

**`findRange()` performance:**
Uses `idx_lookup (symbol_token, interval_type, ts)`. For 250 rows, expect 5–15ms.
For 10,000 rows, expect 20–50ms.

**`findLatestTimestamp()` performance:**
`MAX(ts)` with the same index. Always < 5ms.

---

## 11. Configuration Reference

All properties are in `application.properties` under the `bias.cache.*` namespace.

### 11.1 Core

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `bias.cache.provider` | String | `caffeine` | Active cache backend. Values: `caffeine`, `noop`, `redis`, `mysql`, `caffeine-mysql`, `redis-mysql`, `layered`, `layered3` |
| `bias.cache.ttl-seconds` | int | `60` | L1 Caffeine TTL in seconds |
| `bias.cache.max-size` | int | `500` | Max entries in L1 Caffeine cache |
| `bias.cache.stats-log-minutes` | int | `15` | How often to log cache hit rate. Remove property to disable. |

### 11.2 Redis (L2)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `bias.cache.redis.ttl-seconds` | int | `300` | Redis TTL in seconds (5 min) |
| `bias.cache.redis.key-prefix` | String | `candles` | Redis key prefix |
| `spring.data.redis.host` | String | `localhost` | Redis host |
| `spring.data.redis.port` | int | `6379` | Redis port |

### 11.3 MySQL (L3)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `bias.cache.mysql.async-pool-size` | int | `2` | Background writer thread pool size |
| `bias.cache.mysql.batch-size` | int | `500` | Rows per INSERT batch |
| `bias.cache.mysql.retention-enabled` | boolean | `true` | Enable nightly purge |
| `bias.cache.mysql.retention-days` | int | `365` | Keep candles for this many days |
| `bias.cache.mysql.purge-cron` | String | `0 0 3 * * ?` | When to run retention purge |

### 11.4 BiasWarmer

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `bias.warmer.enabled` | boolean | `false` | Enable scheduled cache warming |
| `bias.warmer.warm-on-startup` | boolean | `false` | Warm cache on app startup |
| `bias.warmer.cron` | String | `0 */5 9-15 * * MON-FRI` | Warmer schedule |
| `bias.warmer.zone` | String | `Asia/Kolkata` | Cron timezone |
| `bias.warmer.thread-pool-size` | int | `10` | Parallel instrument fetches |
| `bias.warmer.timeout-seconds` | int | `60` | Max time per warm round |

---

## 12. How to Add a New Consumer

If you're building a new service that needs candle data, follow this pattern:

### Step 1 — Inject MarketDataService

```java
@Service
@RequiredArgsConstructor
public class MyService {
    private final MarketDataService marketDataService;
    private final BiasInstrumentRepository instrumentRepo;
}
```

### Step 2 — Pick the right interval and lookback

```
Purpose                          Interval         Lookback
─────────────────────────────────────────────────────────
Intraday signals / indicators    FIVE_MINUTE      5 days
Confirmation (higher TF)         FIFTEEN_MINUTE   10 days
Daily trend analysis             ONE_HOUR         20 days
Screener / scoring               ONE_DAY          260 days
Backtest replay                  varies           full date range
```

### Step 3 — Call getCandles()

```java
public void scan() {
    LocalDate to   = LocalDate.now();
    LocalDate from = to.minusDays(260);

    for (BiasInstrumentEntity instr : instrumentRepo.findByEnabledTrue()) {
        Exchange exchange;
        try {
            exchange = Exchange.valueOf(instr.getExchange());
        } catch (IllegalArgumentException e) {
            continue; // skip unknown exchanges
        }

        List<Candle> candles;
        try {
            candles = marketDataService.getCandles(
                instr.getBroker(),
                exchange,
                instr.getSymbolToken(),
                Interval.ONE_DAY,
                from, to
            );
        } catch (Exception e) {
            log.warn("Failed to fetch candles for {}: {}", instr.getSymbol(), e.getMessage());
            continue;
        }

        if (candles.size() < 50) {
            log.warn("Insufficient data for {} ({} candles)", instr.getSymbol(), candles.size());
            continue;
        }

        // Use candles...
        Candle latest = candles.get(candles.size() - 1);
        log.info("{} latest close: {}", instr.getSymbol(), latest.close());
    }
}
```

### Step 4 — Use existing indicator classes

```java
// All in package: com.angle.trading.indicator

List<BigDecimal> adx  = new AverageDirectionalIndex(14).compute(candles);
List<BigDecimal> rsi  = new RelativeStrengthIndex(14).compute(candles);
List<BigDecimal> ema  = new ExponentialMovingAverage(200).compute(candles);
List<BigDecimal> atr  = new AverageTrueRange(14).compute(candles);

// Results are parallel arrays: adx.get(i) corresponds to candles.get(i)
// Leading values are null (warmup period)
BigDecimal latestAdx = lastNonNull(adx);

// Helper:
private BigDecimal lastNonNull(List<BigDecimal> series) {
    for (int i = series.size() - 1; i >= 0; i--) {
        if (series.get(i) != null) return series.get(i);
    }
    return null;
}
```

---

## 13. How to Switch Cache Provider

### Switch to caffeine-mysql (no Redis needed)

```properties
# application.properties
bias.cache.provider=caffeine-mysql
```

Restart the app. Redis is no longer needed. MySQL still used for persistence.

### Switch to caffeine only (no Docker needed — local dev)

```properties
bias.cache.provider=caffeine
```

Restart. No Redis, no MySQL candle storage. All candles lost on restart.

### Enable BiasWarmer (automatic intraday saves)

```properties
bias.warmer.enabled=true
bias.warmer.warm-on-startup=true
```

No restart needed if using Spring DevTools, otherwise restart required.

---

## 14. Troubleshooting

### Redis connection refused on startup

```
RedisConnectionFailureException: Unable to connect to localhost/127.0.0.1:6379
```

**Fix option A:** Start Redis via Docker:
```bash
docker run -d --name angle-redis -p 6379:6379 redis:7-alpine
```

**Fix option B:** Switch cache provider (no Redis):
```properties
bias.cache.provider=caffeine-mysql
```

---

### Angel One returns empty candles

```
Gap-fill: L3 empty for 99926000:FIVE_MINUTE — full Angel fetch
[returns empty list]
```

**Causes:**
- Angel API keys not set (`ANGEL_API_KEY`, `ANGEL_CLIENT_CODE`, etc.)
- Token expired — re-login required
- Market holiday — no data for that date
- Wrong symbol token (check via `/admin/instruments/lookup?q=NIFTY`)

**Debug:**
```bash
# Check if keys are set
echo $ANGEL_API_KEY
echo $ANGEL_CLIENT_CODE

# Try direct API call
curl -u admin:admin123 http://localhost:9010/api/cache/debug
```

---

### Screener shows "Data unavailable" for some stocks

```
FoScreener: failed to fetch candles for RELIANCE — ...
```

**Causes:**
- Instrument has wrong symbol token
- Exchange mismatch (token is NFO but exchange set to NSE)
- Angel rate limit hit (too many instruments scanned at once)

**Fix:**
1. Go to `/admin/instruments`
2. Find the instrument
3. Click Edit → use the token lookup to re-verify the token
4. Check exchange matches the token's market

---

### MySQL candle_history table growing too large

```sql
-- Check current size
SELECT ROUND(data_length/1024/1024, 2) AS data_MB
FROM information_schema.tables WHERE table_name = 'candle_history';
```

**Fix:** Reduce retention period:
```properties
bias.cache.mysql.retention-days=180   # keep 6 months instead of 12
```

Or trigger manual purge (SQL):
```sql
DELETE FROM candle_history WHERE saved_at < DATE_SUB(NOW(), INTERVAL 180 DAY);
```

---

### Cache hit rate is low (logs showing many misses)

Check the stats log:
```
CandleCache stats — hits=150, misses=300, hitRate=33.3%, size=45
```

**Causes and fixes:**

| Cause | Fix |
|-------|-----|
| Too many different date ranges requested | Use consistent from/to dates |
| Caffeine max-size too small | Increase `bias.cache.max-size=1000` |
| TTL too short | Increase `bias.cache.ttl-seconds=300` |
| BiasWarmer disabled | Enable `bias.warmer.enabled=true` |

---

### Candle data is stale (old prices on dashboard)

**Caffeine TTL = 60s.** If dashboard shows 60-second-old data, that's expected.

For fresher data:
- Reduce TTL: `bias.cache.ttl-seconds=10`
- Or invalidate via admin: `POST /api/cache/invalidate/{token}`

---

## 15. Useful Admin Endpoints

All require authentication (admin:admin123 for HTTP Basic, or session cookie).

```bash
# Cache statistics — hit rate, size, entries
curl -u admin:admin123 http://localhost:9010/api/cache/debug

# Force cache warm (all instruments)
curl -u admin:admin123 -X POST http://localhost:9010/api/cache/warm

# Invalidate one instrument's cache (forces fresh fetch)
curl -u admin:admin123 -X POST http://localhost:9010/api/cache/invalidate/99926000

# Clear entire cache (L1 + L2 only — MySQL not affected)
curl -u admin:admin123 -X POST http://localhost:9010/api/cache/clear

# Check candle count in MySQL
curl -u admin:admin123 http://localhost:9010/api/cache/mysql/count

# Bulk seed 50 F&O stocks into instrument list
curl -u admin:admin123 -X POST http://localhost:9010/admin/instruments/bulk-seed

# F&O screener force re-scan
curl -u admin:admin123 "http://localhost:9010/screener?refresh=true"
```

---

> **See also:**
> - [HIGH-LEVEL.md](./HIGH-LEVEL.md) — architecture, design decisions, data flow
> - [../CANDLE-HISTORY.md](../CANDLE-HISTORY.md) — DFD diagrams, CandleHistoryJob design
> - [../FLOW-DIAGRAM.md](../FLOW-DIAGRAM.md) — system-wide flow diagrams
> - [../CONFIGURATION.md](../CONFIGURATION.md) — full configuration reference
