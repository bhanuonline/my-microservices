package com.example.productservice.service;

import com.example.productservice.exception.ResourceNotFoundException;
import com.example.productservice.model.StockLevel;
import com.example.productservice.repository.StockLevelRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Slf4j
@Transactional
public class StockLevelService {

    private final StockLevelRepository repo;

    public StockLevelService(StockLevelRepository repo) { this.repo = repo; }

    public List<StockLevel> findAll() {
        log.info("Fetching all stock levels");
        return repo.findAll();
    }

    public StockLevel findById(Long id) {
        log.info("Fetching stock level {}", id);
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StockLevel not found id=" + id));
    }

    public StockLevel create(StockLevel s) {
        log.info("Creating stock level record for variant {}", s.getProductVariant().getId());
        return repo.save(s);
    }

    public StockLevel update(Long id, StockLevel s) {
        StockLevel existing = findById(id);
        existing.setQuantityOnHand(s.getQuantityOnHand());
        existing.setQuantityReserved(s.getQuantityReserved());
        existing.setLocation(s.getLocation());
        return repo.save(existing);
    }

    public void delete(Long id) {
        log.warn("Deleting stock level {}", id);
        StockLevel s = findById(id);
        repo.delete(s);
    }
}