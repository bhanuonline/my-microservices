package com.example.productservice.controller;

import com.example.productservice.model.ApiResponse;
import com.example.productservice.model.Category;
import com.example.productservice.service.CategoryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categories")
@Slf4j
public class CategoryController {
    private final CategoryService service;
    public CategoryController(CategoryService service) { this.service = service; }

    @GetMapping
    ResponseEntity<ApiResponse<List<Category>>> getAll(HttpServletRequest request) {
        List<Category> categories = service.findAll();
        return ResponseEntity.ok(
                ApiResponse.success("Fetched categories successfully", categories, request.getRequestURI())
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<Category> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Category>> create(@RequestBody Category category) {
        Category created = service.create(category);
        //return ResponseEntity.created(URI.create("/api/categories/" + created.getId())).body(created);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created successfully", created,"/api/categories/"));
    }

    @PostMapping("/batch")
    public ResponseEntity<List<Category>> createCategories(@RequestBody List<Category> categories) {
        List<Category> saved = service.createCategories(categories);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<List<Category>>> createBulk(
            @RequestBody List<Category> categories,
            HttpServletRequest request) {

        List<String> failed = new ArrayList<>();

        // Pass a reference to collect failure messages inside the service
        List<Category> saved = service.createBulk(categories, failed);

        // Prepare different responses for full / partial / complete failure
        if (saved.isEmpty() && !failed.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<List<Category>>builder()
                            .timestamp(Instant.now())
                            .status(HttpStatus.BAD_REQUEST.value())
                            .message("Bulk creation failed. No valid categories to save.")
                            .error(String.join("; ", failed))
                            .path(request.getRequestURI())
                            .build());
        }

        if (!failed.isEmpty()) {
            String msg = String.format("Created %d categories, %d failed",
                    saved.size(), failed.size());

            return ResponseEntity.status(HttpStatus.MULTI_STATUS)
                    .body(ApiResponse.<List<Category>>builder()
                            .timestamp(Instant.now())
                            .status(HttpStatus.MULTI_STATUS.value())
                            .message(msg)
                            .data(saved)
                            .error(String.join("; ", failed))
                            .path(request.getRequestURI())
                            .build());
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("All categories created successfully", saved, request.getRequestURI()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Category> update(@PathVariable Long id, @RequestBody Category c) {
        return ResponseEntity.ok(service.update(id, c));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    //@PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deleteAllCategories(
            @RequestParam(defaultValue = "false") boolean confirm) {

        int deletedCount = service.deleteAllCategories(confirm);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Categories deleted successfully");
        response.put("deletedCount", deletedCount);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/deleteAll")
    public ResponseEntity<?> deleteAll() {
        service.deleteAll();
        return ResponseEntity.ok("All categories deleted");
    }

    @DeleteMapping("/deleteByIds")
    public ResponseEntity<?> deleteMultiple(@RequestBody Map<String, List<Long>> request) {
        List<Long> ids = request.get("ids");
        service.deleteAllByIds(ids);
        return ResponseEntity.ok(Map.of("message", "Deleted categories", "count", ids.size()));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<Category> getByCode(@PathVariable String code) {
        Category category = service.getByCode(code);
        return ResponseEntity.ok(category);
    }

    @GetMapping("/search")
    public ResponseEntity<List<Category>> search(@RequestParam String keyword) {
        return ResponseEntity.ok(service.search(keyword));
    }
}