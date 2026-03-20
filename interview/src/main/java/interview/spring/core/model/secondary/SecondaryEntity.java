package interview.spring.core.model.secondary;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class SecondaryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    // Getters and setters
}
