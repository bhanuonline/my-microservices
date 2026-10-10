package com.example.auth.repository;

import com.example.auth.entity.AuditEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {
    Page<AuditEntry> findAllByOrderByChangedAtDesc(Pageable pageable);
}
