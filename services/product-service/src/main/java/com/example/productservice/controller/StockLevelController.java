package com.example.productservice.controller;

import com.example.productservice.model.StockLevel;
import com.example.productservice.service.StockLevelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/stock-levels")
@Slf4j
public class StockLevelController {

    private final StockLevelService service;
    public StockLevelController(StockLevelService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<List<StockLevel>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<StockLevel> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<StockLevel> create(@RequestBody StockLevel s) {
        StockLevel saved = service.create(s);
        return ResponseEntity.created(URI.create("/api/stock-levels/" + saved.getId())).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<StockLevel> update(@PathVariable Long id, @RequestBody StockLevel s) {
        return ResponseEntity.ok(service.update(id, s));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}