package com.example.shop.model;

import jakarta.persistence.*;

@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String objectID;   // Required by Algolia
    private String name;
    private String brand;
    private String category;
    private String color;
    private double price;
    private double rating;
    private boolean inStock;

    public Long getId() {
        return id;
    }
    public void setObjectID(String objectID) {
        this.objectID = objectID;
    }

    public void setId(Long id) {
        this.id = id;
    }
    // Default constructor (required by JPA)
    public Product() {}

    // Constructor
    public Product(String objectID, String name, String brand,
                   String category, String color,
                   double price, double rating, boolean inStock) {
        this.objectID = objectID;
        this.name = name;
        this.brand = brand;
        this.category = category;
        this.color = color;
        this.price = price;
        this.rating = rating;
        this.inStock = inStock;
    }

    // Getters & Setters (or use Lombok @Data to skip boilerplate)
    public String getObjectID() { return objectID; }
    public String getName() { return name; }
    public String getBrand() { return brand; }
    public String getCategory() { return category; }
    public String getColor() { return color; }
    public double getPrice() { return price; }
    public double getRating() { return rating; }
    public boolean isInStock() { return inStock; }
}