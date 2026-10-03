package com.angle.trading.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NewsHeadlineRepository extends JpaRepository<NewsHeadlineEntity, Long> {

    /** Dedupe check — was this URL already saved? */
    Optional<NewsHeadlineEntity> findByUrl(String url);

    /** Feed page — newest first. Optional filters as nullable params. */
    @Query("""
           SELECT h FROM NewsHeadlineEntity h
           WHERE h.fetchedAt >= :cutoff
             AND (:source    IS NULL OR h.sourceName     = :source)
             AND (:sentiment IS NULL OR h.sentiment       = :sentiment)
             AND (:sector    IS NULL OR h.sector          = :sector)
           ORDER BY h.publishedAt DESC NULLS LAST, h.fetchedAt DESC
           """)
    List<NewsHeadlineEntity> findFiltered(
            @Param("cutoff")    Instant cutoff,
            @Param("source")    String  source,
            @Param("sentiment") String  sentiment,
            @Param("sector")    String  sector,
            Pageable page);

    /** Un-tagged rows for the AI tagger to process. */
    @Query("SELECT h FROM NewsHeadlineEntity h WHERE h.aiTagged = false ORDER BY h.fetchedAt DESC")
    List<NewsHeadlineEntity> findUntagged(Pageable page);

    /** Retention job — delete anything older than cutoff. */
    @Modifying
    @Query("DELETE FROM NewsHeadlineEntity h WHERE h.fetchedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

    /** Simple counter for the "N headlines in last 24h" stat. */
    long countByFetchedAtAfter(Instant cutoff);
}
