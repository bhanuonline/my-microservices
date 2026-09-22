package com.example.userservice.dto;

// Notice: no `password` field. This is the whole point of a DTO — hide internals.
public record UserResponse(
        Long id,
        String name,
        String email
) {}
