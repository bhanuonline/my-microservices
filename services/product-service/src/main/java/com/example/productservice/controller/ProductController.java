package com.example.productservice.controller;

import com.example.productservice.dto.CreateProductRequest;
import com.example.productservice.dto.ProductMapper;
import com.example.productservice.dto.ProductResponse;
import com.example.productservice.exception.ProductNotFoundException;
import com.example.productservice.model.Product;
import com.example.productservice.service.ProductService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@Slf4j
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest req) {
        log.info("Creating product {}", req.name());
        Product saved = productService.saveProduct(ProductMapper.toEntity(req));
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductMapper.toResponse(saved));
    }

    @GetMapping
    public List<ProductResponse> getAll() {
        return productService.getAllProducts().stream()
                .map(ProductMapper::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable Long id) {
        Product p = productService.getProductById(id);
        if (p == null) {
            throw new ProductNotFoundException(id);
        }
        return ProductMapper.toResponse(p);
    }

    // Availability endpoint — order-service's Feign client hits this.
    // Returns plain string ("AVAILABLE" / "NOT_AVAILABLE") for backward compatibility.
    @GetMapping("/{id}/availability")
    public ResponseEntity<String> checkAvailability(@PathVariable("id") Long productId) {
        log.debug("Checking availability for product ID: {}", productId);
        Product p = productService.getProductById(productId);
        if (p != null && p.isActive() && p.getQuantityInStock() > 0) {
            return ResponseEntity.ok("AVAILABLE");
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("NOT_AVAILABLE");
    }
}
