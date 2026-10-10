package com.example.productservice.repository;

import com.example.productservice.model.BaseProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BaseProductRepository extends JpaRepository<BaseProduct, Long> {}
