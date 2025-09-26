package com.example.userservice.productservice;

import com.example.userservice.dto.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Service
public class ProductService {
    @Autowired
    private RestTemplate restTemplate;

    public List<Product> getAllProducts() {
        ResponseEntity<List<Product>> productList =
                restTemplate.exchange(
                        "http://PRODUCT-SERVICE/products",
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<List<Product>>() {
                        }
                );
        log.info("product list :"+productList);
        return productList.getBody();
    }
}
