package com.example.auth.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Custom {@link JwtEncoder} used ONLY when the Vault backend is active.
 *
 * <p>The problem this solves:
 * Spring's default {@code NimbusJwtEncoder} wants an {@code RSAPrivateKey} object
 * in memory so it can call {@code Signature.sign()} locally. That's fundamentally
 * incompatible with Vault — the whole point of Vault is the private key NEVER
 * leaves it. So we need to build the JWT ourselves and delegate ONLY the signature
 * step to the store.
 *
 * <p>What a JWT looks like on the wire:
 * <pre>
 *   {base64url(header)}.{base64url(payload)}.{base64url(signature)}
 *   └──────────┬──────────┘└──────────┬──────────┘└──────────┬──────────┘
 *              1                       2                       3
 * </pre>
 *
 * <p>What this encoder does:
 * <ol>
 *   <li>Build the JOSE header: {@code {kid: <primary>, alg: RS256, typ: JWT}}</li>
 *   <li>Serialize it + the claims to JSON, then base64url-encode both</li>
 *   <li>Concatenate: {@code signingInput = headerB64 + "." + payloadB64}</li>
 *   <li>Ask the store to sign: {@code sig = store.sign(signingInput.bytes)}
 *       — for Vault, this is an HTTP call; the private key stays remote</li>
 *   <li>Assemble: {@code jwt = signingInput + "." + base64url(sig)}</li>
 * </ol>
 *
 * <p>Wired via {@code @ConditionalOnProperty(features.kms-keys.backend=vault)}
 * — only registered on the Vault backend. JPA backend keeps Spring's default
 * encoder.
 */
public class RemoteSigningJwtEncoder implements JwtEncoder {

    private static final Logger log = LoggerFactory.getLogger(RemoteSigningJwtEncoder.class);

    private final SigningKeyStore store;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Base64.Encoder b64url = Base64.getUrlEncoder().withoutPadding();

    public RemoteSigningJwtEncoder(SigningKeyStore store) {
        this.store = store;
    }

    @Override
    public Jwt encode(JwtEncoderParameters parameters) throws JwtEncodingException {
        JwtClaimsSet claims = parameters.getClaims();
        String kid = store.primaryKid();

        // ---- build the JOSE header ----
        Map<String, Object> headerMap = new LinkedHashMap<>();
        headerMap.put("kid", kid);
        headerMap.put("alg", SignatureAlgorithm.RS256.getName());
        // If caller passed headers, respect them (rare).
        if (parameters.getJwsHeader() != null) {
            headerMap.putAll(parameters.getJwsHeader().getHeaders());
            headerMap.putIfAbsent("kid", kid);
            headerMap.putIfAbsent("alg", SignatureAlgorithm.RS256.getName());
        }

        // ---- build the payload ----
        Map<String, Object> claimMap = claims.getClaims();

        try {
            String headerJson  = mapper.writeValueAsString(headerMap);
            String payloadJson = mapper.writeValueAsString(sanitizeForJson(claimMap));

            String headerB64  = b64url.encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
            String payloadB64 = b64url.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signingInput = headerB64 + "." + payloadB64;

            byte[] sig = store.sign(signingInput.getBytes(StandardCharsets.UTF_8));
            String signatureB64 = b64url.encodeToString(sig);

            String tokenValue = signingInput + "." + signatureB64;

            return Jwt.withTokenValue(tokenValue)
                    .headers(h -> h.putAll(headerMap))
                    .claims(c -> c.putAll(claimMap))
                    .issuedAt(claims.getIssuedAt())
                    .expiresAt(claims.getExpiresAt())
                    .subject(claims.getSubject())
                    .audience(claims.getAudience())
                    .build();
        } catch (Exception ex) {
            log.error("RemoteSigningJwtEncoder failed", ex);
            throw new JwtEncodingException("Failed to encode JWT via remote signer", ex);
        }
    }

    /**
     * Nimbus emits Instant → epoch seconds; we do the same so tokens verify identically.
     * Also filter null-valued entries to keep the payload lean.
     */
    private static Map<String, Object> sanitizeForJson(Map<String, Object> in) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : in.entrySet()) {
            Object v = e.getValue();
            if (v == null) continue;
            if (v instanceof java.time.Instant t) {
                out.put(e.getKey(), t.getEpochSecond());
            } else {
                out.put(e.getKey(), v);
            }
        }
        return out;
    }
}
