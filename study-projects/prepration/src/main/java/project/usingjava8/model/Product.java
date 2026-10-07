package project.usingjava8.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class Product{
    String name;
    String category;
    double price;
    int stock;
    int sku;
    private LocalDateTime createdDate;



}