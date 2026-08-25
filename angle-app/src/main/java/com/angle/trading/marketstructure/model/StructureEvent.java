package com.angle.trading.marketstructure.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A BOS or CHoCH event.
 *
 *   index         — candle whose CLOSE triggered the break
 *   timestamp     — that candle's timestamp
 *   type          — BOS (continuation) or CHOCH (reversal)
 *   direction     — BULLISH (broke above a swing high) or BEARISH (broke below a swing low)
 *   brokenLevel   — the swing price that was taken out
 *   brokenSwingIx — index of the swing candle that got broken
 */
public record StructureEvent(
        int index,
        Instant timestamp,
        StructureEventType type,
        Direction direction,
        BigDecimal brokenLevel,
        int brokenSwingIx
) {}
