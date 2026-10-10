package com.example.productservice.repository;

import com.example.productservice.model.StockLevel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockLevelRepository extends JpaRepository<StockLevel, Long> {}
