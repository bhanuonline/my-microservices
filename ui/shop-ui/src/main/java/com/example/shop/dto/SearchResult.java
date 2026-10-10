package com.example.shop.dto;

import java.util.List;
import java.util.Map;

/**
 * Normalised view of what /products/search/query returns. One per page render.
 */
public record SearchResult(
        List<ProductView> products,
        long total,
        int page,
        int size,
        Map<String, List<FacetBucket>> facets
) {
    public int totalPages() {
        return size <= 0 ? 1 : (int) Math.max(1, Math.ceil((double) total / size));
    }
    public boolean hasPrev() { return page > 0; }
    public boolean hasNext() { return page + 1 < totalPages(); }
}
