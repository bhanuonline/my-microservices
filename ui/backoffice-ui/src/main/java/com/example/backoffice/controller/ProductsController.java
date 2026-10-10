package com.example.backoffice.controller;

import com.example.backoffice.client.GatewayClient;
import com.example.backoffice.client.ProductQueryClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ProductsController {

    private final ProductQueryClient productQuery;
    private final GatewayClient gateway;
    private final ObjectMapper mapper = new ObjectMapper();

    public ProductsController(ProductQueryClient productQuery, GatewayClient gateway) {
        this.productQuery = productQuery;
        this.gateway = gateway;
    }

    @GetMapping("/products")
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) String brand,
                       @RequestParam(required = false) Boolean inStock,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "25") int size,
                       Model model) {
        ProductQueryClient.Page results = productQuery.search(
                q, category, brand, null, null, inStock, page, size, true);
        model.addAttribute("activeNav", "products");
        model.addAttribute("results", results);
        model.addAttribute("q", q);
        model.addAttribute("category", category);
        model.addAttribute("brand", brand);
        model.addAttribute("inStock", inStock);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        return "products/list";
    }

    @GetMapping("/products/new")
    public String createForm(Model model) {
        model.addAttribute("activeNav", "products");
        model.addAttribute("mode", "new");
        return "products/edit";
    }

    @PostMapping("/products/new")
    public String create(@RequestParam String name,
                         @RequestParam(required = false) String description,
                         @RequestParam Double price,
                         @RequestParam Integer quantityInStock,
                         @RequestParam(required = false) String category,
                         @RequestParam(required = false) String brand,
                         @RequestParam(required = false) String sku) {
        ObjectNode body = mapper.createObjectNode()
                .put("name", name)
                .put("description", description)
                .put("price", price)
                .put("quantityInStock", quantityInStock);
        if (category != null && !category.isBlank()) body.put("category", category);
        if (brand != null    && !brand.isBlank())    body.put("brand", brand);
        if (sku != null      && !sku.isBlank())      body.put("sku", sku);
        gateway.createProduct(body);
        return "redirect:/products";
    }

    @GetMapping("/products/{id}")
    public String edit(@PathVariable Long id, Model model) {
        JsonNode product = gateway.getProduct(id);
        model.addAttribute("activeNav", "products");
        model.addAttribute("mode", "edit");
        model.addAttribute("product", product);
        model.addAttribute("id", id);
        return "products/edit";
    }

    @PostMapping("/products/{id}")
    public String update(@PathVariable Long id,
                         @RequestParam String name,
                         @RequestParam(required = false) String description,
                         @RequestParam Double price,
                         @RequestParam Integer quantityInStock,
                         @RequestParam(required = false) String category,
                         @RequestParam(required = false) String brand,
                         @RequestParam(required = false) String sku) {
        ObjectNode body = mapper.createObjectNode()
                .put("id", id)
                .put("name", name)
                .put("description", description)
                .put("price", price)
                .put("quantityInStock", quantityInStock);
        if (category != null && !category.isBlank()) body.put("category", category);
        if (brand != null    && !brand.isBlank())    body.put("brand", brand);
        if (sku != null      && !sku.isBlank())      body.put("sku", sku);
        gateway.updateProduct(id, body);
        return "redirect:/products/" + id;
    }

    @PostMapping("/products/{id}/delete")
    public String delete(@PathVariable Long id) {
        gateway.deleteProduct(id);
        return "redirect:/products";
    }
}
