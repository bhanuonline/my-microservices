package com.example.common.dlq;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DlqEventRepository extends JpaRepository<DlqEvent, Long> {
    Page<DlqEvent> findByStatus(DlqEvent.Status status, Pageable pageable);
}
