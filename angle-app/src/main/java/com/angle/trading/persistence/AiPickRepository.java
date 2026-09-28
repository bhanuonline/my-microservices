package com.angle.trading.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AiPickRepository extends JpaRepository<AiPickEntity, Long> {

    List<AiPickEntity> findByStatus(String status);

    /** Recent picks, newest first, for the portfolio page. */
    @Query("SELECT p FROM AiPickEntity p WHERE p.createdAt >= :cutoff ORDER BY p.createdAt DESC")
    List<AiPickEntity> findRecent(@Param("cutoff") Instant cutoff);

    /** Dedupe: does this symbol already have an OPEN pick from the last N days? */
    @Query("""
           SELECT COUNT(p) FROM AiPickEntity p
           WHERE p.symbolToken = :token
             AND p.status       = 'OPEN'
             AND p.createdAt   >= :cutoff
           """)
    long countRecentOpen(@Param("token") String symbolToken, @Param("cutoff") Instant cutoff);
}
