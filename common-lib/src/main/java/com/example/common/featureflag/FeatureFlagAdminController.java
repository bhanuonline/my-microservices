package com.example.common.featureflag;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/flags")
public class FeatureFlagAdminController {

    private final FeatureFlagRepository repo;
    private final FeatureFlagService svc;

    public FeatureFlagAdminController(FeatureFlagRepository repo, FeatureFlagService svc) {
        this.repo = repo;
        this.svc = svc;
    }

    @GetMapping
    public List<FeatureFlag> list() { return repo.findAll(); }

    @GetMapping("/{key}")
    public ResponseEntity<FeatureFlag> get(@PathVariable String key) {
        return repo.findById(key)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Create or update. Body: {"description":"…","enabled":true,"rulesJson":"{...}"} */
    @PutMapping("/{key}")
    public FeatureFlag upsert(@PathVariable String key,
                              @RequestBody Map<String, Object> body) {
        FeatureFlag existing = repo.findById(key).orElse(null);
        boolean enabled = (boolean) body.getOrDefault("enabled", false);
        String rulesJson = (String) body.get("rulesJson");
        String desc = (String) body.get("description");

        FeatureFlag saved;
        if (existing != null) {
            if (desc != null) existing.setDescription(desc);
            existing.update(enabled, rulesJson, operator());
            saved = repo.save(existing);
        } else {
            FeatureFlag f = new FeatureFlag(key, desc, enabled, rulesJson);
            saved = repo.save(f);
        }
        svc.invalidate(key);                       // local cache evict; cluster-wide propagation TODO (Kafka)
        return saved;
    }

    @PostMapping("/{key}/toggle")
    public FeatureFlag toggle(@PathVariable String key) {
        FeatureFlag f = repo.findById(key)
                .orElseThrow(() -> new IllegalArgumentException("no such flag: " + key));
        f.update(!f.isEnabled(), f.getRulesJson(), operator());
        FeatureFlag saved = repo.save(f);
        svc.invalidate(key);
        return saved;
    }

    /** Pulls operator identity from the generic Authentication; works with any resource-server impl. */
    private static String operator() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getName() != null ? auth.getName() : "anonymous";
    }
}
