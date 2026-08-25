package com.angle.trading.broker.angel.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Angel SmartAPI candle response.
 *
 * The "data" field is a list-of-lists where each inner list is:
 * [timestamp, open, high, low, close, volume]
 *
 * Example: ["2024-01-01T09:15:00+05:30", 21750.5, 21780.2, 21740.1, 21770.8, 12345]
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AngelCandleResponse(
        boolean status,
        String message,
        String errorcode,
        List<List<Object>> data
) {}
