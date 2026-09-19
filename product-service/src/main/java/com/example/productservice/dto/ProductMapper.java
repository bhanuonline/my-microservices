package com.example.productservice.dto;

import com.example.productservice.model.Product;

public final class ProductMapper {

    private ProductMapper() {}

    public static Product toEntity(CreateProductRequest req) {
        Product p = new Product();
        p.setName(req.name());
        p.setDescription(req.description());
        p.setPrice(req.price());
        p.setSku(req.sku());
        p.setCategory(req.category());
        p.setQuantityInStock(req.quantityInStock());
        p.setBrand(req.brand());
        return p;
    }

    public static ProductResponse toResponse(Product p) {
        return new ProductResponse(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getPrice(),
                p.getSku(),
                p.getCategory(),
                p.getQuantityInStock(),
                p.getBrand(),
                p.isActive()
        );
    }
}
