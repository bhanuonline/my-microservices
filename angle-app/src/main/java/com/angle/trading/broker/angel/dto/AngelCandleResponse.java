package com.angle.trading.broker.angel.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Angel SmartAPI candle response.
 *
 * Happy path — data is a list-of-lists where each inner list is:
 *   [timestamp, open, high, low, close, volume]
 * Example: ["2024-01-01T09:15:00+05:30", 21750.5, 21780.2, 21740.1, 21770.8, 12345]
 *
 * Angel error responses sometimes return "data": "" (empty string) or null,
 * which breaks strict typing. We accept a raw JsonNode and normalize in {@link #candles()}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AngelCandleResponse(
        boolean status,
        String message,
        String errorcode,
        JsonNode data
) {
    /** Safe accessor — returns [] when data is null / empty string / not an array. */
    public List<List<Object>> candles() {
        List<List<Object>> out = new ArrayList<>();
        if (data == null || !data.isArray()) return out;
        for (JsonNode row : data) {
            if (!row.isArray()) continue;
            List<Object> cols = new ArrayList<>(row.size());
            for (JsonNode cell : row) {
                if (cell.isTextual())      cols.add(cell.asText());
                else if (cell.isNumber())  cols.add(cell.numberValue());
                else if (cell.isNull())    cols.add(null);
                else                        cols.add(cell.toString());
            }
            out.add(cols);
        }
        return out;
    }
}
