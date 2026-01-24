package com.example.productservice.service;

import com.example.productservice.exception.ResourceNotFoundException;
import com.example.productservice.model.BaseProduct;
import com.example.productservice.repository.BaseProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Slf4j
@Transactional
public class BaseProductService {

    private final BaseProductRepository repo;

    public BaseProductService(BaseProductRepository repo) { this.repo = repo; }

    public List<BaseProduct> findAll() {
        log.info("Fetching all base products");
        return repo.findAll();
    }

    public BaseProduct findById(Long id) {
        log.info("Fetching base product {}", id);
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BaseProduct not found id=" + id));
    }

    public BaseProduct create(BaseProduct b) {
        log.info("Creating base product {}", b.getBaseCode());
        return repo.save(b);
    }

    public BaseProduct update(Long id, BaseProduct b) {
        BaseProduct existing = findById(id);
        existing.setName(b.getName());
        existing.setBrand(b.getBrand());
        existing.setDescription(b.getDescription());
        existing.setCategory(b.getCategory());
       // existing.setColor(b.getColor());
        //existing.setMaterial(b.getMaterial());
        return repo.save(existing);
    }

    public void delete(Long id) {
        BaseProduct b = findById(id);
        log.warn("Deleting base product {}", id);
        repo.delete(b);
    }
}