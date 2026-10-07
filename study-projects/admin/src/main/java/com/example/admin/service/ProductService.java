package com.example.admin.service;

import com.example.admin.model.Product;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductService {

    private final Map<Long, Product> products = new HashMap<>();
    private long sequence = 1L;

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public void createProduct(String name, double price) {
        Long id = sequence++;
        products.put(id, new Product(id, name, price));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    public List<Product> getAllProducts() {
        return new ArrayList<>(products.values());
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    public Product getProduct(Long id) {
        return products.get(id);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public void updateProduct(Long id, String name, double price) {
        products.put(id, new Product(id, name, price));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void deleteProduct(Long id) {
        products.remove(id);
    }
}