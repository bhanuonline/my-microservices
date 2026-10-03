package com.example.auth.controller.admin;

import com.example.auth.config.FeatureFlags;
import com.example.auth.repository.AuditEntryRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Read-only browser page for the audit log. Shows newest first, 50 per page.
 *
 * <p>Different from other admin controllers: it reads directly from
 * {@link AuditEntryRepository} instead of going through a service layer.
 * The audit view is display-only — no business rules to encapsulate.
 *
 * <p>Guarded by {@code features.audit.enabled}. When off, redirects to /admin
 * (the page effectively doesn't exist) and the nav link hides itself.
 */
@Controller
@Profile("jdbc")
@RequestMapping("/admin/audit")
public class AdminAuditController {

    private static final int PAGE_SIZE = 50;

    private final AuditEntryRepository repo;
    private final FeatureFlags flags;

    public AdminAuditController(AuditEntryRepository repo, FeatureFlags flags) {
        this.repo = repo;
        this.flags = flags;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        if (!flags.getAudit().isEnabled()) {
            return "redirect:/admin";
        }
        var entries = repo.findAllByOrderByChangedAtDesc(PageRequest.of(page, PAGE_SIZE));
        model.addAttribute("entries", entries);
        model.addAttribute("page", page);
        return "admin/audit/list";
    }
}
