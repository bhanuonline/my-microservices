package com.example.admin.controller;

import com.example.admin.model.Product;
import com.example.admin.service.ProductService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public String listProducts(Model model) {
        model.addAttribute("products", productService.getAllProducts());
        return "products/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public String createForm(Model model) {
        model.addAttribute("product", new Product());
        return "products/create";
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public String save(@ModelAttribute Product product) {
        productService.createProduct(product.getName(), product.getPrice());
        return "redirect:/products";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String delete(@PathVariable Long id) {
        productService.deleteProduct(id);
        return "redirect:/products";
    }

    @GetMapping("/edit/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public String editForm(@PathVariable Long id, Model model) {
        Product product = productService.getProduct(id);
        if (product == null) {
            // if not found, just redirect back
            return "redirect:/products";
        }
        model.addAttribute("product", product);
        return "products/edit";
    }

    @PostMapping("/edit/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public String update(@PathVariable Long id, @ModelAttribute Product product) {
        productService.updateProduct(id, product.getName(), product.getPrice());
        return "redirect:/products";
    }
}