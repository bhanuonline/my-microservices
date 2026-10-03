package com.example.auth.api.v1;

import com.example.auth.api.v1.dto.AuditResponse;
import com.example.auth.repository.AuditEntryRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only REST endpoint for the audit log. Paged, newest first.
 *
 * <p>Only GET is exposed — audit entries are immutable once written. If you
 * want to purge old entries, that's a job for a scheduled task with DB
 * access, not this API.
 *
 * <p>Standard usage from a SIEM (Splunk, Datadog, ELK): poll every N minutes,
 * follow pages until an entry with a known-seen id appears, index the new
 * rows. Fine for low-volume auth-server audit; higher-volume systems push to
 * Kafka instead of pull.
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
@Profile("jdbc")
public class AuditRestController {

    private static final int PAGE_SIZE = 50;

    private final AuditEntryRepository repo;

    public AuditRestController(AuditEntryRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_admin.read')")
    public List<AuditResponse> list(@RequestParam(defaultValue = "0") int page) {
        return repo.findAllByOrderByChangedAtDesc(PageRequest.of(page, PAGE_SIZE))
                .stream()
                .map(AuditResponse::from)
                .toList();
    }
}
