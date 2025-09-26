package com.example.userservice.controller;

import com.example.userservice.productservice.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserProductController {

    @Autowired
    ProductService productService;

    @GetMapping("/getProducts")
    public void showProducts() {
        productService.getAllProducts().forEach(System.out::println);
    }
}
