package interview.spring.core.repository.tertiary;
import interview.spring.core.model.tertiary.TertiaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TertiaryRepository extends JpaRepository<TertiaryEntity, Long> {
}
