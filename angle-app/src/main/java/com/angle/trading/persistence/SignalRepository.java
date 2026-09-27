package com.angle.trading.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SignalRepository extends JpaRepository<SignalEntity, Long> {

    List<SignalEntity> findByStatusAndSymbolToken(String status, String symbolToken);

    List<SignalEntity> findByStatus(String status);

    /** All signals — newest first — created after cutoff. Used by the feed page. */
    @Query("SELECT s FROM SignalEntity s WHERE s.createdAt >= :cutoff ORDER BY s.createdAt DESC")
    List<SignalEntity> findRecent(@Param("cutoff") Instant cutoff);

    /** Filtered feed query. Any null filter is a wildcard. */
    @Query("""
           SELECT s FROM SignalEntity s
           WHERE s.createdAt >= :cutoff
             AND (:token  IS NULL OR s.symbolToken   = :token)
             AND (:status IS NULL OR s.status         = :status)
             AND (:source IS NULL OR s.source         = :source)
           ORDER BY s.createdAt DESC
           """)
    List<SignalEntity> findFiltered(
            @Param("cutoff") Instant cutoff,
            @Param("token")  String symbolToken,
            @Param("status") String status,
            @Param("source") String source);

    /** Dedupe check — was a signal for this symbol/action created within N minutes? */
    @Query("""
           SELECT COUNT(s) FROM SignalEntity s
           WHERE s.symbolToken = :token
             AND s.action       = :action
             AND s.createdAt   >= :cutoff
           """)
    long countRecent(
            @Param("token")  String symbolToken,
            @Param("action") String action,
            @Param("cutoff") Instant cutoff);
}
