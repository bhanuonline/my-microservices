package com.example.shop.service;

import com.algolia.api.SearchClient;
import com.algolia.model.search.*;
import com.example.shop.model.Product;
import com.example.shop.model.SearchResult;
import com.example.shop.model.SortOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AlgoliaService {

    private final SearchClient client;
    private final String indexName;

    private final String indexPriceAsc;
    private final String indexPriceDesc;
    private final String indexRatingDesc;

    // Algolia client is created once using your credentials
    public AlgoliaService(
            @Value("${algolia.application-id}") String appId,
            @Value("${algolia.api-key}") String apiKey,
            @Value("${algolia.index-name}") String indexName,
            @Value("${algolia.index-price-asc}") String indexPriceAsc,
            @Value("${algolia.index-price-desc}") String indexPriceDesc,
            @Value("${algolia.index-rating-desc}") String indexRatingDesc) {

        this.client = new SearchClient(appId, apiKey);
        this.indexName = indexName;
        this.indexPriceAsc = indexPriceAsc;
        this.indexPriceDesc = indexPriceDesc;
        this.indexRatingDesc = indexRatingDesc;
    }

    // Picks the right index based on sort option
    private String resolveIndex(SortOption sort) {
        return switch (sort) {
            case PRICE_ASC    -> indexPriceAsc;
            case PRICE_DESC   -> indexPriceDesc;
            case RATING_DESC  -> indexRatingDesc;
            default           -> indexName;  // RELEVANCE = main index
        };
    }

    // Search with pagination + sorting
    public SearchResult searchProductsSorted(
            String query, int page, int hitsPerPage, SortOption sort) throws Exception {

        String targetIndex = resolveIndex(sort); // ← pick the right replica

        SearchForHits searchQuery = new SearchForHits()
                .setIndexName(targetIndex)       // ← search the replica
                .setQuery(query)
                .setPage(page)
                .setHitsPerPage(hitsPerPage);

        SearchMethodParams params = new SearchMethodParams()
                .addRequests(searchQuery);

        SearchResponses<Product> responses = client.search(params, Product.class);
        SearchResponse<Product> response = (SearchResponse<Product>) responses.getResults().get(0);
        // ✅ Replace with this
        List<Product> products = response.getHits();

        return new SearchResult(
                products,
                response.getNbHits(),
                response.getPage(),
                response.getNbPages(),
                response.getHitsPerPage()
        );
    }

    // Push a list of products into Algolia
    public void pushProducts(List<Product> products) throws Exception {
        client.saveObjects(indexName, products);
        System.out.println("✅ " + products.size() + " products pushed to Algolia!");
    }

    public SearchResponse<Product> searchProducts(String query) throws Exception {

        SearchForHits searchQuery = new SearchForHits()
                .setIndexName(indexName)
                .setQuery(query);          // what the user typed

        SearchMethodParams params = new SearchMethodParams()
                .addRequests(searchQuery);

        SearchResponses<Product> responses = client.search(params, Product.class);

        // responses contains a list of results — we return the first one
        return (SearchResponse<Product>) responses.getResults().get(0);
    }

    public SearchResponse<Product> searchWithFilter(String query, String filter) throws Exception {

        SearchForHits searchQuery = new SearchForHits()
                .setIndexName(indexName)
                .setQuery(query)
                .setFilters(filter);   // ← filter expression

        SearchMethodParams params = new SearchMethodParams()
                .addRequests(searchQuery);

        SearchResponses<Product> responses = client.search(params, Product.class);
        return (SearchResponse<Product>) responses.getResults().get(0);
    }
    public SearchResult searchProductsPaginated(String query, int page, int hitsPerPage) throws Exception {

        SearchForHits searchQuery = new SearchForHits()
                .setIndexName(indexName)
                .setQuery(query)
                .setPage(page)                  // ← which page
                .setHitsPerPage(hitsPerPage);   // ← how many per page

        SearchMethodParams params = new SearchMethodParams()
                .addRequests(searchQuery);

        SearchResponses<Product> responses = client.search(params, Product.class);
        SearchResponse<Product> response = (SearchResponse<Product>) responses.getResults().get(0);

        // Extract products from hits
        List<Product> products = response.getHits();

        // Build and return the result with pagination info
        return new SearchResult(
                products,
                response.getNbHits(),     // total matching results
                response.getPage(),       // current page
                response.getNbPages(),    // total pages
                response.getHitsPerPage() // results per page
        );
    }
    // Delete a single product from Algolia by objectID
    public void deleteProduct(String objectID) throws Exception {
        client.deleteObject(indexName, objectID);
        System.out.println("✅ Deleted from Algolia: " + objectID);
    }
}