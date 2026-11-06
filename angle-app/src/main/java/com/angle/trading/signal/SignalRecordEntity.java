package com.angle.trading.signal;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
    name = "signal_record",
    indexes = {
        @Index(name = "idx_sr_date",   columnList = "trade_date"),
        @Index(name = "idx_sr_market", columnList = "market"),
        @Index(name = "idx_sr_symbol", columnList = "symbol")
    }
)
@Data
@NoArgsConstructor
public class SignalRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String market;                 // NSE / MCX / BSE / NFO / BFO / CDS

    @Column(nullable = false, length = 64)
    private String symbol;                 // "NIFTY 50", "CRUDEOIL", "RELIANCE"

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(length = 200)
    private String note;

    // Denormalized — computed on save so the list page never parses stateJson
    @Column(name = "overall_signal", length = 8)
    private String overallSignal;          // BUY / SELL / NEUTRAL

    @Column(name = "overall_sum")
    private Integer overallSum;

    @Column(name = "state_json", columnDefinition = "TEXT")
    private String stateJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
