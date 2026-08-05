package com.angle.trading.dto;

public record ProfileDto(
        String name,
        String email,
        String memberSince,
        String kycStatus,
        String tier
) {}
