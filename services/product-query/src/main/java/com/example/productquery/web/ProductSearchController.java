package com.example.productquery.web;

import com.example.productquery.model.ProductDoc;
import com.example.productquery.repo.ProductSearchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only product search. All reads hit Elasticsearch, not MySQL.
 *
 *   GET /products/search             → derived query, Page<ProductDoc> (Phase 2 shape)
 *   GET /products/search/{id}        → direct doc lookup
 *   GET /products/search/query       → NativeQuery (Phase 3 — bool + filter + fuzzy + scores)
 */
@RestController
@RequestMapping("/products/search")
public class ProductSearchController {

    private final ProductSearchRepository repo;
    private final ProductSearchService searchService;

    public ProductSearchController(ProductSearchRepository repo, ProductSearchService searchService) {
        this.repo = repo;
        this.searchService = searchService;
    }

    /**
     * Phase 2 — simple paged list with optional name-contains filter.
     * Kept for cheap backward-compat; prefer {@link #search} for anything real.
     */
    @GetMapping
    public Page<ProductDoc> list(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pr = PageRequest.of(page, size);
        if (name != null && !name.isBlank()) {
            return repo.findByNameContainingIgnoreCaseAndDeletedFalse(name, pr);
        }
        return repo.findByDeletedFalse(pr);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDoc> byId(@PathVariable String id) {
        return repo.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Phase 3 + 4 — the real search endpoint.
     *
     *   GET /products/search/query?q=widget
     *   GET /products/search/query?q=widgt&fuzzy=true           ← Levenshtein, auto distance
     *   GET /products/search/query?sku=WID-001                  ← exact match (filter clause)
     *   GET /products/search/query?category=widgets             ← facet-driven navigation (filter)
     *   GET /products/search/query?brand=acme                   ← same
     *   GET /products/search/query?minPrice=10&maxPrice=50
     *   GET /products/search/query?inStock=true                 ← stock > 0 only
     *   GET /products/search/query?aggregations=category,brand,price   ← Phase 4: facet sidebar
     *
     * Response includes `maxScore` so you can see BM25 at work:
     *   - Narrow q + unique match         → score ~2.0+
     *   - Common word match               → score close to 0
     *   - Filter-only query (no q)        → score = 0 for every hit
     *
     * Response includes `facets` when aggregations are requested — one map
     * entry per requested facet name, each with {key, count} buckets.
     */
    @GetMapping("/query")
    public SearchResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean fuzzy,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) String aggregations,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return searchService.search(new ProductSearchService.SearchCriteria(
                q, fuzzy, sku, category, brand, minPrice, maxPrice, inStock,
                aggregations, page, size));
    }
}
