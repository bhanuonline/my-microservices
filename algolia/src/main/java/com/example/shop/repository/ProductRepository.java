package com.example.shop.repository;

import com.example.shop.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    // JpaRepository gives you findAll(), save(), deleteById() etc. for free
}