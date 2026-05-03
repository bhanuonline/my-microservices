package com.fastapi.controller;

import com.fastapi.model.Product;
import com.fastapi.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // ─── HOT PATH ─────────────────────────────────────────────────────────
    // This is what gets hit 10,000+ times/min — everything is optimized for it

    @GetMapping("/{id}")
    public ResponseEntity<Product> getById(@PathVariable Long id) {
        // Virtual thread handles this — no blocking concern
        // Redis cache check happens in ProductService before hitting DB
        return ResponseEntity.ok(productService.getById(id));
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<Product>> getByCategory(@PathVariable String category) {
        return ResponseEntity.ok(productService.getByCategory(category));
    }

    // ─── COLD PATHS ───────────────────────────────────────────────────────
    // Called infrequently — less critical for latency

    @PostMapping
    public ResponseEntity<Product> create(@RequestBody Product product) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(productService.create(product));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Product> update(@PathVariable Long id, @RequestBody Product product) {
        product.setId(id);
        return ResponseEntity.ok(productService.update(product));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
