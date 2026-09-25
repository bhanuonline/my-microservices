package com.angle.trading.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Persistent record of one Angel candle (OHLCV at a specific timestamp).
 *
 * Design notes:
 *   - UNIQUE(broker, exchange, symbol_token, interval_type, ts) means the same
 *     candle can only exist once — safe to re-save on Angel re-fetch (upsert).
 *   - INDEX (symbol_token, interval_type, ts) makes range queries fast:
 *     "give me all Nifty 5-min candles from Sep 1 to Sep 24".
 *   - saved_at defaults to insertion time — useful for retention purge.
 *
 * Table auto-created by Hibernate (spring.jpa.hibernate.ddl-auto=update).
 */
@Entity
@Table(
        name = "candle_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_candle",
                columnNames = {"broker", "exchange", "symbol_token", "interval_type", "ts"}
        ),
        indexes = {
                @Index(name = "idx_lookup", columnList = "symbol_token,interval_type,ts"),
                @Index(name = "idx_saved_at", columnList = "saved_at")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CandleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String broker;               // ANGEL

    @Column(nullable = false, length = 8)
    private String exchange;             // NSE / MCX / NFO ...

    @Column(name = "symbol_token", nullable = false, length = 32)
    private String symbolToken;

    @Column(name = "interval_type", nullable = false, length = 16)
    private String intervalType;         // FIVE_MINUTE / ONE_DAY ...

    @Column(nullable = false)
    private Instant ts;                  // candle timestamp

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal open;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal high;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal low;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal close;

    @Column(nullable = false)
    private long volume;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    public CandleEntity(String broker, String exchange, String symbolToken, String intervalType,
                        Instant ts, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, long volume) {
        this.broker = broker;
        this.exchange = exchange;
        this.symbolToken = symbolToken;
        this.intervalType = intervalType;
        this.ts = ts;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
        this.savedAt = Instant.now();
    }
}
