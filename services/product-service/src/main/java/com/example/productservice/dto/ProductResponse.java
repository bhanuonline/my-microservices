package com.example.productservice.dto;

// Public API view of a Product. Excludes audit fields (createdBy/updatedBy/etc)
// — those are internal implementation detail.
public record ProductResponse(
        Long id,
        String name,
        String description,
        double price,
        String sku,
        String category,
        int quantityInStock,
        String brand,
        boolean active
) {}
