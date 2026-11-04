package com.angle.trading.dto;

import java.math.BigDecimal;

/**
 * Live price data from Angel's LTP endpoint.
 * Always reflects the current market price — never historical.
 * Null in HISTORICAL analysis mode.
 */
public record PriceDto(
        BigDecimal ltp,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal previousClose,
        BigDecimal change,
        BigDecimal changePercent,
        /** Open Interest — populated for F&O only (futures/options). Null for equity/indices. */
        OpenInterestDto openInterest
) {}
