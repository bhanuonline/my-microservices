package com.example.productquery.web;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.CompletionSuggester;
import co.elastic.clients.elasticsearch.core.search.FieldSuggester;
import co.elastic.clients.elasticsearch.core.search.Suggester;
import co.elastic.clients.elasticsearch.core.search.SuggestFuzziness;
import com.example.productquery.model.ProductDoc;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Autocomplete surface. Two variants, each with its own trade-off:
 *
 *   GET /products/suggest?prefix=wid                 ← completion suggester (FST, sub-ms)
 *   GET /products/suggest/contains?q=wid&inStock=true ← edge-ngram sub-field (filterable)
 *
 * Why both exist:
 *   - FST is faster but can't filter/aggregate — it's a pure prefix lookup.
 *   - Edge n-gram is a normal text field, so bool+filter+aggregations all work.
 *     That makes it the right tool when autocomplete needs to respect stock,
 *     price, category, etc. (common in e-commerce).
 */
@RestController
@RequestMapping("/products/suggest")
public class AutocompleteController {

    private static final String INDEX_ALIAS = "products";
    private static final String SUGGEST_FIELD = "suggest";
    private static final String SUGGEST_NAME = "product-suggest";

    private final ElasticsearchClient client;

    public AutocompleteController(ElasticsearchClient client) {
        this.client = client;
    }

    /**
     * Pure prefix autocomplete via the completion suggester.
     *
     *   GET /products/suggest?prefix=wi             → ["Blue Widget","widgets",…]
     *   GET /products/suggest?prefix=widgt&fuzzy=true
     *
     * Response is a de-duplicated list of suggestion strings capped at `size`.
     * No scores, no filters — if you need those, use the /contains endpoint.
     */
    @GetMapping
    public ResponseEntity<?> suggest(
            @RequestParam String prefix,
            @RequestParam(defaultValue = "false") boolean fuzzy,
            @RequestParam(defaultValue = "10") int size) {

        try {
            CompletionSuggester.Builder completion = new CompletionSuggester.Builder()
                    .field(SUGGEST_FIELD)
                    .size(size)
                    .skipDuplicates(true);
            if (fuzzy) {
                completion.fuzzy(SuggestFuzziness.of(f -> f.fuzziness("AUTO")));
            }

            Suggester suggester = Suggester.of(s -> s
                    .suggesters(Map.of(SUGGEST_NAME, FieldSuggester.of(fs -> fs
                            .prefix(prefix)
                            .completion(completion.build()))))
            );

            SearchResponse<ProductDoc> resp = client.search(req -> req
                    .index(INDEX_ALIAS)
                    .size(0)                     // hits irrelevant; we only want the suggestions
                    .source(src -> src.fetch(false))
                    .suggest(suggester), ProductDoc.class);

            // Response path: resp.suggest() → Map<String, List<Suggestion>>
            // For each Suggestion → .completion() → .options() → .text()
            Set<String> out = new LinkedHashSet<>();
            resp.suggest().getOrDefault(SUGGEST_NAME, List.of()).forEach(sugg ->
                    sugg.completion().options().forEach(opt -> out.add(opt.text()))
            );
            return ResponseEntity.ok(Map.of("suggestions", out));
        } catch (IOException | co.elastic.clients.elasticsearch._types.ElasticsearchException e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "suggest_failed", "message", e.getMessage()));
        }
    }

    /**
     * Edge-ngram "contains" autocomplete via the name.autocomplete sub-field.
     *
     *   GET /products/suggest/contains?q=widg&inStock=true&category=widgets
     *
     * Returns the actual docs (not just strings) with their relevance score,
     * so the UI can render thumbnails + prices alongside the matched name.
     * Supports the same filter dimensions as /products/search/query.
     */
    @GetMapping("/contains")
    public ResponseEntity<?> contains(
            @RequestParam String q,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "10") int size) {

        try {
            BoolQuery.Builder bool = new BoolQuery.Builder();

            // MUST: match against the edge-ngram sub-field.
            // The index-time analyzer produces grams; the search_analyzer is
            // whole-word so "widg" matches the "widg" gram inside "widget",
            // not against three single-char grams of "w","i","d","g".
            bool.must(Query.of(qq -> qq.match(MatchQuery.of(m -> m
                    .field("name.autocomplete").query(q)))));

            // Standard product filters re-used.
            bool.filter(Query.of(qq -> qq.term(TermQuery.of(t -> t
                    .field("deleted").value(false)))));
            if (inStock != null && inStock) {
                bool.filter(Query.of(qq -> qq.range(r -> r
                        .field("stock").gt(co.elastic.clients.json.JsonData.of(0)))));
            }
            if (category != null && !category.isBlank()) {
                bool.filter(Query.of(qq -> qq.term(TermQuery.of(t -> t
                        .field("category").value(category)))));
            }

            Query root = Query.of(qq -> qq.bool(bool.build()));
            SearchResponse<ProductDoc> resp = client.search(req -> req
                    .index(INDEX_ALIAS)
                    .query(root)
                    .size(size), ProductDoc.class);

            List<Map<String, Object>> hits = resp.hits().hits().stream()
                    .map(h -> {
                        ProductDoc d = h.source();
                        return Map.<String, Object>of(
                                "id", d != null ? d.getId() : h.id(),
                                "name", d != null ? d.getName() : null,
                                "price", d != null ? d.getPrice() : null,
                                "score", h.score() != null ? h.score() : 0.0
                        );
                    })
                    .toList();

            return ResponseEntity.ok(Map.of(
                    "total", resp.hits().total() != null ? resp.hits().total().value() : hits.size(),
                    "hits", hits
            ));
        } catch (IOException | co.elastic.clients.elasticsearch._types.ElasticsearchException e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "contains_failed", "message", e.getMessage()));
        }
    }

}
