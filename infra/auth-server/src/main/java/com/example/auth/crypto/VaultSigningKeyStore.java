package com.example.auth.crypto;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyOperation;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.VaultResponse;

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/**
 * SigningKeyStore that keeps the private key inside HashiCorp Vault. Auth-server
 * NEVER sees the private key — every signature is a remote call to Vault.
 *
 * <p>The "transit engine" is Vault's crypto-as-a-service module. You give it data,
 * it signs/encrypts, you never touch the key. Perfect for JWT signing.
 *
 * <p>How each method maps to Vault:
 * <table>
 *   <tr><th>SigningKeyStore method</th><th>Vault HTTP call</th></tr>
 *   <tr><td>{@link #activeJwks()}</td>
 *       <td>{@code GET  /v1/transit/keys/auth-server} — returns metadata for ALL
 *           versions of the key with their public-key PEMs</td></tr>
 *   <tr><td>{@link #sign(byte[])}</td>
 *       <td>{@code POST /v1/transit/sign/auth-server/sha2-256} — send the JWT
 *           signing input, get back the signature</td></tr>
 *   <tr><td>{@link #rotate()}</td>
 *       <td>{@code POST /v1/transit/keys/auth-server/rotate} — Vault creates a new
 *           version; old versions still work for verification</td></tr>
 *   <tr><td>{@link #retire(String)}</td>
 *       <td>{@code POST /v1/transit/keys/auth-server/config} with a raised
 *           {@code min_decryption_version} — that version can no longer verify</td></tr>
 * </table>
 *
 * <p>Kid convention: {@code vault:auth-server:v1} (contains the Vault key version)
 * so JWT verifiers know exactly which version of the Vault key signed the token.
 *
 * <p>Bootstrap: constructor ensures the transit engine is mounted and the key
 * exists. Idempotent — safe to call on every restart.
 */
public class VaultSigningKeyStore implements SigningKeyStore {

    private static final Logger log = LoggerFactory.getLogger(VaultSigningKeyStore.class);

    private final VaultOperations vault;
    private final String keyName;
    private final String transitPath;
    private final ObservationRegistry observationRegistry;

    public VaultSigningKeyStore(VaultOperations vault, String transitPath, String keyName,
                                ObservationRegistry observationRegistry) {
        this.vault = vault;
        this.transitPath = transitPath;
        this.keyName = keyName;
        this.observationRegistry = observationRegistry;
        bootstrap();
    }

    /** Ensure transit engine + key exist. Idempotent — safe on every boot. */
    private void bootstrap() {
        try {
            // Enable transit engine if not already mounted.
            List<String> mounts = List.of(); // we skip listing; enable is idempotent-ish
            try {
                vault.write("sys/mounts/" + transitPath, Map.of("type", "transit"));
                log.info("Vault: enabled transit engine at {}", transitPath);
            } catch (Exception e) {
                // Already mounted → 400 "path is already in use" — ignore.
                log.debug("Vault: transit already mounted at {}", transitPath);
            }
            // Create the signing key if it doesn't exist.
            VaultResponse existing = vault.read(transitPath + "/keys/" + keyName);
            if (existing == null || existing.getData() == null) {
                vault.write(transitPath + "/keys/" + keyName, Map.of("type", "rsa-2048"));
                log.info("Vault: created transit key {} (type=rsa-2048)", keyName);
            } else {
                log.info("Vault: transit key {} already exists (latest_version={})",
                        keyName, existing.getData().get("latest_version"));
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Vault bootstrap failed — is Vault running at "
                    + " configured URI, and is transit engine reachable?", ex);
        }
    }

