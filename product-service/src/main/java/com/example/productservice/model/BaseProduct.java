package com.example.productservice.model;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "base_product")
@Data
@EntityListeners(AuditingEntityListener.class)
public class BaseProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String baseCode;

    @Column(nullable = false)
    private String name;

    private String description;
    private String brand;
    // Link to category
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @OneToMany(mappedBy = "baseProduct", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductVariant> variants = new ArrayList<>();

    @CreatedBy @Column(updatable = false) private String createdBy;
    @CreatedDate @Column(updatable = false) private LocalDateTime createdAt;
    @LastModifiedBy private String updatedBy;
    @LastModifiedDate private LocalDateTime updatedAt;
}