package com.angle.trading.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Persistent record of an instrument tracked by the bias engine.
 *
 * Source of truth once populated. Seeded on first boot from
 * {@code bias.instruments[]} in application.properties, then edited
 * exclusively via {@code /admin/instruments} UI.
 *
 * Fields mirror {@link com.angle.trading.config.BiasProperties.Instrument}
 * so the transition from properties-backed to DB-backed is seamless.
 */
@Entity
@Table(
        name = "bias_instrument",
        uniqueConstraints = @UniqueConstraint(name = "uk_bias_instr_token", columnNames = {"symbol_token"}),
        indexes = {
                @Index(name = "idx_bias_instr_enabled_priority", columnList = "enabled,priority")
        }
)
@Data
@NoArgsConstructor
public class BiasInstrumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String symbol;                   // display name — "Nifty 50"

    @Column(nullable = false, length = 16)
    private String broker = "ANGEL";         // "ANGEL", "UPSTOX", "KITE"

    @Column(nullable = false, length = 8)
    private String exchange = "NSE";         // "NSE", "MCX", "NFO", ...

    @Column(name = "symbol_token", nullable = false, length = 32)
    private String symbolToken;              // Angel token, e.g. "99926000"

    @Column(name = "interval_type", nullable = false, length = 16)
    private String intervalType = "FIVE_MINUTE";

    @Column(nullable = false)
    private boolean enabled = true;

    /** Lower number = higher priority (warmed first, listed first). */
    @Column(nullable = false)
    private int priority = 100;

    @Column(length = 255)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public BiasInstrumentEntity(String symbol, String broker, String exchange, String symbolToken,
                                 String intervalType, boolean enabled, int priority) {
        this.symbol = symbol;
        this.broker = broker;
        this.exchange = exchange;
        this.symbolToken = symbolToken;
        this.intervalType = intervalType;
        this.enabled = enabled;
        this.priority = priority;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }
}
