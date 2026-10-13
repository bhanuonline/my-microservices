package com.example.orderservice.web;

import java.time.Instant;

// Envelope that wraps every successful API response.
// Client always receives a consistent shape:
// {
//   "data":          { ... actual payload ... },
//   "status":        200,
//   "timestamp":     "2026-10-12T10:30:00Z",
//   "correlationId": "abc-123"
// }
public record StandardResponse<T>(
        T data,
        int status,
        Instant timestamp,
        String correlationId
) {
    public static <T> StandardResponse<T> of(T data, int status, String correlationId) {
        return new StandardResponse<>(data, status, Instant.now(), correlationId);
    }
}
