package com.example.auth.api.v1;

import com.example.auth.api.v1.dto.ClientRequest;
import com.example.auth.api.v1.dto.ClientResponse;
import com.example.auth.service.admin.ClientAdminService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * JSON REST version of the browser /admin/clients CRUD. Delegates to the same
 * {@link ClientAdminService} — no duplicated business logic.
 *
 * <p>URL map:
 * <pre>
 *   GET    /api/v1/admin/clients             list
 *   GET    /api/v1/admin/clients/{id}        one
 *   POST   /api/v1/admin/clients             create → 201 with new resource
 *   PUT    /api/v1/admin/clients/{id}        update → 200 with updated resource
 *   DELETE /api/v1/admin/clients/{id}        delete → 204
 * </pre>
 *
 * <p>Auth model: JWT bearer only. Caller gets a token from {@code /oauth2/token}
 * as the seeded {@code api-admin} client (see V11 migration) with scopes
 * {@code admin.read} + {@code admin.write}. Method-level
 * {@link org.springframework.security.access.prepost.PreAuthorize} enforces
 * per-endpoint scope requirements — Spring auto-maps JWT {@code scope} claim
 * values to {@code SCOPE_*} authorities.
 *
 * <p>Errors: RFC 7807 {@code application/problem+json} bodies via
 * {@link ApiExceptionAdvice}.
 *
 * <p>Interview point: because both browser and REST call the SAME service, the
 * audit log automatically distinguishes: {@code actor=admin} for browser
 * sessions, {@code actor=api-admin} for JWT bearer. No extra code.
 */
@RestController
@RequestMapping("/api/v1/admin/clients")
@Profile("jdbc")
public class ClientRestController {

    private final ClientAdminService service;

    public ClientRestController(ClientAdminService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_admin.read')")
    public List<Map<String, Object>> list() {
        return service.listAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_admin.read')")
    public ClientResponse get(@PathVariable String id) {
        RegisteredClient rc = service.findById(id);
        if (rc == null) throw new NotFoundException("client id=" + id);
        return ClientResponse.from(rc);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_admin.write')")
    public ResponseEntity<ClientResponse> create(@Valid @RequestBody ClientRequest req) {
        service.save(req.toForm());
        RegisteredClient created = service.findByClientId(req.clientId());
        return ResponseEntity
                .created(URI.create("/api/v1/admin/clients/" + created.getId()))
                .body(ClientResponse.from(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_admin.write')")
    public ClientResponse update(@PathVariable String id, @Valid @RequestBody ClientRequest req) {
        if (service.findById(id) == null) throw new NotFoundException("client id=" + id);
        var form = req.toForm();
        form.setId(id);
        service.save(form);
        return ClientResponse.from(service.findById(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_admin.write')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (service.findById(id) == null) throw new NotFoundException("client id=" + id);
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /** Signal 404 to ApiExceptionAdvice. */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String msg) { super(msg); }
    }
}
