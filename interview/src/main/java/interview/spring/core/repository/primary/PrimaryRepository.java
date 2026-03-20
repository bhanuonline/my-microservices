package interview.spring.core.repository.primary;
import interview.spring.core.model.primary.PrimaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrimaryRepository extends JpaRepository<PrimaryEntity, Long> {
}
