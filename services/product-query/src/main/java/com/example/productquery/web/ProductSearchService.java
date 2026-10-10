package com.example.productquery.web;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationRange;
import co.elastic.clients.elasticsearch._types.aggregations.RangeAggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.TermsAggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.json.JsonData;
import com.example.productquery.model.ProductDoc;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.AggregationsContainer;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The real search implementation. Phase 2 used derived queries (hidden Lucene).
 * Phase 3 moved to NativeQuery + bool/filter/fuzzy — explicit DSL.
 * Phase 4 adds aggregations: terms (facet counts) + range (price buckets).
 *
 * Query clause rules — recite these cold in interviews:
 *   must     affects BM25 score;        use for full-text relevance
 *   should   boosts score if matched;   use for "nice to have"
 *   filter   NO score + CACHED;         use for yes/no (price, in-stock, flags, soft-delete)
 *   must_not negation
 *
 * Aggregation rules:
 *   terms     works on keyword / numeric; one bucket per unique value
 *   range     works on numeric fields; buckets you define up-front
 *   DO NOT    aggregate on `text` fields — tokenized values would bucket per token
 *             (ES will refuse unless fielddata=true, which blows up memory)
 */
@Service
public class ProductSearchService {

    private static final Set<String> ALLOWED_FACETS = Set.of("category", "brand", "price");

    private final ElasticsearchOperations ops;

    // ─── Phase 7 metrics ──────────────────────────────────────────────────
    // Timer: product_search_duration_seconds{result=hit|miss}
    //        Published with percentiles-histogram so Grafana can compute real p95/p99.
    // Counter: product_search_requests_total{result=hit|miss}
    //        Rate panel. zero-result rate is a canary for bad analyzers / stale indices.
    private final Timer searchHitTimer;
    private final Timer searchMissTimer;
    private final Counter zeroResultCounter;

