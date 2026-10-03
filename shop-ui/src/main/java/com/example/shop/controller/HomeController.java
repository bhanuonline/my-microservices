package com.example.shop.controller;

import com.example.shop.client.ProductQueryClient;
import com.example.shop.dto.SearchResult;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Home page: pulls the first 8 products from the ES read model to render a
 * "featured" grid. Rebuild your product catalog and this grid rebuilds.
 */
@Controller
public class HomeController {

    private final ProductQueryClient productQuery;

    public HomeController(ProductQueryClient productQuery) {
        this.productQuery = productQuery;
    }

    @GetMapping("/")
    public String home(Model model) {
        SearchResult featured = productQuery.search(
                null, null, null, null, null, true, 0, 8, false);
        model.addAttribute("featured", featured);
        return "home";
    }
}
