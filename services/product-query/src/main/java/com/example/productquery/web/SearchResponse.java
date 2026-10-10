package com.example.productquery.web;

import com.example.productquery.model.ProductDoc;

import java.util.List;
import java.util.Map;

/**
 * Thin wrapper so clients see the parts of an ES SearchResponse that matter:
 *   hits      the docs
 *   total     total matches (not the page size)
 *   maxScore  best relevance score — surfaces the BM25 story
 *   facets    terms + range aggregations — powers sidebar filter UI
 *
 * Shape of facets:
 *
 *   {
 *     "category": [ { "key": "widgets", "count": 42 }, { "key": "gadgets", "count": 7 } ],
 *     "brand":    [ { "key": "acme",    "count": 25 } ],
 *     "price":    [ { "key": "0-25",    "count": 10 }, { "key": "25-50", "count": 30 } ]
 *   }
 */
public record SearchResponse(
        List<Hit> hits,
        long total,
        float maxScore,
        Map<String, List<Bucket>> facets
) {

    public record Hit(ProductDoc doc, float score) {}
    public record Bucket(String key, long count) {}
}
