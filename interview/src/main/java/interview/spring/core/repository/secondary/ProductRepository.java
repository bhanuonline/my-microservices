package interview.spring.core.repository.secondary;

import interview.spring.core.model.secondary.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}