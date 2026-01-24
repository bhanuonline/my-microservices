package com.example.productservice.listener;

import com.example.productservice.model.Category;
import com.example.productservice.repository.CategoryRepository;
import org.springframework.data.rest.core.annotation.HandleBeforeCreate;
import org.springframework.data.rest.core.annotation.HandleBeforeSave;
import org.springframework.data.rest.core.annotation.RepositoryEventHandler;
import org.springframework.stereotype.Component;

@Component
@RepositoryEventHandler(Category.class)
public class CategoryEventListener {

    private final CategoryRepository categoryRepository;

    public CategoryEventListener(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @HandleBeforeCreate
    @HandleBeforeSave
    public void beforeSave(Category category) {
        normalize(category);
        validate(category);
    }

    private void normalize(Category category) {
        if (category.getCode() != null) {
            category.setCode(category.getCode().trim().toLowerCase());
        }
        if (category.getName() != null) {
            category.setName(category.getName().trim());
        }
    }

    private void validate(Category category) {
        if (category.getCode() == null || category.getCode().isBlank()) {
            throw new IllegalArgumentException("Category code must not be empty");
        }
        if (category.getName() == null || category.getName().isBlank()) {
            throw new IllegalArgumentException("Category name must not be empty");
        }

        boolean codeExists = categoryRepository.existsByCodeIgnoreCase(category.getCode());
        boolean nameExists = categoryRepository.existsByNameIgnoreCase(category.getName());

        // if it's an update, ignore the current entity
        if (category.getId() != null) {
            codeExists = categoryRepository.existsByCodeIgnoreCaseAndIdNot(category.getCode(), category.getId());
            nameExists = categoryRepository.existsByNameIgnoreCaseAndIdNot(category.getName(), category.getId());
        }

        if (codeExists) {
            throw new IllegalArgumentException("Category code already exists: " + category.getCode());
        }
        if (nameExists) {
            throw new IllegalArgumentException("Category name already exists: " + category.getName());
        }
    }
}