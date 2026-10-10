package com.example.productservice.controller;

import com.example.productservice.model.ApiResponse;
import com.example.productservice.model.BaseProduct;
import com.example.productservice.model.Product;
import com.example.productservice.service.BaseProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/base-products")
@Slf4j
public class BaseProductController {

    private final BaseProductService service;
    public BaseProductController(BaseProductService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<List<BaseProduct>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaseProduct> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BaseProduct>> create(@RequestBody BaseProduct bp) {
        BaseProduct created = service.create(bp);
        //return ResponseEntity.created(URI.create("/api/base-products/" + created.getId())).body(created);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully", created,"/api/base-products/"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BaseProduct> update(@PathVariable Long id, @RequestBody BaseProduct bp) {
        return ResponseEntity.ok(service.update(id, bp));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}