package com.angle.trading.persistence;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A scraped news headline with optional AI sentiment tag.
 *
 * Deduped by URL (unique index). AI tagging happens async after save —
 * `sentiment` and `sector` are null until the tagger processes the row.
 */
@Entity
@Table(
        name = "news_headline",
        indexes = {
                @Index(name = "idx_nh_published",  columnList = "published_at"),
                @Index(name = "idx_nh_sentiment",  columnList = "sentiment"),
                @Index(name = "idx_nh_source",     columnList = "source_name")
        },
        uniqueConstraints = @UniqueConstraint(name = "uk_nh_url", columnNames = "url")
)
@Data
@NoArgsConstructor
public class NewsHeadlineEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(name = "source_name", nullable = false, length = 80)
    private String sourceName;

    @Column(name = "source_category", nullable = false, length = 16)
    private String sourceCategory;      // MARKETS / ECONOMY / GENERAL

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    /** BULLISH / BEARISH / NEUTRAL — set by AI tagger. null until tagged. */
    @Column(length = 12)
    private String sentiment;

    /** Sector inferred by AI: BANKING / IT / OIL / AUTO / PHARMA / METALS / GENERAL / etc. */
    @Column(length = 32)
    private String sector;

    /** Short 1-sentence explanation from AI. */
    @Column(length = 500)
    private String rationale;

    /** true once AI has tagged. Used to find un-tagged rows. */
    @Column(name = "ai_tagged", nullable = false)
    private boolean aiTagged = false;
}
