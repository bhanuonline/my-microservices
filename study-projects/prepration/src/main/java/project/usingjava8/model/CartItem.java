package project.usingjava8.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Builder
public class CartItem {
    Product product;
    int qnty;
    private LocalDateTime createdDate;
}
