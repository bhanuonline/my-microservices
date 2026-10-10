package com.example.auth.api.v1;

import com.example.auth.api.v1.dto.KeyResponse;
import com.example.auth.service.admin.KeyRotationService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * JSON REST version of /admin/keys. Backed by {@link KeyRotationService}.
 *
 * <p>URL map:
 * <pre>
 *   GET  /api/v1/admin/keys                    list all keys       admin.read
 *   POST /api/v1/admin/keys/rotate             new PRIMARY         admin.write
 *   POST /api/v1/admin/keys/{kid}/retire       drop SECONDARY      admin.write
 * </pre>
 *
 * <p>Typical automation pattern: a scheduled CI job hits {@code POST /rotate}
 * every 90 days. Old tokens keep verifying (they still find their kid in
 * JWKS as SECONDARY) — no user gets logged out.
 */
@RestController
@RequestMapping("/api/v1/admin/keys")
@Profile("jdbc")
public class KeyRestController {

    private final KeyRotationService service;

    public KeyRestController(KeyRotationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_admin.read')")
    public List<KeyResponse> list() {
        return service.listAll().stream().map(KeyResponse::from).toList();
    }

    @PostMapping("/rotate")
    @PreAuthorize("hasAuthority('SCOPE_admin.write')")
    public KeyResponse rotate() {
        return KeyResponse.from(service.rotate());
    }

    @PostMapping("/{kid}/retire")
    @PreAuthorize("hasAuthority('SCOPE_admin.write')")
    public ResponseEntity<Void> retire(@PathVariable String kid) {
        service.retire(kid);
        return ResponseEntity.noContent().build();
    }
}
