package com.example.auth.api.v1;

import com.example.auth.api.v1.dto.UserRequest;
import com.example.auth.api.v1.dto.UserResponse;
import com.example.auth.entity.AppUser;
import com.example.auth.service.admin.UserAdminService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * JSON REST version of /admin/users. Delegates to {@link UserAdminService}
 * (same service the browser uses) so business logic + audit trail stay in one
 * place.
 *
 * <p>URL map:
 * <pre>
 *   GET    /api/v1/admin/users              list                 admin.read
 *   GET    /api/v1/admin/users/{id}         one                  admin.read
 *   POST   /api/v1/admin/users              create → 201         admin.write
 *   PUT    /api/v1/admin/users/{id}         update               admin.write
 *   DELETE /api/v1/admin/users/{id}         delete → 204         admin.write
 *   POST   /api/v1/admin/users/{id}/unlock  clear lockout        admin.unlock
 * </pre>
 *
 * <p>The unlock endpoint requires its own scope ({@code admin.unlock}) because
 * security-sensitive operations should be independently grantable. A CI/CD
 * pipeline that manages clients might not be allowed to unlock user accounts.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@Profile("jdbc")
public class UserRestController {

    private final UserAdminService service;

    public UserRestController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_admin.read')")
    public List<UserResponse> list() {
        return service.listAll().stream().map(UserResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_admin.read')")
    public UserResponse get(@PathVariable Long id) {
        AppUser u = service.findById(id);
        if (u == null) throw new ClientRestController.NotFoundException("user id=" + id);
        return UserResponse.from(u);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_admin.write')")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest req) {
        service.save(req.toForm());
        AppUser created = service.listAll().stream()
                .filter(u -> u.getUsername().equals(req.username()))
                .findFirst().orElseThrow();
        return ResponseEntity
                .created(URI.create("/api/v1/admin/users/" + created.getId()))
                .body(UserResponse.from(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_admin.write')")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserRequest req) {
        if (service.findById(id) == null) throw new ClientRestController.NotFoundException("user id=" + id);
        var form = req.toForm();
        form.setId(id);
        service.save(form);
        return UserResponse.from(service.findById(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_admin.write')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (service.findById(id) == null) throw new ClientRestController.NotFoundException("user id=" + id);
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /** FEATURE 5 unlock via API — separate scope for least-privilege. */
    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasAuthority('SCOPE_admin.unlock')")
    public UserResponse unlock(@PathVariable Long id) {
        if (service.findById(id) == null) throw new ClientRestController.NotFoundException("user id=" + id);
        service.unlock(id);
        return UserResponse.from(service.findById(id));
    }
}
