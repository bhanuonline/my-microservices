package com.example.productservice.controller;

import com.example.productservice.model.ApiResponse;
import com.example.productservice.model.Product;
import com.example.productservice.repository.ProductRepository;
import com.example.productservice.service.ProductService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
@Slf4j
public class ProductController {

    @Autowired
    private ProductService productService;

    @PostMapping
    public ResponseEntity<ApiResponse<Product>> addProduct(@Valid @RequestBody Product product) {
        log.info("Creating product {}", product.getName());
        Product created = productService.saveProduct(product);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully", created,""));
    }

    @PostMapping("/bulk")
    public List<Product> addMultipleProducts(@RequestBody List<Product> products) {
        log.debug("Received bulk insert request for {} products", products.size());
        return productService.saveAllProducts(products);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Product>>> getAll() {
        log.debug("Fetching all products");
        return ResponseEntity.ok(ApiResponse.success("All products retrieved", productService.getAllProducts(),""));
    }

    @GetMapping("/{id}/availability")
    public ResponseEntity<String> checkProductAvailability(@PathVariable("id") Long productId) {
        // In a real system -> Check from DB
        log.debug("Checking availability for product ID: {}", productId);
        if (productId == 1L) {
            return ResponseEntity.ok("AVAILABLE");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("NOT_AVAILABLE");
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Product>> getById(@PathVariable Long id) {
        log.debug("Fetching product {}", id);
        Product product = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.success("Product fetched successfully", product,""));
    }
}