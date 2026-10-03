package com.angle.trading.persistence;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One pre-market briefing snapshot.
 *
 *   forDate     = the trading day this briefing is FOR (not when generated)
 *   summary     = Claude's ~200-word overnight summary
 *   bias        = one of: BULLISH / BEARISH / NEUTRAL / CAUTIOUS
 *   marketsJson = raw global-markets snapshot (for audit trail)
 *   newsJson    = raw headline titles considered
 */
@Entity
@Table(
        name = "briefing",
        indexes = { @Index(name = "idx_br_date", columnList = "for_date") },
        uniqueConstraints = @UniqueConstraint(name = "uk_br_date", columnNames = "for_date")
)
@Data
@NoArgsConstructor
public class BriefingEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "for_date", nullable = false)
    private LocalDate forDate;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(nullable = false, length = 16)
    private String bias;            // BULLISH / BEARISH / NEUTRAL / CAUTIOUS

    @Column(nullable = false, length = 4000)
    private String summary;

    @Column(name = "key_drivers", length = 1500)
    private String keyDrivers;      // 3-5 bullets that shaped the bias

    @Column(name = "markets_snapshot_json", columnDefinition = "TEXT")
    private String marketsJson;

    @Column(name = "news_titles_json", columnDefinition = "TEXT")
    private String newsJson;

    @Column(name = "model", length = 40)
    private String model;

    /** Notification status flags — updated after email/telegram send. */
    @Column(name = "email_sent",    nullable = false) private boolean emailSent    = false;
    @Column(name = "telegram_sent", nullable = false) private boolean telegramSent = false;
}