    public ProductSearchService(ElasticsearchOperations ops, MeterRegistry registry) {
        this.ops = ops;
        this.searchHitTimer = Timer.builder("product_search_duration_seconds")
                .description("Elasticsearch product search latency, tagged by hit/miss")
                .tag("result", "hit")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);
        this.searchMissTimer = Timer.builder("product_search_duration_seconds")
                .description("Elasticsearch product search latency, tagged by hit/miss")
                .tag("result", "miss")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);
        this.zeroResultCounter = Counter.builder("product_search_zero_result_total")
                .description("Searches that returned no hits — canary for bad analyzers / stale indices")
                .register(registry);
    }

    public SearchResponse search(SearchCriteria c) {
        Timer.Sample sample = Timer.start();
        try {
            SearchResponse resp = doSearch(c);
            boolean hit = resp.total() > 0;
            sample.stop(hit ? searchHitTimer : searchMissTimer);
            if (!hit) zeroResultCounter.increment();
            return resp;
        } catch (RuntimeException e) {
            // Timer still stops but tagged as miss — we don't want crashed queries to inflate p95.
            sample.stop(searchMissTimer);
            throw e;
        }
    }

    private SearchResponse doSearch(SearchCriteria c) {
        BoolQuery.Builder bool = new BoolQuery.Builder();

        // ─── MUST — full-text relevance clauses (contribute to score) ───
        if (c.q() != null && !c.q().isBlank()) {
            MatchQuery.Builder nameMatch = new MatchQuery.Builder()
                    .field("name")
                    .query(c.q());
            if (c.fuzzy()) nameMatch.fuzziness("AUTO");
            bool.must(Query.of(q -> q.match(nameMatch.build())));
        }

        // ─── FILTER — exact / range clauses (no score contribution, cached) ───
        if (c.sku() != null && !c.sku().isBlank()) {
            bool.filter(Query.of(q -> q.term(TermQuery.of(t -> t
                    .field("sku").value(c.sku())))));
        }
        if (c.category() != null && !c.category().isBlank()) {
            bool.filter(Query.of(q -> q.term(TermQuery.of(t -> t
                    .field("category").value(c.category())))));
        }
        if (c.brand() != null && !c.brand().isBlank()) {
            bool.filter(Query.of(q -> q.term(TermQuery.of(t -> t
                    .field("brand").value(c.brand())))));
        }
        if (c.minPrice() != null || c.maxPrice() != null) {
            bool.filter(Query.of(q -> q.range(buildPriceRange(c))));
        }
        if (c.inStock() != null && c.inStock()) {
            bool.filter(Query.of(q -> q.range(RangeQuery.of(r -> r
                    .field("stock").gt(JsonData.of(0))))));
        }
        bool.filter(Query.of(q -> q.term(TermQuery.of(t -> t
                .field("deleted").value(false)))));

        BoolQuery built = bool.build();
        Query root = (built.must().isEmpty() && built.filter().isEmpty() && built.should().isEmpty())
                ? Query.of(q -> q.matchAll(m -> m))
                : Query.of(q -> q.bool(built));

        NativeQueryBuilder nqb = NativeQuery.builder()
                .withQuery(root)
                .withPageable(PageRequest.of(c.page(), c.size()));

        // ─── AGGREGATIONS — only build the ones the caller asked for ───
        List<String> requested = parseFacets(c.aggregations());
        for (String facet : requested) {
            switch (facet) {
                case "category" -> nqb.withAggregation("category",
                        Aggregation.of(a -> a.terms(TermsAggregation.of(t -> t.field("category").size(20)))));
                case "brand"    -> nqb.withAggregation("brand",
                        Aggregation.of(a -> a.terms(TermsAggregation.of(t -> t.field("brand").size(20)))));
                case "price"    -> nqb.withAggregation("price",
                        Aggregation.of(a -> a.range(RangeAggregation.of(r -> r
                                .field("price")
                                .ranges(
                                        AggregationRange.of(x -> x.key("0-25").to("25")),
                                        AggregationRange.of(x -> x.key("25-50").from("25").to("50")),
                                        AggregationRange.of(x -> x.key("50-100").from("50").to("100")),
                                        AggregationRange.of(x -> x.key("100+").from("100"))
                                )))));
                default -> { /* defensive — parseFacets already filtered */ }
            }
        }

        SearchHits<ProductDoc> hits = ops.search(nqb.build(), ProductDoc.class);

        List<SearchResponse.Hit> out = hits.stream()
                .map(h -> new SearchResponse.Hit(h.getContent(), (float) h.getScore()))
                .toList();

        Map<String, List<SearchResponse.Bucket>> facets = extractFacets(hits.getAggregations(), requested);

        return new SearchResponse(out, hits.getTotalHits(), (float) hits.getMaxScore(), facets);
    }

    /** Pull terms + range buckets from the typed 8.x Aggregate union. */
    private static Map<String, List<SearchResponse.Bucket>> extractFacets(
            AggregationsContainer<?> aggsContainer, List<String> requested) {
        Map<String, List<SearchResponse.Bucket>> out = new LinkedHashMap<>();
        if (aggsContainer == null || requested.isEmpty()) return out;
        if (!(aggsContainer instanceof ElasticsearchAggregations esAggs)) return out;

        for (ElasticsearchAggregation agg : esAggs.aggregations()) {
            String name = agg.aggregation().getName();
            if (!requested.contains(name)) continue;

            Aggregate aggregate = agg.aggregation().getAggregate();
            List<SearchResponse.Bucket> buckets = new ArrayList<>();

            if (aggregate.isSterms()) {
                for (StringTermsBucket b : aggregate.sterms().buckets().array()) {
                    buckets.add(new SearchResponse.Bucket(b.key().stringValue(), b.docCount()));
                }
            } else if (aggregate.isRange()) {
                aggregate.range().buckets().array().forEach(b ->
                        buckets.add(new SearchResponse.Bucket(b.key(), b.docCount())));
            }
            out.put(name, buckets);
        }
        return out;
    }

    private static List<String> parseFacets(String csv) {
        if (!StringUtils.hasText(csv)) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .filter(ALLOWED_FACETS::contains)
                .distinct()
                .toList();
    }

    private static RangeQuery buildPriceRange(SearchCriteria c) {
        RangeQuery.Builder r = new RangeQuery.Builder().field("price");
        if (c.minPrice() != null) r.gte(JsonData.of(c.minPrice()));
        if (c.maxPrice() != null) r.lte(JsonData.of(c.maxPrice()));
        return r.build();
    }

    /** Immutable args — easier to extend than a long method signature. */
    public record SearchCriteria(
            String q,
            boolean fuzzy,
            String sku,
            String category,
            String brand,
            Double minPrice,
            Double maxPrice,
            Boolean inStock,
            String aggregations,
            int page,
            int size
    ) {}
}
