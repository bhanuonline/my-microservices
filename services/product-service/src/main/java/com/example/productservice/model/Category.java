package com.example.productservice.model;

import com.fasterxml.jackson.annotation.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@Entity
@Table(name = "category")
@Data
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //for unique code at db level make it true
    @Column(unique = false, nullable = false)
    @NotBlank(message = "Code is required")
    private String code;        // e.g. CAT001

    @Column(nullable = false)
    @NotBlank(message = "Name is required")
    private String name;        // e.g. "Men’s Clothing"

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "parent_id")
    //@JsonBackReference               // 👈 prevents recursion from parent side
    @JsonIgnore
    private Category parent;    // to support hierarchy

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = false)
    //@JsonManagedReference            // 👈 marks this as the "forward" side
    private List<Category> children;

    private String description;

    // soft delete flag
    @Column(nullable = false)
    private boolean deleted = false;

    /**
     * These lifecycle callbacks are invoked automatically by JPA before the entity is saved.integrate nicely with @Transactional
     */
    //@PrePersist
    //@PreUpdate
    public void normalize() {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Category code must not be empty");
        }
        if (code != null) {
            code = code.trim().toLowerCase();
        }
        if (name != null) {
            name = name.trim();
        }
    }
}