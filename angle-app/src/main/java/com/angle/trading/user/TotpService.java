package com.angle.trading.user;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import dev.samstevens.totp.util.Utils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Wraps the samstevens TOTP library behind a tiny app-shaped API:
 *
 *   generateSecret()           — new 160-bit base32 secret for enrolment
 *   generateQrDataUri(user, s) — PNG QR code as a `data:image/png;base64,...` URL
 *                                 that the enrolment page can drop straight into
 *                                 an <img src>
 *   verify(secret, code)       — true if the 6-digit code is current (±1 step)
 *
 * The otpauth URI uses the hard-coded issuer "AngleApp" so authenticator apps
 * group all codes under one logo. Account label = username.
 *
 * Period = 30s, digits = 6, algorithm = SHA1 — the universal defaults every
 * authenticator app supports out of the box. Changing these would break
 * users who've already enrolled.
 */
@Slf4j
@Service
public class TotpService {

    private static final String ISSUER = "AngleApp";

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator();
    private final TimeProvider timeProvider = new SystemTimeProvider();
    private final CodeVerifier verifier;
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();

    public TotpService() {
        DefaultCodeVerifier v = new DefaultCodeVerifier(codeGenerator, timeProvider);
        // Accept the previous AND current 30s window — covers phone/server clock drift.
        v.setAllowedTimePeriodDiscrepancy(1);
        this.verifier = v;
    }

    /** Fresh base32 secret for a new enrolment. */
    public String generateSecret() {
        return secretGenerator.generate();
    }

    /** Build the otpauth URI that goes into the QR code (also useful for manual entry). */
    public String otpAuthUri(String username, String secret) {
        return new QrData.Builder()
                .label(username)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build()
                .getUri();
    }

    /**
     * Produces a self-contained data URL for an <img>:
     *   data:image/png;base64,iVBORw0KGgo...
     * Means we never serve the QR as a separate endpoint — no cache concerns,
     * no stale-secret leaks.
     */
    public String generateQrDataUri(String username, String secret) {
        QrData data = new QrData.Builder()
                .label(username)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();
        try {
            byte[] png = qrGenerator.generate(data);
            return Utils.getDataUriForImage(png, qrGenerator.getImageMimeType());
        } catch (QrGenerationException e) {
            log.error("QR generation failed for {}: {}", username, e.getMessage());
            throw new IllegalStateException("Could not generate QR code", e);
        }
    }

    /** 6-digit code verification with ±1 time-step tolerance. */
    public boolean verify(String secret, String code) {
        if (secret == null || code == null) return false;
        String trimmed = code.trim();
        if (trimmed.length() != 6 || !trimmed.chars().allMatch(Character::isDigit)) return false;
        return verifier.isValidCode(secret, trimmed);
    }
}
