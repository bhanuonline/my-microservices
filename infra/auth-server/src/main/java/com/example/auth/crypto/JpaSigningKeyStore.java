package com.example.auth.crypto;

import com.example.auth.entity.SigningKeyEntity;
import com.example.auth.entity.SigningKeyEntity.Status;
import com.example.auth.repository.SigningKeyRepository;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyOperation;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * SigningKeyStore that keeps the private key in MySQL — the simple, no-external-deps backend.
 *
 * <p>How it works:
 * <ul>
 *   <li>Each key is a row in the {@code signing_key} table with columns for the
 *       kid, public key PEM, private key PEM, status (PRIMARY/SECONDARY/RETIRED),
 *       and active flag.</li>
 *   <li>{@link #sign(byte[])} loads the PRIMARY row, parses the PEM into an
 *       {@code RSAPrivateKey}, and calls {@code Signature.getInstance("SHA256withRSA")}
 *       to compute the signature — all in-JVM, no network.</li>
 *   <li>{@link #rotate()} does two saves in one transaction: insert the new PRIMARY,
 *       update the old PRIMARY to SECONDARY.</li>
 * </ul>
 *
 * <p>Security warning: anyone with SELECT on {@code signing_key} can read the
 * private key PEM and forge tokens. That's why Feature 11 exists —
 * {@link VaultSigningKeyStore} keeps the key OUT of the DB entirely.
 */
public class JpaSigningKeyStore implements SigningKeyStore {

    private static final Logger log = LoggerFactory.getLogger(JpaSigningKeyStore.class);

    private final SigningKeyRepository repo;

    public JpaSigningKeyStore(SigningKeyRepository repo) {
        this.repo = repo;
        // Bootstrap: ensure at least one PRIMARY exists.
        if (repo.findFirstByStatus(Status.PRIMARY).isEmpty()) {
            bootstrapPrimary();
        }
    }

    @Override
    public List<JWK> activeJwks() {
        return repo.findAllByActiveTrueOrderByCreatedAtDesc().stream()
                .map(JpaSigningKeyStore::toJwk)
                .toList();
    }

    @Override
    public String primaryKid() {
        return repo.findFirstByStatus(Status.PRIMARY)
                .orElseThrow(() -> new IllegalStateException("no PRIMARY signing key"))
                .getKid();
    }

    @Override
    public byte[] sign(byte[] input) {
        SigningKeyEntity primary = repo.findFirstByStatus(Status.PRIMARY)
                .orElseThrow(() -> new IllegalStateException("no PRIMARY signing key"));
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(parsePrivate(primary.getPrivateKey()));
            sig.update(input);
            return sig.sign();
        } catch (Exception ex) {
            throw new IllegalStateException("JPA sign failed", ex);
        }
    }

    @Override
    @Transactional
    public void rotate() {
        SigningKeyEntity oldPrimary = repo.findFirstByStatus(Status.PRIMARY).orElse(null);
        SigningKeyEntity fresh = generate();
        fresh.setStatus(Status.PRIMARY);
        fresh.setActive(true);
        repo.save(fresh);
        if (oldPrimary != null) {
            oldPrimary.setStatus(Status.SECONDARY);
            repo.save(oldPrimary);
        }
        log.info("Rotated JPA signing keys: new PRIMARY kid={}", fresh.getKid());
    }

    @Override
    @Transactional
    public void retire(String kid) {
        SigningKeyEntity key = repo.findById(kid)
                .orElseThrow(() -> new IllegalArgumentException("no such key: " + kid));
        if (key.getStatus() == Status.PRIMARY) {
            throw new IllegalStateException("cannot retire PRIMARY key");
        }
        key.setStatus(Status.RETIRED);
        key.setActive(false);
        repo.save(key);
    }

    // ---- helpers ----

    @Transactional
    protected void bootstrapPrimary() {
        SigningKeyEntity fresh = generate();
        fresh.setStatus(Status.PRIMARY);
        fresh.setActive(true);
        repo.save(fresh);
        log.info("Bootstrapped JPA PRIMARY signing key (kid={})", fresh.getKid());
    }

    private static SigningKeyEntity generate() {
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

    private static JWK toJwk(SigningKeyEntity e) {
        RSAPublicKey publicKey = parsePublic(e.getPublicKey());
        RSAPrivateKey privateKey = parsePrivate(e.getPrivateKey());
        RSAKey.Builder b = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(e.getKid())
                .keyUse(KeyUse.SIGNATURE);
        if (e.getStatus() == Status.PRIMARY) {
            b.keyOperations(Set.of(KeyOperation.SIGN, KeyOperation.VERIFY));
        } else {
            b.keyOperations(Set.of(KeyOperation.VERIFY));
        }
        return b.build();
    }

    private static String toPem(String label, byte[] der) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        return "-----BEGIN " + label + "-----\n" + base64 + "\n-----END " + label + "-----\n";
    }

    private static byte[] fromPem(String pem) {
        String stripped = pem
                .replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(stripped);
    }

    private static RSAPublicKey parsePublic(String pem) {
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(fromPem(pem)));
        } catch (Exception ex) {
            throw new IllegalStateException("Bad RSA public key PEM", ex);
        }
    }

    private static RSAPrivateKey parsePrivate(String pem) {
        try {
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(fromPem(pem)));
        } catch (Exception ex) {
            throw new IllegalStateException("Bad RSA private key PEM", ex);
        }
    }
}
