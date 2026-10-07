package com.example.shop.controller;

import com.algolia.model.search.Hit;
import com.algolia.model.search.SearchResponse;
import com.example.shop.model.Product;
import com.example.shop.model.SearchResult;
import com.example.shop.model.SortOption;
import com.example.shop.service.AlgoliaService;
import com.example.shop.service.ProductService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
public class ProductController {

    private final AlgoliaService algoliaService;
    private final ProductService productService;

    public ProductController(AlgoliaService algoliaService,ProductService productService) {
        this.algoliaService = algoliaService;
        this.productService = productService;
    }


    @GetMapping("/push-products")
    public String pushProducts() throws Exception {

        // Sample product data (in real life, this comes from your database)
        List<Product> products = List.of(
            new Product("prod-001", "Nike Air Max 90", "Nike", "Sneakers", "Blue", 120.0, 4.5, true),
            new Product("prod-002", "Adidas Ultraboost", "Adidas", "Sneakers", "White", 150.0, 4.7, true),
            new Product("prod-003", "Puma RS-X", "Puma", "Sneakers", "Red", 90.0, 4.2, false),
            new Product("prod-004", "Reebok Classic", "Reebok", "Sneakers", "Black", 80.0, 4.0, true)
        );

        algoliaService.pushProducts(products);
        return "Products pushed to Algolia successfully!";
    }

    @GetMapping("/search")
    public List<Product> search(@RequestParam String query) throws Exception {
        SearchResponse<Product> response = algoliaService.searchProducts(query);
        // Extract just the product objects from Algolia's Hit wrappers
        return response.getHits();
    }

    @GetMapping("/search-paged")
    public SearchResult searchPaged(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,         // default: first page
            @RequestParam(defaultValue = "2") int hitsPerPage)  // default: 2 per page
            throws Exception {

        return algoliaService.searchProductsPaginated(query, page, hitsPerPage);
    }

    @GetMapping("/search-sorted")
    public SearchResult searchSorted(
            @RequestParam String query,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int hitsPerPage,
            @RequestParam(defaultValue = "RELEVANCE") SortOption sort)
            throws Exception {

        return algoliaService.searchProductsSorted(query, page, hitsPerPage, sort);
    }

    // Add a new product → saves to MySQL + syncs to Algolia
    @PostMapping
    public Product addProduct(@RequestBody Product product) throws Exception {
        return productService.saveProduct(product);
    }

    // Get all products from MySQL
    @GetMapping
    public List<Product> getAllProducts() {
        return productService.getAllProducts();
    }

    // Sync all MySQL products → Algolia (useful for first-time setup)
    @PostMapping("/sync")
    public String syncToAlgolia() throws Exception {
        productService.syncAllToalgolia();
        return "✅ All products synced to Algolia!";
    }

    // Delete product from MySQL + Algolia
    @DeleteMapping("/{id}")
    public String deleteProduct(@PathVariable Long id) throws Exception {
        productService.deleteProduct(id);
        return "✅ Product deleted!";
    }
}