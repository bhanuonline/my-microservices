package com.angle.trading.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A signal fired by SignalDetector — a consensus-BUY or consensus-SELL
 * on a specific instrument at a specific bar close.
 *
 * Lifecycle:
 *   OPEN        — just created, waiting for target/stop
 *   HIT_TARGET  — LTP crossed target → profit
 *   HIT_STOP    — LTP crossed stop → loss
 *   EXPIRED    — neither hit within {@code signals.monitor.expiry-hours}
 *   CANCELLED   — manually closed via /admin
 *
 * When status transitions from OPEN, {@code closedAt}, {@code closedPrice},
 * {@code profitPoints}, {@code profitPercent} are populated for reporting.
 */
@Entity
@Table(
        name = "bias_signal",
        indexes = {
                @Index(name = "idx_sig_status",  columnList = "status"),
                @Index(name = "idx_sig_created", columnList = "created_at"),
                @Index(name = "idx_sig_symbol",  columnList = "symbol_token,created_at")
        }
)
@Data
@NoArgsConstructor
public class SignalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "symbol_token", nullable = false, length = 32)
    private String symbolToken;

    @Column(nullable = false, length = 64)
    private String symbol;

    @Column(nullable = false, length = 8)
    private String exchange;

    @Column(name = "interval_type", nullable = false, length = 16)
    private String intervalType;

    @Column(nullable = false, length = 8)
    private String action;                     // BUY / SELL

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal entry;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal stop;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal target;

    @Column(name = "agreed_strategies", length = 500)
    private String agreedStrategies;            // csv

    @Column(name = "agreed_count", nullable = false)
    private int agreedCount;

    @Column(nullable = false, length = 16)
    private String status = "OPEN";

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_price", precision = 15, scale = 4)
    private BigDecimal closedPrice;

    @Column(name = "profit_points", precision = 15, scale = 4)
    private BigDecimal profitPoints;

    @Column(name = "profit_percent", precision = 6, scale = 2)
    private BigDecimal profitPercent;

    @Column(length = 500)
    private String notes;

    /** "CONSENSUS" (from technical strategies) or "AI" (from Claude). */
    @Column(nullable = false, length = 16)
    private String source = "CONSENSUS";

    /** For AI signals: high / medium / low. Null for CONSENSUS. */
    @Column(name = "ai_confidence", length = 8)
    private String aiConfidence;

    /** For AI signals: Claude's rationale text. Null for CONSENSUS. */
    @Column(name = "ai_rationale", length = 1500)
    private String aiRationale;

    /** Original stop set at entry — never changes. Used to detect trailing activity. */
    @Column(name = "original_stop", precision = 15, scale = 4)
    private BigDecimal originalStop;

    /** Highest price seen for BUY, lowest for SELL. Ratchet reference for trailing. */
    @Column(name = "high_water_mark", precision = 15, scale = 4)
    private BigDecimal highWaterMark;

    /** How many times the stop has been tightened. Handy debug + shows in feed UI. */
    @Column(name = "trail_updates", nullable = false)
    private int trailUpdates = 0;
}
