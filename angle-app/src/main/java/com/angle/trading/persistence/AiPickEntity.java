package com.angle.trading.persistence;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A single AI stock recommendation for portfolio (medium-term investing).
 *
 * Distinct from bias_signal (intraday trading signals). Longer horizon,
 * fundamentals-aware where possible.
 *
 * Lifecycle:
 *   OPEN         — just generated, waiting for target/stop
 *   HIT_TARGET   — close price ≥ target (BUY)
 *   HIT_STOP     — close price ≤ stop-loss
 *   EXPIRED      — horizon days elapsed without hit
 *   CANCELLED    — manually closed
 */
@Entity
@Table(
        name = "ai_pick",
        indexes = {
                @Index(name = "idx_aip_status",  columnList = "status"),
                @Index(name = "idx_aip_created", columnList = "created_at"),
                @Index(name = "idx_aip_symbol",  columnList = "symbol_token,created_at")
        }
)
@Data
@NoArgsConstructor
public class AiPickEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "symbol_token", nullable = false, length = 32)
    private String symbolToken;

    @Column(nullable = false, length = 64)
    private String symbol;

    @Column(nullable = false, length = 8)
    private String exchange;

    @Column(nullable = false, length = 8)
    private String action;                    // BUY / HOLD / AVOID

    @Column(name = "entry_price", precision = 15, scale = 4)
    private BigDecimal entryPrice;

    @Column(name = "target_price", precision = 15, scale = 4)
    private BigDecimal targetPrice;

    @Column(name = "stop_loss", precision = 15, scale = 4)
    private BigDecimal stopLoss;

    @Column(length = 8)
    private String confidence;                // HIGH / MEDIUM / LOW

    @Column(name = "horizon_days")
    private Integer horizonDays;

    @Column(length = 1500)
    private String rationale;

    @Column(length = 32)
    private String model;                     // claude model id at time of pick

    // Outcome tracking
    @Column(nullable = false, length = 16)
    private String status = "OPEN";

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_price", precision = 15, scale = 4)
    private BigDecimal closedPrice;

    @Column(name = "return_percent", precision = 8, scale = 2)
    private BigDecimal returnPercent;         // signed % move from entry to closed
}
