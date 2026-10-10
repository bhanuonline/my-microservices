package com.example.productservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateProductRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @PositiveOrZero double price,
        @Size(max = 50) String sku,
        @Size(max = 100) String category,
        @PositiveOrZero int quantityInStock,
        @Size(max = 100) String brand
) {}
