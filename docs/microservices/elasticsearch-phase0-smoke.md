# Elasticsearch — Phase 0 Smoke Test

5-minute hand-exercise before touching Spring Data. Prove the cluster is up
and you can CRUD a doc via raw HTTP. Learning: ES stores JSON docs in
indices, queried via HTTP — nothing magic.

> Running against the `elasticsearch:8.14.3` service already in your
> `docker-compose.yml`. Port `9200`, security disabled (dev-only).

---

## 1. Start the cluster (if not already)

```bash
docker compose up -d elasticsearch
```

## 2. Cluster health

```bash
curl -s http://localhost:9200/_cluster/health | jq
```

Expect `"status": "yellow"` or `"green"`. (Single-node clusters start yellow
because they can't replicate — that's fine for dev.)

```json
{
  "cluster_name": "docker-cluster",
  "status": "yellow",
  "number_of_nodes": 1,
  "active_shards": 1,
  "unassigned_shards": 1
}
```

## 3. List indices (likely empty, or just the ones from order-query)

```bash
curl -s 'http://localhost:9200/_cat/indices?v'
```

## 4. Create a toy index with an explicit mapping

Why do this manually before `@Document`? So you see the two ES mapping gotchas:
- `text` fields are **analyzed** (tokenized → searchable but not exact-match)
- `keyword` fields are **not analyzed** (exact match, aggregatable, sortable)

```bash
curl -s -X PUT http://localhost:9200/products_demo \
  -H 'Content-Type: application/json' \
  -d '{
    "mappings": {
      "properties": {
        "name":        { "type": "text" },
        "sku":         { "type": "keyword" },
        "price":       { "type": "scaled_float", "scaling_factor": 100 },
        "tags":        { "type": "keyword" },
        "description": { "type": "text" },
        "created_at":  { "type": "date" }
      }
    }
  }' | jq
```

Expect `{"acknowledged":true, "shards_acknowledged":true, "index":"products_demo"}`.

## 5. Insert docs

```bash
curl -s -X POST http://localhost:9200/products_demo/_doc/1 \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Blue Widget",
    "sku": "WID-001",
    "price": 9.99,
    "tags": ["widget","blue","sale"],
    "description": "A shiny blue widget with chrome finish.",
    "created_at": "2026-10-01T00:00:00Z"
  }' | jq

curl -s -X POST http://localhost:9200/products_demo/_doc/2 \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Red Gadget",
    "sku": "GAD-001",
    "price": 24.50,
    "tags": ["gadget","red"],
    "description": "Red gadget with widgets inside.",
    "created_at": "2026-10-02T00:00:00Z"
  }' | jq
```

## 6. Queries — see the four core patterns

### 6a. match_all (list everything)

```bash
curl -s 'http://localhost:9200/products_demo/_search?pretty' \
  -H 'Content-Type: application/json' \
  -d '{ "query": { "match_all": {} } }'
```

### 6b. match — full-text, analyzed

```bash
# Finds BOTH docs because "widget" appears in doc 1's name AND doc 2's description.
# Analyzer tokenizes on whitespace, lowercases, stems.
curl -s 'http://localhost:9200/products_demo/_search?pretty' \
  -H 'Content-Type: application/json' \
  -d '{ "query": { "match": { "description": "widget" } } }'
```

### 6c. term — exact match on keyword

```bash
# Finds doc 1 only. Note: using "sku" (keyword), not "name" (text).
# A `term` on a text field often returns nothing because the field is analyzed.
curl -s 'http://localhost:9200/products_demo/_search?pretty' \
  -H 'Content-Type: application/json' \
  -d '{ "query": { "term": { "sku": "WID-001" } } }'
```

### 6d. bool — the real production query

```bash
# must  → affects relevance score (full-text search)
# filter → doesn't affect score, gets cached (plain yes/no criteria)
curl -s 'http://localhost:9200/products_demo/_search?pretty' \
  -H 'Content-Type: application/json' \
  -d '{
    "query": {
      "bool": {
        "must":   [ { "match": { "description": "widget" } } ],
        "filter": [
          { "term":  { "tags": "sale" } },
          { "range": { "price": { "lte": 20 } } }
        ]
      }
    }
  }'
```

### 6e. aggregation — faceted navigation preview

```bash
curl -s 'http://localhost:9200/products_demo/_search?pretty' \
  -H 'Content-Type: application/json' \
  -d '{
    "size": 0,
    "aggs": {
      "by_tag":   { "terms": { "field": "tags" } },
      "price_hist": { "histogram": { "field": "price", "interval": 10 } }
    }
  }'
```

## 7. Fuzzy — handles typos

```bash
# "widgt" (typo) still finds "widget" via Levenshtein distance.
curl -s 'http://localhost:9200/products_demo/_search?pretty' \
  -H 'Content-Type: application/json' \
  -d '{ "query": { "match": { "name": { "query": "widgt", "fuzziness": "AUTO" } } } }'
```

## 8. Inspect the inverted index for one field

Peek at how the analyzer tokenized a field — the "aha" moment for ES mental models.

```bash
curl -s -X POST http://localhost:9200/products_demo/_analyze \
  -H 'Content-Type: application/json' \
  -d '{
    "field": "description",
    "text":  "A shiny blue widget with chrome finish."
  }' | jq
```

Expect tokens like `["a","shiny","blue","widget","with","chrome","finish"]` —
whitespace-split, lowercased, no stop-word removal (default `standard` analyzer).

## 9. Clean up

```bash
curl -X DELETE http://localhost:9200/products_demo
```

---

## What you should now understand

- **Index** = a logical collection of docs with a mapping. Like a table.
- **Doc** = JSON object with an `_id`. Like a row.
- **Mapping** = schema. `text` ≠ `keyword` is the single most important distinction.
- **Analyzer** = pipeline that turns text into searchable tokens at write time AND query time.
- **Inverted index** = Lucene's data structure — maps `token → list of doc ids`.
- **`must` vs `filter`** = scoring vs filtering. Use `filter` for yes/no, `must` for relevance.
- **Aggregations** = facets, histograms, metrics. Run on `keyword`/numeric, not `text`.

---

## Interview talking points unlocked by this phase

| Q | Short answer |
|---|---|
| How does ES differ from a SQL database? | Document store (JSON), inverted index for text, horizontally sharded, eventually consistent, no joins |
| Why does my `term` query return nothing? | You hit a `text` field (analyzed/tokenized). Use `keyword` for exact match. |
| Why can't I change a mapping after creating an index? | Lucene's data structures are immutable. Mitigation: alias + reindex. |
| How does ES score relevance? | BM25 by default — term frequency × inverse document frequency, length-normalized |
| What's an analyzer? | Char-filter → tokenizer → token-filter chain. Default is `standard`. |

---

Next: **Phase 1** — publish `product.updated` events from product-service via
the existing outbox pattern. See `docs/microservices/tier2-architecture.md`
for the outbox mechanics; next doc covers wiring it to product writes.
