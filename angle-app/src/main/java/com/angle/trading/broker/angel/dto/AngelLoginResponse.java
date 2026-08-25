package com.angle.trading.broker.angel.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Angel SmartAPI login response. Only fields we actually consume are declared;
 * unknown fields are ignored (Angel may add new ones without breaking us).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AngelLoginResponse(
        boolean status,
        String message,
        String errorcode,
        Data data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
            String jwtToken,
            String refreshToken,
            String feedToken
    ) {}
}
