package com.angle.trading.journal;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Stores trades taken from the Analysis API trade suggestions.
 * Auto-closes when price hits target or stop loss via AnalysisTradeMonitor.
 */
@Entity
@Table(name = "analysis_trade_journal",
       indexes = {
           @Index(name = "idx_atj_symbol",  columnList = "symbol"),
           @Index(name = "idx_atj_status",  columnList = "status"),
           @Index(name = "idx_atj_created", columnList = "created_at")
       })
@Data
@NoArgsConstructor
public class AnalysisTradeJournalEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Instrument ───────────────────────────────────────────────
    @Column(nullable = false, length = 64) private String symbol;
    @Column(nullable = false, length = 10)  private String exchange;
    @Column(name = "interval_stack", length = 20) private String intervalStack;

    // ── Entry ────────────────────────────────────────────────────
    @Column(nullable = false, length = 5)   private String direction;   // LONG | SHORT
    @Column(name = "entry_zone_from", precision = 20, scale = 4) private BigDecimal entryZoneFrom;
    @Column(name = "entry_zone_to",   precision = 20, scale = 4) private BigDecimal entryZoneTo;
    @Column(name = "entry_price",     precision = 20, scale = 4) private BigDecimal entryPrice;
    @Column(name = "entry_time",      nullable = false)           private Instant    entryTime;

    // ── Stop & Targets ───────────────────────────────────────────
    @Column(name = "stop_loss",  precision = 20, scale = 4) private BigDecimal stopLoss;
    @Column(name = "stop_basis", length = 64)               private String     stopBasis;
    @Column(name = "target1",    precision = 20, scale = 4) private BigDecimal target1;
    @Column(name = "target1_rr", length = 10)               private String     target1Rr;
    @Column(name = "target2",    precision = 20, scale = 4) private BigDecimal target2;
    @Column(name = "target2_rr", length = 10)               private String     target2Rr;

    // ── MTF Context at entry ─────────────────────────────────────
    @Column(name = "primary_bias",   length = 10)  private String primaryBias;
    @Column(name = "confidence",     length = 10)  private String confidence;
    @Column(name = "trade_setup",    length = 50)  private String tradeSetup;
    @Column(name = "aligned_tfs",    length = 255) private String alignedTfs;
    @Column(name = "conflicting_tfs",length = 255) private String conflictingTfs;

    // ── Key Indicators at entry ──────────────────────────────────
    @Column(name = "rsi14",   precision = 10, scale = 4) private BigDecimal rsi14;
    @Column(name = "adx14",   precision = 10, scale = 4) private BigDecimal adx14;
    @Column(name = "plus_di", precision = 10, scale = 4) private BigDecimal plusDi;
    @Column(name = "minus_di",precision = 10, scale = 4) private BigDecimal minusDi;
    @Column(name = "supertrend", length = 10)            private String     supertrend;

    // ── ORB at entry ─────────────────────────────────────────────
    @Column(name = "orb_15m", length = 10) private String orb15m;
    @Column(name = "orb_30m", length = 10) private String orb30m;
    @Column(name = "orb_60m", length = 10) private String orb60m;

    // ── Outcome (auto-updated by monitor) ────────────────────────
    @Column(nullable = false, length = 20)
    private String status = "OPEN"; // OPEN|TARGET1_HIT|TARGET2_HIT|STOP_HIT|MANUAL_CLOSE

    @Column(name = "close_price",  precision = 20, scale = 4) private BigDecimal closePrice;
    @Column(name = "close_time")                               private Instant    closeTime;
    @Column(name = "close_reason", length = 20)                private String     closeReason;

    @Column(name = "profit_points",  precision = 10, scale = 4) private BigDecimal profitPoints;
    @Column(name = "profit_percent", precision = 10, scale = 4) private BigDecimal profitPercent;

    // ── Notes ────────────────────────────────────────────────────
    @Column(columnDefinition = "TEXT") private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate void onUpdate() { this.updatedAt = Instant.now(); }
}
