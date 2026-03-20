package interview.spring.core.model.tertiary;

import jakarta.persistence.*;

@Entity
public class TertiaryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    // Getters and setters
}
