package com.example.shop.controller;

import com.example.shop.client.ProductQueryClient;
import com.example.shop.dto.SearchResult;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Product listing / search page.
 *
 *   GET /products
 *   GET /products?q=widget
 *   GET /products?category=widgets        ← click a facet in the sidebar
 *   GET /products?q=widget&inStock=true&page=1
 *
 * Hits Elasticsearch via product-query; facets drive the sidebar.
 */
@Controller
public class ProductListingController {

    private final ProductQueryClient productQuery;

    public ProductListingController(ProductQueryClient productQuery) {
        this.productQuery = productQuery;
    }

    @GetMapping("/products")
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) String brand,
                       @RequestParam(required = false) Double minPrice,
                       @RequestParam(required = false) Double maxPrice,
                       @RequestParam(required = false) Boolean inStock,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "12") int size,
                       Model model) {

        SearchResult result = productQuery.search(
                q, category, brand, minPrice, maxPrice, inStock, page, size, true);

        model.addAttribute("result", result);
        model.addAttribute("activeFilters", activeFilters(q, category, brand, inStock));
        model.addAttribute("q", q);
        model.addAttribute("category", category);
        model.addAttribute("brand", brand);
        model.addAttribute("inStock", inStock);
        return "product/list";
    }

    /** Compact map rendered as removable chips at the top of the results. */
    private static Map<String, String> activeFilters(String q, String category, String brand, Boolean inStock) {
        Map<String, String> out = new LinkedHashMap<>();
        if (q != null && !q.isBlank())       out.put("q", q);
        if (category != null && !category.isBlank()) out.put("category", category);
        if (brand != null && !brand.isBlank())       out.put("brand", brand);
        if (Boolean.TRUE.equals(inStock))    out.put("inStock", "true");
        return out;
    }
}
