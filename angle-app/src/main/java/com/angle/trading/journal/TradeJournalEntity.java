package com.angle.trading.journal;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "trade_journal",
        indexes = {
                @Index(name = "idx_tj_entry_dt", columnList = "entry_date_time"),
                @Index(name = "idx_tj_outcome",  columnList = "outcome"),
                @Index(name = "idx_tj_symbol",   columnList = "symbol")
        }
)
@Data
@NoArgsConstructor
public class TradeJournalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String symbol;

    @Column(nullable = false, length = 16)
    private String segment;                  // NSE / BSE / MCX / NFO / BFO / CDS

    @Column(name = "trade_type", nullable = false, length = 8)
    private String tradeType;               // BUY / SELL

    @Column(name = "entry_price", nullable = false, precision = 15, scale = 4)
    private BigDecimal entryPrice;

    @Column(name = "entry_date_time", nullable = false)
    private LocalDateTime entryDateTime;

    @Column(name = "exit_price", precision = 15, scale = 4)
    private BigDecimal exitPrice;

    @Column(name = "exit_date_time")
    private LocalDateTime exitDateTime;

    @Column(name = "stop_loss", precision = 15, scale = 4)
    private BigDecimal stopLoss;

    @Column(name = "target", precision = 15, scale = 4)
    private BigDecimal target;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "lot_size")
    private Integer lotSize;

    @Column(nullable = false, length = 8)
    private String timeframe;               // 1m / 5m / 15m / 1h / 1d

    @Column(name = "indicators_used", length = 500)
    private String indicatorsUsed;          // csv: RSI,MACD,ADX

    @Column(length = 128)
    private String strategy;

    @Column(name = "reason_for_entry", length = 1000)
    private String reasonForEntry;

    @Column(name = "market_condition", length = 32)
    private String marketCondition;         // Trending / Sideways / Volatile

    @Column(name = "emotional_state", length = 32)
    private String emotionalState;          // Calm / Confident / Anxious / FOMO / Greedy / Fearful

    @Column(nullable = false, length = 16)
    private String outcome = "OPEN";        // WIN / LOSS / BREAKEVEN / OPEN

    @Column(name = "pnl", precision = 15, scale = 4)
    private BigDecimal pnl;

    @Column(name = "pnl_percent", precision = 8, scale = 2)
    private BigDecimal pnlPercent;

    @Column(length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
