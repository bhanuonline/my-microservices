package com.example.productservice.controller;

import com.example.productservice.model.ProductVariant;
import com.example.productservice.service.ProductVariantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/variants")
@Slf4j
public class ProductVariantController {

    private final ProductVariantService service;
    public ProductVariantController(ProductVariantService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<List<ProductVariant>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductVariant> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<ProductVariant> create(@RequestBody ProductVariant variant) {
        ProductVariant saved = service.create(variant);
        return ResponseEntity.created(URI.create("/api/variants/" + saved.getId())).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductVariant> update(@PathVariable Long id, @RequestBody ProductVariant v) {
        return ResponseEntity.ok(service.update(id, v));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}