package project.usingjava8.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@Builder
@ToString
public class Cart {
    List<CartItem> items;
    String cartId;
    private LocalDateTime createdDate;
}
