package com.angle.trading.candle;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Per-instrument result within one CandleHistoryJob run.
 * One row per instrument per run.
 * Used for Retry Failed/Cancelled support.
 */
@Entity
@Table(
    name = "candle_job_run_detail",
    indexes = {
        @Index(name = "idx_detail_run",    columnList = "run_id"),
        @Index(name = "idx_detail_status", columnList = "run_id,status")
    }
)
@Data
@NoArgsConstructor
public class CandleJobRunDetailEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;              // FK → candle_job_run.id

    @Column(name = "symbol_token", nullable = false, length = 32)
    private String symbolToken;

    @Column(length = 64)
    private String symbol;           // display name e.g. "Nifty 50"

    @Column(nullable = false, length = 16)
    private String status;           // DONE / FAILED / SKIPPED / CANCELLED

    @Column(name = "candles_saved", nullable = false)
    private int candlesSaved;

    @Column(name = "error_msg", length = 500)
    private String errorMsg;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "duration_ms")
    private Integer durationMs;
}
