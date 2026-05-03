package com.example.shop.model;

import java.util.List;

public class SearchResult {

    private List<Product> products;   // the actual results
    private long totalResults;        // total number of matching products
    private int currentPage;          // which page we're on
    private int totalPages;           // how many pages exist
    private int resultsPerPage;       // how many per page

    public SearchResult(List<Product> products, long totalResults,
                        int currentPage, int totalPages, int resultsPerPage) {
        this.products = products;
        this.totalResults = totalResults;
        this.currentPage = currentPage;
        this.totalPages = totalPages;
        this.resultsPerPage = resultsPerPage;
    }

    // Getters
    public List<Product> getProducts() { return products; }
    public long getTotalResults() { return totalResults; }
    public int getCurrentPage() { return currentPage; }
    public int getTotalPages() { return totalPages; }
    public int getResultsPerPage() { return resultsPerPage; }
}