    @Override
    public List<JWK> activeJwks() {
        VaultResponse resp = vault.read(transitPath + "/keys/" + keyName);
        if (resp == null || resp.getData() == null) return List.of();
        Object versionsObj = resp.getData().get("keys");
        Object latestObj = resp.getData().get("latest_version");
        if (!(versionsObj instanceof Map<?, ?> versions) || !(latestObj instanceof Number latestNum)) {
            return List.of();
        }
        int latest = latestNum.intValue();
        List<JWK> out = new ArrayList<>();
        for (Map.Entry<?, ?> entry : versions.entrySet()) {
            int version = Integer.parseInt(entry.getKey().toString());
            Map<?, ?> versionMeta = (Map<?, ?>) entry.getValue();
            String publicKeyPem = (String) versionMeta.get("public_key");
            if (publicKeyPem == null) continue;
            String kid = kidFor(version);
            RSAKey.Builder b = new RSAKey.Builder(parsePublic(publicKeyPem))
                    .keyID(kid)
                    .keyUse(KeyUse.SIGNATURE);
            if (version == latest) {
                b.keyOperations(Set.of(KeyOperation.SIGN, KeyOperation.VERIFY));
            } else {
                b.keyOperations(Set.of(KeyOperation.VERIFY));
            }
            out.add(b.build());
        }
        return out;
    }

    @Override
    public String primaryKid() {
        VaultResponse resp = vault.read(transitPath + "/keys/" + keyName);
        int latest = ((Number) Objects.requireNonNull(resp).getData().get("latest_version")).intValue();
        return kidFor(latest);
    }

    /**
     * Vault's transit sign endpoint expects the RAW input base64-encoded (not the hash).
     * It hashes internally using the algorithm we specify. We use SHA-256 for RS256.
     *
     * signature_algorithm=pkcs1v15 forces PKCS#1 v1.5 padding to match JWS RS256 exactly
     * (Vault's default for RSA is PSS which would produce PS256-compatible signatures).
     */
    @Override
    public byte[] sign(byte[] input) {
        // FEATURE 9: wrap in an Observation so the Vault sign call becomes a distinct
        // span in Zipkin under the parent /oauth2/token span. Makes latency visible
        // — you see "vault.sign took 42ms of the 120ms token issuance".
        return Observation.createNotStarted("vault.sign", observationRegistry)
                .lowCardinalityKeyValue("key.name", keyName)
                .observe(() -> {
                    String inputB64 = Base64.getEncoder().encodeToString(input);
                    VaultResponse resp = vault.write(
                            transitPath + "/sign/" + keyName + "/sha2-256",
                            Map.of("input", inputB64, "signature_algorithm", "pkcs1v15"));
                    if (resp == null || resp.getData() == null) {
                        throw new IllegalStateException("Vault sign returned no data");
                    }
                    String signature = (String) resp.getData().get("signature");
                    if (signature == null) {
                        throw new IllegalStateException("Vault sign response missing 'signature'");
                    }
                    // Format: "vault:v{version}:base64signature"
                    int lastColon = signature.lastIndexOf(':');
                    return Base64.getDecoder().decode(signature.substring(lastColon + 1));
                });
    }

    @Override
    public void rotate() {
        vault.write(transitPath + "/keys/" + keyName + "/rotate", null);
        VaultResponse resp = vault.read(transitPath + "/keys/" + keyName);
        int latest = ((Number) Objects.requireNonNull(resp).getData().get("latest_version")).intValue();
        log.info("Vault: rotated key {} → new latest_version={}", keyName, latest);
    }

    @Override
    public void retire(String kid) {
        // kid format: vault:{keyName}:v{version}
        int version = Integer.parseInt(kid.substring(kid.lastIndexOf('v') + 1));
        // Raise min_decryption_version so this version can no longer verify.
        // We + 1 because min = version means version is INCLUDED, we want it EXCLUDED.
        vault.write(transitPath + "/keys/" + keyName + "/config",
                Map.of("min_decryption_version", version + 1));
        log.info("Vault: retired version {} (min_decryption_version now {})", version, version + 1);
    }

    // -- helpers --

    private String kidFor(int version) {
        return "vault:" + keyName + ":v" + version;
    }

    private static RSAPublicKey parsePublic(String pem) {
        String stripped = pem
                .replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(stripped)));
        } catch (Exception ex) {
            throw new IllegalStateException("Bad Vault public key PEM", ex);
        }
    }

    // Reserved for future use — MessageDigest exposed so a caller can pre-hash if we ever
    // switch to Vault's "hash+sign" endpoint. Present so imports don't warn.
    @SuppressWarnings("unused")
    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
