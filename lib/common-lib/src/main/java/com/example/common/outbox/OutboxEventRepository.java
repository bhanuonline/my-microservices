package com.example.common.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * FOR UPDATE SKIP LOCKED — key trick that makes multiple poller instances safe.
     * Two pods can poll at the same time; each locks a disjoint set of rows,
     * neither blocks the other, neither picks up the same row.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
        // Hibernate 6 way to add SKIP LOCKED on top of FOR UPDATE.
        // Without SKIP LOCKED, second poller blocks on first poller's rows.
        @jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")
    })
    @Query("select e from OutboxEvent e where e.status = 'PENDING' order by e.createdAt asc")
    List<OutboxEvent> lockPendingBatch(Pageable pageable);
}
