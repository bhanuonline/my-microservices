package com.example.apigateway.apikey;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Admin CRUD for API keys.
 *
 *   POST   /admin/apikeys        → create; response contains rawKey ONCE
 *   DELETE /admin/apikeys/{id}   → revoke by public key ID
 *
 * Requires JWT auth (existing SecurityFilterChain rules).
 */
@RestController
@RequestMapping("/admin/apikeys")
@ConditionalOnProperty(prefix = "gateway.apikey", name = "enabled", havingValue = "true")
public class ApiKeyAdminController {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyStore store;
    private final ApiKeyProperties props;

    public ApiKeyAdminController(ApiKeyStore store, ApiKeyProperties props) {
        this.store = store;
        this.props = props;
    }

    @PostMapping
    public Mono<ResponseEntity<CreateResponse>> create(@RequestBody CreateRequest req) {
        String rawKey = props.getKeyPrefix() + randomToken(32);
        String keyId = "key_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        ApiKeyRecord record = new ApiKeyRecord(
                keyId,
                rawKey.substring(0, Math.min(10, rawKey.length())),
                req.ownerId(),
                req.name(),
                req.scopes() == null ? List.of("read") : req.scopes(),
                req.rateLimitTier() == null ? "standard" : req.rateLimitTier(),
                req.expiresAt(),
                true,
                Instant.now());

        return store.save(record, rawKey)
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED)
                        .body(new CreateResponse(keyId, rawKey,
                                "Store this key now — it will not be shown again.")));
    }

    @DeleteMapping("/{keyId}")
    public Mono<ResponseEntity<Void>> revoke(@PathVariable String keyId) {
        return store.revokeById(keyId)
                .map(deleted -> deleted
                        ? ResponseEntity.noContent().<Void>build()
                        : ResponseEntity.notFound().<Void>build());
    }

    private String randomToken(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }

    public record CreateRequest(
            String ownerId,
            String name,
            List<String> scopes,
            String rateLimitTier,
            Instant expiresAt) {}

    public record CreateResponse(
            String keyId,
            String rawKey,
            String warning) {}
}
