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
                // Covering index — includes close so screener reads don't hit the table row.
                // (symbol_token, interval_type, ts) is the range filter; close avoids extra lookup.
                @Index(name = "idx_lookup",   columnList = "symbol_token,interval_type,ts,close"),
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
    private String exchange;             // NSE / MCX / NFO / CDS

    @Column(name = "symbol_token", nullable = false, length = 32)
    private String symbolToken;

    @Column(name = "interval_type", nullable = false, length = 16)
    private String intervalType;         // FIVE_MINUTE / ONE_DAY ...

    @Column(nullable = false)
    private Instant ts;                  // candle open time (UTC)

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

    // Denormalised display name (e.g. "Nifty 50") — populated from bias_instrument
    // at save time so candles are identifiable without a JOIN if bias_instrument is
    // later modified or cleaned up.
    @Column(length = 64)
    private String symbol;

    // Open interest — populated only for NFO/MCX futures contracts.
    // NULL for NSE equity, index candles, and any instrument where OI is not
    // available from the Angel One historical API response.
    @Column(name = "open_interest")
    private Long openInterest;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    public CandleEntity(String broker, String exchange, String symbolToken, String intervalType,
                        Instant ts, BigDecimal open, BigDecimal high, BigDecimal low,
                        BigDecimal close, long volume, String symbol, Long openInterest) {
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
        this.symbol = symbol;
        this.openInterest = openInterest;
        this.savedAt = Instant.now();
    }
}
