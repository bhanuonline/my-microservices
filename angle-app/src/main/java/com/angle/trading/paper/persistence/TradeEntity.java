package com.angle.trading.paper.persistence;

import com.angle.trading.strategy.model.ExitReason;
import com.angle.trading.strategy.model.IntentAction;
import com.angle.trading.strategy.model.Trade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * One completed paper trade — persisted immediately when a session closes a position.
 *
 * No FK constraint to {@link SessionEntity} on purpose: keeps trade
 * inserts independent of session updates and avoids cascading concerns.
 * The {@code session_id} column is a soft reference — indexed for queries.
 */
@Entity
@Table(name = "paper_trade", indexes = {
        @Index(name = "idx_trade_session",   columnList = "session_id"),
        @Index(name = "idx_trade_entry",     columnList = "entry_time"),
        @Index(name = "idx_trade_direction", columnList = "direction")
})
@Data
@NoArgsConstructor
public class TradeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IntentAction direction;

    @Column(name = "entry_time", nullable = false)
    private Instant entryTime;

    @Column(name = "entry_price", nullable = false, precision = 20, scale = 4)
    private BigDecimal entryPrice;

    @Column(name = "exit_time", nullable = false)
    private Instant exitTime;

    @Column(name = "exit_price", nullable = false, precision = 20, scale = 4)
    private BigDecimal exitPrice;

    @Column(name = "stop_loss", precision = 20, scale = 4)
    private BigDecimal stopLoss;

    @Column(precision = 20, scale = 4)
    private BigDecimal target;

    @Enumerated(EnumType.STRING)
    @Column(name = "exit_reason", nullable = false, length = 32)
    private ExitReason exitReason;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal pnl;

    @Column(length = 512)
    private String rationale;

    public static TradeEntity from(String sessionId, Trade t) {
        TradeEntity e = new TradeEntity();
        e.sessionId   = sessionId;
        e.direction   = t.direction();
        e.entryTime   = t.entryTime();
        e.entryPrice  = t.entryPrice();
        e.exitTime    = t.exitTime();
        e.exitPrice   = t.exitPrice();
        e.stopLoss    = t.stopLoss();
        e.target      = t.target();
        e.exitReason  = t.exitReason();
        e.pnl         = t.pnl();
        e.rationale   = t.rationale();
        return e;
    }
}
