package com.example.productservice.repository;

import com.example.productservice.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Q: @Repository Is Not Required Here?
 * Ans:You don’t need to explicitly annotate your Spring Data JpaRepository interfaces with @Repository because Spring Data JPA already does it for you automatically
 * It dynamically creates a proxy implementation class for your repository.
 *
 * That proxy is automatically annotated with @Repository, so:
 *
 * It’s registered as a Spring Bean, and
 * It participates in Spring’s exception‑translation mechanism
 *
 * You’re Writing a Custom Repository (Manually Implemented)
 */
public interface ProductRepository extends JpaRepository<Product, Long> {


    //Custom Repository
    @Modifying
    @Query(
            value = "INSERT INTO products(name, description, price, sku, category, quantity_in_stock, brand, weight, dimensions, active, created_at, updated_at) " +
                    "VALUES (:name, :description, :price, :sku, :category, :quantityInStock, :brand, :weight, :dimensions, :active, NOW(), NOW())",
            nativeQuery = true)
    void insertProduct(
            @Param("name") String name,
            @Param("description") String description,
            @Param("price") double price,
            @Param("sku") String sku,
            @Param("category") String category,
            @Param("quantityInStock") int quantityInStock,
            @Param("brand") String brand,
            @Param("weight") double weight,
            @Param("dimensions") String dimensions,
            @Param("active") boolean active
    );

}