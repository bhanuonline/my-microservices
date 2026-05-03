package com.fastapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products", indexes = {
    // ✅ Index every column used in WHERE / ORDER BY
    @Index(name = "idx_products_category", columnList = "category"),
    @Index(name = "idx_products_active",   columnList = "active")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String category;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
