package com.angle.trading.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BriefingRepository extends JpaRepository<BriefingEntity, Long> {

    Optional<BriefingEntity> findByForDate(LocalDate date);

    /** Newest N briefings for the history page. */
    List<BriefingEntity> findTop30ByOrderByForDateDesc();

    /** Retention purge. */
    @Modifying
    @Query("DELETE FROM BriefingEntity b WHERE b.generatedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
