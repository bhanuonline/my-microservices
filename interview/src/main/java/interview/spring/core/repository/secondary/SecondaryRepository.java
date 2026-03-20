package interview.spring.core.repository.secondary;

import interview.spring.core.model.secondary.SecondaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecondaryRepository extends JpaRepository<SecondaryEntity, Long> {
}
