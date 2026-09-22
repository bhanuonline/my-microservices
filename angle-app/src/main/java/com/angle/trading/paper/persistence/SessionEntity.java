package com.angle.trading.paper.persistence;

import com.angle.trading.paper.model.SessionSnapshot;
import com.angle.trading.paper.model.SessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Persistent record of a paper-trading session.
 *
 * We deliberately do NOT persist the live runtime state (open position,
 * lastIntent, candle series, source thread). Only the durable facts:
 * who ran what strategy when, and what the final stats were.
 *
 * On restart, sessions in the RUNNING state stay RUNNING in the DB but
 * are effectively orphaned — no live thread is polling for them. A future
 * "resume" feature could re-hydrate them; for now they're just history.
 */
@Entity
@Table(name = "paper_session", indexes = {
        @Index(name = "idx_session_status",   columnList = "status"),
        @Index(name = "idx_session_strategy", columnList = "strategy_name"),
        @Index(name = "idx_session_started",  columnList = "started_at")
})
@Data
@NoArgsConstructor
public class SessionEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "strategy_name", nullable = false, length = 64)
    private String strategyName;

    @Column(name = "source_name", nullable = false, length = 256)
    private String sourceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SessionStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "candle_count", nullable = false)
    private int candleCount;

    @Column(name = "total_trades", nullable = false)
    private int totalTrades;

    @Column(nullable = false)
    private int winners;

    @Column(nullable = false)
    private int losers;

    @Column(name = "net_pnl", nullable = false, precision = 20, scale = 4)
    private BigDecimal netPnl = BigDecimal.ZERO;

    /** Build from a snapshot (called on session start). */
    public static SessionEntity from(SessionSnapshot snap) {
        SessionEntity e = new SessionEntity();
        e.id           = snap.sessionId();
        e.strategyName = snap.strategyName();
        e.sourceName   = snap.sourceName();
        e.status       = snap.status();
        e.startedAt    = snap.startedAt();
        e.candleCount  = snap.candleCount();
        e.totalTrades  = snap.totalTrades();
        e.winners      = snap.winners();
        e.losers       = snap.losers();
        e.netPnl       = snap.netPnl() == null ? BigDecimal.ZERO : snap.netPnl();
        return e;
    }

    /** Mutate to match the given snapshot (called on session end + periodic status). */
    public void updateFrom(SessionSnapshot snap) {
        this.status      = snap.status();
        this.candleCount = snap.candleCount();
        this.totalTrades = snap.totalTrades();
        this.winners     = snap.winners();
        this.losers      = snap.losers();
        this.netPnl      = snap.netPnl() == null ? BigDecimal.ZERO : snap.netPnl();
        if (snap.status() != SessionStatus.RUNNING && this.endedAt == null) {
            this.endedAt = Instant.now();
        }
    }
}
