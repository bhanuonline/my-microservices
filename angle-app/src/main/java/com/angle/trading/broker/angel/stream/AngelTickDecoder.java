package com.angle.trading.broker.angel.stream;

import com.angle.trading.broker.angel.stream.model.Tick;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;

/**
 * Decodes Angel SmartStream binary tick messages.
 *
 * Layout (Mode 1 — LTP, 51 bytes):
 *   Byte  0        subscription mode (1=LTP)
 *   Byte  1        exchange type (1=NSE_CM, 2=NSE_FO, ...)
 *   Bytes 2..26    symbol token — ASCII, null-padded to 25 bytes
 *   Bytes 27..34   sequence number (int64 little-endian)
 *   Bytes 35..42   exchange timestamp millis (int64 little-endian)
 *   Bytes 43..50   LTP × 100 (int64 little-endian)
 *
 * Mode 2 (Quote) — 379 bytes; Mode 3 (SnapQuote) — 379 bytes with more fields.
 * Phase 1 only decodes Mode 1. Longer payloads are truncated safely.
 *
 * All numbers are little-endian per Angel's spec.
 */
public final class AngelTickDecoder {

    private static final int MIN_LEN = 51;

    private AngelTickDecoder() {}

    /** Decode one binary WebSocket frame. Returns null on malformed input. */
    public static Tick decode(byte[] bytes) {
        if (bytes == null || bytes.length < MIN_LEN) return null;

        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        int subMode  = buf.get(0) & 0xFF;
        int exchange = buf.get(1) & 0xFF;

        // Token — trim trailing null bytes
        byte[] tokenBytes = new byte[25];
        buf.position(2);
        buf.get(tokenBytes);
        String token = new String(tokenBytes).replace("\0", "").trim();

        // long fields (little-endian)
        long seq       = buf.getLong(27);
        long tsMillis  = buf.getLong(35);
        long ltpScaled = buf.getLong(43);

        // Angel scales LTP by 100 (paise → rupees)
        BigDecimal ltp = BigDecimal.valueOf(ltpScaled)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        return new Tick(
                exchange,
                token,
                ltp,
                Instant.ofEpochMilli(tsMillis),
                Instant.now()
        );
    }
}
