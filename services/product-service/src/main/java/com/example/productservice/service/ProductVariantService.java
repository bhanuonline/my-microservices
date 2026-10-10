package com.example.productservice.service;

import com.example.productservice.exception.ResourceNotFoundException;
import com.example.productservice.model.ProductVariant;
import com.example.productservice.repository.ProductVariantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Slf4j
@Transactional
public class ProductVariantService {

    private final ProductVariantRepository repo;

    public ProductVariantService(ProductVariantRepository repo) { this.repo = repo; }

    public List<ProductVariant> findAll() {
        log.info("Fetching all product variants");
        return repo.findAll();
    }

    public ProductVariant findById(Long id) {
        log.info("Fetching product variant {}", id);
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant not found id=" + id));
    }

    public ProductVariant create(ProductVariant v) {
        log.info("Creating product variant {}", v.getCode());
        return repo.save(v);
    }

    public ProductVariant update(Long id, ProductVariant v) {
        ProductVariant existing = findById(id);
        log.info("Updating product variant {}", id);
        existing.setCode(v.getCode());
        existing.setBaseProduct(v.getBaseProduct());
        existing.setPrice(v.getPrice());
        existing.setAttributeValues(v.getAttributeValues());
        return repo.save(existing);
    }

    public void delete(Long id) {
        ProductVariant v = findById(id);
        log.warn("Deleting product variant {}", id);
        repo.delete(v);
    }
}