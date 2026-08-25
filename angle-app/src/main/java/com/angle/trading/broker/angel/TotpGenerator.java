package com.angle.trading.broker.angel;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.time.Instant;

/**
 * Generates a 6-digit TOTP (Time-based One-Time Password) from a
 * base32-encoded secret. Angel One's SmartAPI login needs a fresh
 * TOTP alongside the password.
 *
 * Algorithm: RFC 6238 with HMAC-SHA1, 30-second window, 6 digits.
 * Same math Google Authenticator uses.
 */
public final class TotpGenerator {

    private static final int TIME_STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private TotpGenerator() {}

    public static String generate(String base32Secret) {
        try {
            long counter = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
            byte[] key = decodeBase32(base32Secret);
            byte[] data = ByteBuffer.allocate(8).putLong(counter).array();

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format("%0" + DIGITS + "d", otp);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate TOTP", e);
        }
    }

    /** Minimal Base32 decoder (RFC 4648). Ignores spaces and '=' padding. */
    private static byte[] decodeBase32(String input) {
        String cleaned = input.replaceAll("[\\s=]", "").toUpperCase();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (char c : cleaned.toCharArray()) {
            int idx = BASE32_ALPHABET.indexOf(c);
            if (idx < 0) {
                throw new IllegalArgumentException("Invalid base32 char: " + c);
            }
            buffer = (buffer << 5) | idx;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                out.write((buffer >> bitsLeft) & 0xFF);
            }
        }
        return out.toByteArray();
    }
}
