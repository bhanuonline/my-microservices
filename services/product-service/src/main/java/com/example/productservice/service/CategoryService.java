package com.example.productservice.service;

import com.example.productservice.exception.ResourceNotFoundException;
import com.example.productservice.model.Category;
import com.example.productservice.repository.CategoryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@Transactional
public class CategoryService {
    private final CategoryRepository repo;
    public CategoryService(CategoryRepository repo) { this.repo = repo; }

    public List<Category> findAll() {
        log.info("Fetching all categories");
        return repo.findAll();
    }

    public Category findById(Long id) {
        log.info("Fetching category {}", id);
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found id=" + id));
    }

    public Category create(Category c) {
        //c.setCode(toSafeCode(c.getCode()));

        log.info("Creating category {}", c.getName());
//        if (repo.existsByCodeIgnoreCase(c.getCode())) {
//            throw new IllegalArgumentException("Category code already exists: " + c.getCode());
//        }
//
//        if (repo.existsByNameIgnoreCase(c.getName())) {
//            throw new IllegalArgumentException("Category name already exists: " + c.getName());
//        }
        return repo.save(c);
    }

    public Category update(Long id, Category c) {
        Category existing = findById(id);
        log.info("Updating category id {}", id);
        existing.setName(c.getName());
        existing.setCode(c.getCode());
        existing.setDescription(c.getDescription());
        existing.setParent(c.getParent());
        return repo.save(existing);
    }

    public void delete(Long id) {
        log.warn("Deleting category id {}", id);
        Category c = findById(id);
        repo.delete(c);
    }

    private String toSafeCode(String val) {
        if (val == null || val.isBlank()) {
            throw new IllegalArgumentException("Category code must not be empty");
        }
        return val.trim().toLowerCase();
    }

    public List<Category> createCategories(List<Category> categories) {
        // Phase 1: Validate all records before starting the transaction
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < categories.size(); i++) {
            Category category = categories.get(i);
            normalize(category);

            if (category.getCode() == null || category.getCode().isBlank()) {
                errors.add("Record " + (i + 1) + ": Category code must not be empty");
                continue;
            }

            if (category.getName() == null || category.getName().isBlank()) {
                errors.add("Record " + (i + 1) + ": Category name must not be empty");
                continue;
            }

            if (repo.existsByCodeIgnoreCase(category.getCode())) {
                errors.add("Record " + (i + 1) +
                        ": Category code already exists -> " + category.getCode());
            }

            if (repo.existsByNameIgnoreCase(category.getName())) {
                errors.add("Record " + (i + 1) +
                        ": Category name already exists -> " + category.getName());
            }
        }

        // If any validation errors found → throw single exception listing all of them
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Bulk validation failed:\n" + String.join("\n", errors));
        }

        // Phase 2: Save all at once inside transaction
        return saveAllTransactional(categories);
    }

    @Transactional
    protected List<Category> saveAllTransactional(List<Category> categories) {
        return repo.saveAll(categories);
    }

    // Helper to normalize fields
    private void normalize(Category c) {
        if (c.getCode() != null) c.setCode(c.getCode().trim().toLowerCase());
        if (c.getName() != null) c.setName(c.getName().trim());
    }

    @Transactional
    public List<Category> createBulk(List<Category> categories, List<String> failedMessages) {
        List<Category> validCategories = new ArrayList<>();

        for (Category c : categories) {
            String name = c.getName();
            String code = c.getCode();

            if (repo.existsByCodeIgnoreCase(code)) {
                failedMessages.add(String.format("Category code already exists: %s", code));
                continue;
            }

            if (repo.existsByNameIgnoreCase(name)) {
                failedMessages.add(String.format("Category name already exists: %s", name));
                continue;
            }

            validCategories.add(c);
        }

        if (validCategories.isEmpty()) {
            log.warn("No valid categories found to save.");
            return new ArrayList<>();
        }

        // Save all valid ones in a single batch
        List<Category> saved = repo.saveAll(validCategories);
        log.info("Saved {} categories in batch", saved.size());
        return saved;
    }

    @Transactional
    public int deleteAllCategories(boolean confirm) {
        if (!confirm) {
            throw new IllegalArgumentException("Delete confirmation is required");
        }
        return repo.softDeleteAll();
    }

    @Transactional
    public void deleteAllByIds(List<Long> ids) {
        repo.deleteAllByIdInBatch(ids);
    }

    public void deleteAll() {
        repo.deleteAll();
    }

    public Category getByCode(String code) {
        return repo.findByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("Category not found for code: " + code));
    }

    public List<Category> search(String keyword) {
        return repo
                .findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(keyword, keyword);
    }
}