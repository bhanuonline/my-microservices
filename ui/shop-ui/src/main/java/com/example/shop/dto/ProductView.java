package com.example.shop.dto;

/**
 * Flat view-model mapped from both product-query's ProductDoc (ES hit) and
 * product-service's Product (SQL row). The two shapes differ on numeric
 * types + id (String vs Long) — this normalisation lets Thymeleaf not care.
 */
public record ProductView(
        String id,
        String name,
        String description,
        Double price,
        Integer stock,
        String category,
        String brand
) {
    public boolean inStock() { return stock != null && stock > 0; }
}
