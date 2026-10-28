package com.angle.trading.candle;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Summary record of one CandleHistoryJob run.
 * One row per run (scheduled, manual, startup, or retry).
 */
@Entity
@Table(
    name = "candle_job_run",
    indexes = {
        @Index(name = "idx_job_started", columnList = "started_at DESC"),
        @Index(name = "idx_job_status",  columnList = "status"),
        @Index(name = "idx_job_name",    columnList = "job_name")
    }
)
@Data
@NoArgsConstructor
public class CandleJobRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── What ran ──────────────────────────────────────────────────────
    @Column(name = "job_name", nullable = false, length = 64)
    private String jobName;          // INTRADAY / DAILY / WEEKEND / SINGLE / RETRY / ALL

    @Column(name = "trigger_type", nullable = false, length = 16)
    private String triggerType;      // SCHEDULED / MANUAL / STARTUP / RETRY

    @Column(name = "triggered_by", nullable = false, length = 64)
    private String triggeredBy;      // "SYSTEM" or username

    @Column(name = "target_token", length = 32)
    private String targetToken;      // NULL = all instruments, set = single stock run

    // ── When ──────────────────────────────────────────────────────────
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;         // NULL while still running

    // ── Status ────────────────────────────────────────────────────────
    @Column(nullable = false, length = 16)
    private String status;           // RUNNING / COMPLETED / FAILED / CANCELLED

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    // ── Progress counters ─────────────────────────────────────────────
    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "done_count", nullable = false)
    private int doneCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "total_saved", nullable = false)
    private int totalSaved;          // total candle rows saved across all instruments

    // ── Config snapshot ───────────────────────────────────────────────
    @Column(name = "interval_type", length = 16)
    private String intervalType;     // NULL = all intervals

    @Column(name = "lookback_days")
    private Integer lookbackDays;

    @Column(name = "thread_count")
    private Integer threadCount;

    // ── Candle date range ─────────────────────────────────────────────
    @Column(name = "candle_from")
    private LocalDate candleFrom;     // earliest date fetched (e.g. 2024-10-25)

    @Column(name = "candle_to")
    private LocalDate candleTo;       // latest date fetched   (e.g. 2026-10-25)
}
