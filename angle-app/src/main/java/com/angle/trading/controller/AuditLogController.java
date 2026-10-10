package com.angle.trading.controller;

import com.angle.trading.persistence.LoginAuditEntity;
import com.angle.trading.user.LoginAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Admin UI for the login audit trail.
 *
 *   GET /admin/audit                              latest 50 attempts
 *   GET /admin/audit?username=admin               filter to one user
 *   GET /admin/audit?success=false                only failures
 *   GET /admin/audit?username=admin&success=false bad passwords for admin
 *   GET /admin/audit?since=24                     last N hours
 *   GET /admin/audit?page=1&size=100              paging
 *
 * Mounted under /admin so inherits the ADMIN-only security chain.
 */
@Controller
@RequestMapping("/admin/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final LoginAuditService auditService;

    @GetMapping
    public String list(@RequestParam(required = false) String username,
                       @RequestParam(required = false) Boolean success,
                       @RequestParam(required = false) Integer since,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "50") int size,
                       Model model) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(500, Math.max(10, size)));
        Page<LoginAuditEntity> rows;

        if (since != null && since > 0) {
            Instant cutoff = Instant.now().minus(Duration.ofHours(since));
            rows = auditService.findSince(cutoff, pageable);
        } else {
            rows = auditService.findFiltered(username, success, pageable);
        }

        Set<String> suspiciousIps = auditService.suspiciousIps();

        model.addAttribute("rows", rows);
        model.addAttribute("suspiciousIps", suspiciousIps);
        model.addAttribute("filterUsername", username);
        model.addAttribute("filterSuccess", success);
        model.addAttribute("filterSince", since);
        return "admin/audit/list";
    }
}
