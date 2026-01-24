package com.example.productservice.repository;

import com.example.productservice.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;

@Validated
public interface CategoryRepository extends JpaRepository<Category, Long> {
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByNameIgnoreCase(String name);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    @Modifying
    @Query("UPDATE Category c SET c.deleted = true WHERE c.deleted = false")
    int softDeleteAll();

    List<Category> findByDeletedFalse();
    Optional<Category> findByCode(String code);
    List<Category> findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(String name, String code);
}

