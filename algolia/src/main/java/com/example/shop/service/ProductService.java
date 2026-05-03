package com.example.shop.service;

import com.example.shop.model.Product;
import com.example.shop.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final AlgoliaService algoliaService;

    public ProductService(ProductRepository productRepository,
                          AlgoliaService algoliaService) {
        this.productRepository = productRepository;
        this.algoliaService = algoliaService;
    }

    // Save one product to MySQL + sync to Algolia
    public Product saveProduct(Product product) throws Exception {
        // 1. Save to MySQL first
        Product saved = productRepository.save(product);

        // 2. Use DB id as objectID for Algolia
        saved.setObjectID("prod-" + saved.getId());
        productRepository.save(saved);  // update objectID in DB

        // 3. Push to Algolia
        algoliaService.pushProducts(List.of(saved));

        return saved;
    }

    // Sync ALL products from MySQL → Algolia
    public void syncAllToalgolia() throws Exception {
        List<Product> allProducts = productRepository.findAll();

        if (allProducts.isEmpty()) {
            System.out.println("⚠️ No products in database to sync.");
            return;
        }

        algoliaService.pushProducts(allProducts);
        System.out.println("✅ Synced " + allProducts.size() + " products to Algolia!");
    }

    // Get all products from MySQL
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    // Delete product from MySQL + Algolia
    public void deleteProduct(Long id) throws Exception {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        // 1. Delete from Algolia
        algoliaService.deleteProduct(product.getObjectID());

        // 2. Delete from MySQL
        productRepository.deleteById(id);

        System.out.println("✅ Product deleted from MySQL and Algolia!");
    }
}