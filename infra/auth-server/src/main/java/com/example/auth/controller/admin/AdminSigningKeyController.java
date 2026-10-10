package com.example.auth.controller.admin;

import com.example.auth.config.FeatureFlags;
import com.example.auth.service.admin.KeyRotationService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * Browser pages for signing-key management: list + rotate + retire.
 * The "brains" is {@link KeyRotationService} — this class is HTTP glue.
 *
 * <p>URL map:
 * <pre>
 *   GET  /admin/keys                 list all keys (PRIMARY/SECONDARY/RETIRED)
 *   POST /admin/keys/rotate          promote a new PRIMARY, demote old to SECONDARY
 *   POST /admin/keys/{kid}/retire    remove a SECONDARY from JWKS
 * </pre>
 *
 * <p>All endpoints are guarded by {@code features.key-rotation.enabled}. When
 * off, they redirect to /admin (as if the page doesn't exist). The nav link
 * in the layout also hides itself.
 */
@Controller
@Profile("jdbc")
@RequestMapping("/admin/keys")
public class AdminSigningKeyController {

    private final KeyRotationService service;
    private final FeatureFlags flags;

    public AdminSigningKeyController(KeyRotationService service, FeatureFlags flags) {
        this.service = service;
        this.flags = flags;
    }

    @GetMapping
    public String list(Model model) {
        if (!flags.getKeyRotation().isEnabled()) {
            return "redirect:/admin";
        }
        model.addAttribute("keys", service.listAll());
        model.addAttribute("features", flags);
        model.addAttribute("autoRetireAfterDays",
                flags.getKeyRotation().getAutoRetireAfterDays());
        return "admin/keys/list";
    }

    @PostMapping("/rotate")
    public String rotate() {
        if (!flags.getKeyRotation().isEnabled()) {
            return "redirect:/admin";
        }
        service.rotate();
        return "redirect:/admin/keys";
    }

    @PostMapping("/{kid}/retire")
    public String retire(@PathVariable String kid) {
        if (!flags.getKeyRotation().isEnabled()) {
            return "redirect:/admin";
        }
        service.retire(kid);
        return "redirect:/admin/keys";
    }
}
