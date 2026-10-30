package com.angle.trading.dto;

import com.angle.trading.broker.model.Interval;

import java.time.LocalDate;

/**
 * Per-symbol configuration inside a bulk analysis request.
 * All fields except {@code symbol} are optional — defaults apply when null.
 *
 * @param symbol       required — Angel scrip symbol e.g. "HDFCBANK-EQ"
 * @param interval     highest TF in the MTF stack (default: ONE_DAY)
 * @param baseInterval smallest TF — controls live refresh frequency (default: from config)
 * @param days         calendar days of warmup history (default: 60)
 * @param asOf         historical date — used when top-level live=false
 * @param marketOpen   override session open time "HH:mm" e.g. "09:00"
 * @param marketClose  override session close time "HH:mm" e.g. "23:30"
 */
public record SymbolRequestDto(
        String    symbol,
        Interval  interval,
        Interval  baseInterval,
        Integer   days,
        LocalDate asOf,
        String    marketOpen,
        String    marketClose
) {}
