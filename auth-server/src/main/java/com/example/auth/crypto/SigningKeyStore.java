package com.example.auth.crypto;

import com.nimbusds.jose.jwk.JWK;

import java.util.List;

/**
 * Where the JWT signing key lives — abstracted so we can swap backends.
 *
 * <p>Why abstract this? Two very different worlds:
 * <ul>
 *   <li><b>JPA backend:</b> private key stored as PEM string in MySQL. Fast, no
 *       external deps, but any DB read exposes it. Fine for dev/learning.</li>
 *   <li><b>Vault backend:</b> private key lives INSIDE HashiCorp Vault; the
 *       auth-server never has a copy. To sign, we send the JWT signing input to
 *       Vault, Vault returns the signature. Slower (network call) but a DB leak
 *       can't forge tokens.</li>
 * </ul>
 *
 * <p>Same interface, both worlds. Swapping is a properties flip:
 * {@code features.kms-keys.backend=jpa} vs {@code =vault}. Zero code change.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link JpaSigningKeyStore} — Feature 3 default</li>
 *   <li>{@link VaultSigningKeyStore} — Feature 11 opt-in</li>
 * </ul>
 */
public interface SigningKeyStore {

    /**
     * Every key currently exposed in {@code /oauth2/jwks} — i.e. everything a
     * client uses to VERIFY tokens.
     *
     * <p>PRIMARY key returns with {@code key_ops=[sign, verify]} so Nimbus's
     * default selector picks it for signing. SECONDARY keys return with
     * {@code [verify]} only — they can validate old tokens but never sign new
     * ones. RETIRED keys are excluded entirely.
     */
    List<JWK> activeJwks();

    /**
     * The id (kid) of the current PRIMARY key. Goes into the JWT header so
     * verifiers know which public key to use.
     *
     * <p>Format:
     * <ul>
     *   <li>JPA:   {@code <uuid>}</li>
     *   <li>Vault: {@code vault:auth-server:v1}</li>
     * </ul>
     */
    String primaryKid();

    /**
     * Actually sign some bytes using the PRIMARY key.
     *
     * <p>Input = the JWT signing input (header + "." + payload, both base64url).
     * Output = raw signature bytes. The caller (see {@link RemoteSigningJwtEncoder})
     * base64url-encodes the signature and glues the whole JWT together.
     *
     * <p>JPA impl calls {@code Signature.getInstance("SHA256withRSA")} locally.
     * Vault impl POSTs to {@code /v1/transit/sign/auth-server} — the key never
     * leaves Vault.
     */
    byte[] sign(byte[] input);

    /**
     * Promote a brand-new key to PRIMARY. The old PRIMARY becomes SECONDARY
     * (still verifies existing tokens, no longer signs new ones).
     *
     * <p>Zero-downtime: no user gets logged out because their JWT still has a
     * kid that's in the JWKS response.
     */
    void rotate();

    /**
     * Remove a SECONDARY key from JWKS. Any tokens still signed by it stop
     * verifying immediately.
     *
     * <p>You should only do this after the retire-after-days grace period (see
     * {@code features.key-rotation.auto-retire-after-days}) so no active tokens
     * die.
     *
     * <p>Cannot retire PRIMARY — that would leave the auth-server with no
     * signing key. Rotate first, then retire the demoted one.
     */
    void retire(String kid);
}
