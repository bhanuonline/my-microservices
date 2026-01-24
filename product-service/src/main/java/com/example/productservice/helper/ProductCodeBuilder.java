package com.example.productservice.util;

import com.example.productservice.model.BaseProduct;
import com.example.productservice.model.ProductVariant;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.stream.Collectors;

@Component
public class ProductCodeBuilder {

    public String buildCode(BaseProduct base, ProductVariant variant) {
        // 🌟 Use base code, not name
        String prefix = base.getBaseCode().toUpperCase();

        String attrPart = variant.getAttributeValues().stream()
                .sorted(Comparator.comparing(av -> av.getAttribute().getName()))
                .map(av -> av.getValue().toUpperCase())
                .collect(Collectors.joining("-"));

        return prefix + "-" + attrPart;
    }
}