package com.example.bff.model;

public record Order(String id, String status, String amount, Long productId) {}
