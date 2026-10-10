package com.example.productquery.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.CompletionField;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.Mapping;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.data.elasticsearch.annotations.Setting;
import org.springframework.data.elasticsearch.core.suggest.Completion;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Denormalized product search document.
 *
 * Field mapping cheatsheet (interview-critical):
 *
 *   name              text + keyword sub-field + autocomplete sub-field
 *                     - name              full-text search (standard analyzer)
 *                     - name.keyword      exact match / sort / aggregation
 *                     - name.autocomplete edge-ngram tokenized → "contains" prefix
 *   suggest           CompletionField (FST in memory, sub-ms prefix suggest)
 *   description       text — full-text search only
 *   price             Double
 *   stock             Integer
 *   sku, category, brand   keyword — exact match, facets, filters
 *   deleted           boolean soft-delete flag
 *
 * ---------------------------------------------------------------------------
 * Phase 5 — ALIAS INDIRECTION.
 *
 *   @Document points at the ALIAS "products", not a concrete index. Reads AND
 *   writes go through the alias; a reindex into products_vN followed by an
 *   atomic alias flip changes mapping with zero downtime.
 *
 *   AliasBootstrap ensures the alias exists on startup; without it Spring
 *   Data would create a concrete index literally named "products" on first
 *   write — breaking the flip because an index and alias can't share a name.
 * ---------------------------------------------------------------------------
 * Phase 6 — AUTOCOMPLETE.
 *
 *   TWO patterns, both exposed on separate endpoints so you can compare:
 *
 *   1. Completion suggester  (name=suggest field)
 *        - ES native "completion" field type → in-memory FST
 *        - Sub-ms prefix lookup
 *        - Only prefix (with optional fuzzy); no filters, no aggs
 *        - Great for search bar "search-as-you-type"
 *
 *   2. Edge n-gram "contains"  (name.autocomplete sub-field)
 *        - Custom analyzer tokenizes "widget" → "wi","wid","widg",…
 *        - Normal match query against that sub-field
 *        - Can combine with bool/filter/aggs — full search power
 *        - Slower + larger index than FST but much more flexible
 *
 *   The custom analyzer is declared via @Setting pointing to a JSON file:
 *   src/main/resources/elasticsearch/edge-ngram-settings.json
 * ---------------------------------------------------------------------------
 */
@Document(indexName = "products")
@Setting(settingPath = "elasticsearch/edge-ngram-settings.json")
@Mapping(mappingPath = "elasticsearch/products-mapping.json")
public class ProductDoc {

    @Id
    private String id;

    /**
     * Multi-field: text (standard analyzer) + keyword exact + autocomplete edge-ngram.
     * NOTE: @Mapping(mappingPath=...) above overrides the auto-generated mapping to
     * attach the custom "autocomplete" analyzer, which Spring Data's annotations
     * can't fully express. Annotations here are still useful at the Java-type level.
     */
    @MultiField(
        mainField = @Field(type = FieldType.Text),
        otherFields = {
            @InnerField(suffix = "keyword",     type = FieldType.Keyword, ignoreAbove = 256),
            @InnerField(suffix = "autocomplete", type = FieldType.Text,   analyzer = "autocomplete",
                        searchAnalyzer = "autocomplete_search")
        }
    )
    private String name;

    /** Completion suggester — populated from `name` by {@link #fromEvent}. */
    @CompletionField(maxInputLength = 100)
    private Completion suggest;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Double)
    private Double price;

    @Field(type = FieldType.Integer)
    private Integer stock;

    @Field(type = FieldType.Keyword)
    private String sku;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Keyword)
    private String brand;

    @Field(type = FieldType.Boolean)
    private Boolean deleted;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant updatedAt;

    public ProductDoc() {}

    public ProductDoc(String id, String name, String description, Double price,
                      Integer stock, String sku, String category, String brand,
                      Boolean deleted, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.sku = sku;
        this.category = category;
        this.brand = brand;
        this.deleted = deleted;
        this.updatedAt = updatedAt;
        this.suggest = buildSuggest(name, category, brand);
    }

    public String getId()           { return id; }
    public String getName()         { return name; }
    public Completion getSuggest()  { return suggest; }
    public String getDescription()  { return description; }
    public Double getPrice()        { return price; }
    public Integer getStock()       { return stock; }
    public String getSku()          { return sku; }
    public String getCategory()     { return category; }
    public String getBrand()        { return brand; }
    public Boolean getDeleted()     { return deleted; }
    public Instant getUpdatedAt()   { return updatedAt; }

    public void setId(String id) { this.id = id; }
    public void setName(String name) {
        this.name = name;
        this.suggest = buildSuggest(name, category, brand);
    }
    public void setSuggest(Completion suggest) { this.suggest = suggest; }
    public void setDescription(String description) { this.description = description; }
    public void setPrice(Double price) { this.price = price; }
    public void setStock(Integer stock) { this.stock = stock; }
    public void setSku(String sku) { this.sku = sku; }
    public void setCategory(String category) { this.category = category; }
    public void setBrand(String brand) { this.brand = brand; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    /**
     * Convenience factory from the Kafka event.
     *
     * Suggest field is populated with multiple "inputs" so one doc matches on
     * name OR category OR brand prefixes — mirrors how Amazon's search bar
     * suggests across dimensions.
     */
    public static ProductDoc fromEvent(Long productId, String name, String description,
                                       BigDecimal price, Integer stock,
                                       String category, String brand,
                                       boolean deleted, Instant updatedAt) {
        return new ProductDoc(
                String.valueOf(productId),
                name,
                description,
                price != null ? price.doubleValue() : null,
                stock,
                null,                              // Product entity doesn't ship sku in the event today
                category,
                brand,
                deleted,
                updatedAt
        );
    }

    /** Build the suggester input array. Nulls dropped automatically. */
    private static Completion buildSuggest(String name, String category, String brand) {
        java.util.List<String> inputs = new java.util.ArrayList<>(3);
        if (name != null && !name.isBlank())     inputs.add(name);
        if (category != null && !category.isBlank()) inputs.add(category);
        if (brand != null && !brand.isBlank())   inputs.add(brand);
        if (inputs.isEmpty()) return null;
        return new Completion(inputs.toArray(new String[0]));
    }
}
