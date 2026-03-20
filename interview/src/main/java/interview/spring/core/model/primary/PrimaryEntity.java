package interview.spring.core.model.primary;;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class PrimaryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    // Getters and setters
}