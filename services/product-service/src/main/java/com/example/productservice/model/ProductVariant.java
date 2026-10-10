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
@Table(name = "product_variant")
@Data
@EntityListeners(AuditingEntityListener.class)
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;

    private double price;
    private int quantityInStock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_product_id")
    private BaseProduct baseProduct;

    // One variant -> one or many stock records
    @OneToMany(mappedBy = "productVariant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StockLevel> stockLevels = new ArrayList<>();

    // Flexible attributes (color, size, material, etc.)
    @ManyToMany
    @JoinTable(
        name = "variant_attribute",
        joinColumns = @JoinColumn(name = "variant_id"),
        inverseJoinColumns = @JoinColumn(name = "attribute_value_id")
    )
    private List<AttributeValue> attributeValues = new ArrayList<>();

    private boolean active = true;

    @CreatedBy @Column(updatable = false) private String createdBy;
    @CreatedDate @Column(updatable = false) private LocalDateTime createdAt;
    @LastModifiedBy private String updatedBy;
    @LastModifiedDate private LocalDateTime updatedAt;
}