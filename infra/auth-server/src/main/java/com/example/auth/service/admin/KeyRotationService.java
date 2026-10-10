package com.example.auth.service.admin;

import com.example.auth.entity.SigningKeyEntity;
import com.example.auth.entity.SigningKeyEntity.Status;
import com.example.auth.repository.SigningKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Handles the "rotate the signing key" button on /admin/keys and its REST twin.
 *
 * <p>The lifecycle of a signing key:
 * <pre>
 *   PRIMARY   ── rotate ──▶ SECONDARY   ── retire ──▶ RETIRED
 *   (signs new                (only verifies              (gone from JWKS,
 *    tokens)                   old tokens)                 kept for audit)
 * </pre>
 *
 * <p>Rules we enforce:
 * <ul>
 *   <li><b>At most ONE PRIMARY at any time.</b> {@link #rotate()} runs in a
 *       single @Transactional: insert new PRIMARY + demote old PRIMARY to
 *       SECONDARY. Both writes or neither.</li>
 *   <li><b>Can't retire PRIMARY directly.</b> Would leave the auth-server with
 *       no signing key. Must rotate first — that demotes the current PRIMARY
 *       to SECONDARY, then you can retire it.</li>
 *   <li><b>Rotate is idempotent-ish.</b> Always creates a new key. Calling
 *       rotate twice = two rotations = one PRIMARY + one SECONDARY.</li>
 * </ul>
 *
 * <p>Note: this service still writes directly to {@link SigningKeyRepository}.
 * For the Vault backend (Feature 11) rotation should go through
 * {@code SigningKeyStore.rotate()} — deferred as a documented follow-up.
 */
@Service
@Profile("jdbc")
public class KeyRotationService {

    private static final Logger log = LoggerFactory.getLogger(KeyRotationService.class);

    private final SigningKeyRepository repo;
    private final AuditService audit;
    private final com.example.auth.metrics.AuthMetrics metrics;

    public KeyRotationService(SigningKeyRepository repo, AuditService audit,
                              com.example.auth.metrics.AuthMetrics metrics) {
        this.repo = repo;
        this.audit = audit;
        this.metrics = metrics;
    }

    public List<SigningKeyEntity> listAll() {
        return repo.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Generate a new PRIMARY key and demote the current PRIMARY to SECONDARY.
     * Runs in a single transaction — either both writes commit or neither.
     */
    @Transactional
    public SigningKeyEntity rotate() {
        SigningKeyEntity oldPrimary = repo.findFirstByStatus(Status.PRIMARY).orElse(null);

        SigningKeyEntity fresh = generate();
        fresh.setStatus(Status.PRIMARY);
        fresh.setActive(true);
        repo.save(fresh);

        if (oldPrimary != null) {
            oldPrimary.setStatus(Status.SECONDARY);
            // stays active=true → still in JWKS, still verifies existing tokens
            repo.save(oldPrimary);
            log.info("Rotated signing keys: new PRIMARY kid={}, demoted old PRIMARY kid={} → SECONDARY",
                    fresh.getKid(), oldPrimary.getKid());
        } else {
            log.info("Rotated signing keys: new PRIMARY kid={} (no previous PRIMARY existed)",
                    fresh.getKid());
        }
        audit.recordKey("ROTATE", fresh.getKid(), java.util.Map.of(
                "newPrimary", fresh.getKid(),
                "demoted", oldPrimary != null ? oldPrimary.getKid() : null));
        metrics.keyRotated();
        return fresh;
    }

    /**
     * Mark a SECONDARY key as RETIRED: removes it from JWKS, keeps the row for audit.
     * Refuses to retire PRIMARY (would leave the auth-server with no signing key).
     */
    @Transactional
    public void retire(String kid) {
        SigningKeyEntity key = repo.findById(kid)
                .orElseThrow(() -> new IllegalArgumentException("no such key: " + kid));
        if (key.getStatus() == Status.PRIMARY) {
            throw new IllegalStateException(
                    "cannot retire PRIMARY key — rotate first to promote a replacement");
        }
        if (key.getStatus() == Status.RETIRED) {
            return; // idempotent
        }
        key.setStatus(Status.RETIRED);
        key.setActive(false);
        repo.save(key);
        log.info("Retired signing key kid={}", kid);
        audit.recordKey("RETIRE", kid, java.util.Map.of("kid", kid));
    }

    // -------- helpers --------
    private SigningKeyEntity generate() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair kp = gen.generateKeyPair();
            SigningKeyEntity e = new SigningKeyEntity();
            e.setKid(UUID.randomUUID().toString());
            e.setPublicKey(toPem("PUBLIC KEY", kp.getPublic().getEncoded()));
            e.setPrivateKey(toPem("PRIVATE KEY", kp.getPrivate().getEncoded()));
            return e;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate RSA signing key", ex);
        }
    }

    private static String toPem(String label, byte[] der) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        return "-----BEGIN " + label + "-----\n" + base64 + "\n-----END " + label + "-----\n";
    }
}
